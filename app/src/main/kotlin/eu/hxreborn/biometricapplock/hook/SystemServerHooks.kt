package eu.hxreborn.biometricapplock.hook

import android.app.TaskInfo
import android.content.Intent
import android.content.pm.ActivityInfo
import android.util.Log
import eu.hxreborn.biometricapplock.util.Logger
import io.github.libxposed.api.XposedModule

internal fun XposedModule.registerSystemServerHooks(
    classLoader: ClassLoader,
    locked: Set<String>,
) {
    lockedPackages = locked
    reflection =
        runCatching { SystemServerReflection(classLoader) }
            .onFailure { Logger.log(Log.ERROR, "reflection init failed: ${it.message}", it) }
            .getOrNull()

    hookLaunchIntercept(classLoader)
    hookActivityLaunched(classLoader)
    hookRecents(classLoader)
    hookScreenAwake(classLoader)
    hookSnapshotProtection(classLoader)
}

private fun XposedModule.hookLaunchIntercept(classLoader: ClassLoader) {
    runCatching {
        val method =
            classLoader.findMethod(
                "com.android.server.wm.ActivityStartInterceptor",
                "intercept",
                11,
            )
        hook(method).intercept { chain ->
            val intent = chain.args[0] as? Intent
            val activityInfo = chain.args[2] as? ActivityInfo
            val packageName = activityInfo?.packageName

            if (isValidAuthToken(intent, packageName)) return@intercept chain.proceed()

            relockOtherPackages(packageName)

            val result = chain.proceed()
            if (result == true) return@intercept true
            if (packageName == null || packageName !in lockedPackages) return@intercept false
            if (packageName in unlockedPackages) {
                Logger.debug { "intercept pass pkg=$packageName comp=${activityInfo.name}" }
                return@intercept false
            }

            Logger.debug {
                "intercept gating pkg=$packageName comp=${activityInfo.name} action=${intent?.action}"
            }
            tryRedirect(chain.thisObject, packageName, activityInfo.name)
        }
        Logger.log(Log.INFO, "hooked intercept locked=$lockedPackages")
    }.onFailure { Logger.log(Log.ERROR, "hookLaunchIntercept failed: ${it.message}", it) }
}

private fun XposedModule.hookActivityLaunched(classLoader: ClassLoader) {
    runCatching {
        val method =
            classLoader.findMethod(
                "com.android.server.wm.ActivityStartInterceptor",
                "onActivityLaunched",
                2,
            )
        hook(method).intercept { chain ->
            val taskInfo = chain.args[0] as? TaskInfo
            val topActivity = taskInfo?.topActivity
            val packageName = topActivity?.packageName
            if (packageName in lockedPackages && taskInfo != null && topActivity != null) {
                taskCache[taskInfo.taskId] = TaskEntry(packageName!!, topActivity.className)
                Logger.log(
                    Log.INFO,
                    "launched pkg=$packageName taskId=${taskInfo.taskId} top=${topActivity.shortClassName}",
                )
            }
            chain.proceed()
        }
        Logger.log(Log.INFO, "hooked onActivityLaunched")
    }.onFailure { Logger.log(Log.ERROR, "hookActivityLaunched failed: ${it.message}", it) }
}

private fun XposedModule.hookRecents(classLoader: ClassLoader) {
    runCatching {
        val method =
            classLoader.findMethod(
                "com.android.server.wm.ActivityTaskSupervisor",
                "startActivityFromRecents",
                4,
            )
        hook(method).intercept { chain ->
            val taskId = chain.args[2] as? Int
            val entry = taskId?.let { taskCache[it] }

            relockOtherPackages(entry?.packageName)

            if (entry != null && entry.packageName !in unlockedPackages) {
                Logger.log(
                    Log.INFO,
                    "gating recents pkg=${entry.packageName} taskId=$taskId unlocked=$unlockedPackages",
                )
                val result = chain.proceed()
                Logger.debug { "recents proceed result=$result taskId=$taskId" }
                runCatching { postAuthLaunch(chain.thisObject, entry) }
                    .onFailure { Logger.log(Log.ERROR, "recents auth failed: ${it.message}", it) }
                return@intercept result
            }
            Logger.debug {
                val unlocked = entry?.packageName?.let { it in unlockedPackages }
                "recents pass-through taskId=$taskId pkg=${entry?.packageName} unlocked=$unlocked"
            }
            chain.proceed()
        }
        Logger.log(Log.INFO, "hooked startActivityFromRecents")
    }.onFailure { Logger.log(Log.ERROR, "hookRecents failed: ${it.message}", it) }
}

private fun XposedModule.hookScreenAwake(classLoader: ClassLoader) {
    runCatching {
        val method =
            classLoader.findMethod(
                "com.android.server.wm.ActivityTaskManagerService",
                "onScreenAwakeChanged",
                1,
            )
        hook(method).intercept { chain ->
            val awake = chain.args[0] as? Boolean
            if (awake == true && unlockedPackages.isNotEmpty()) {
                unlockedPackages.clear()
                val topPackageName =
                    runCatching {
                        reflection?.findTopResumedPackageName(
                            chain.thisObject,
                        )
                    }.getOrNull()
                Logger.log(Log.INFO, "screen on, re-locked all topPkg=$topPackageName")
            }
            chain.proceed()
        }
        Logger.log(Log.INFO, "hooked onScreenAwakeChanged")
    }.onFailure { Logger.log(Log.ERROR, "hookScreenAwake failed: ${it.message}", it) }
}

private fun XposedModule.hookSnapshotProtection(classLoader: ClassLoader) {
    runCatching {
        val method =
            classLoader.findMethod(
                "com.android.server.wm.ActivityRecord",
                "shouldUseAppThemeSnapshot",
                0,
            )
        val packageNameField =
            classLoader
                .loadClass("com.android.server.wm.ActivityRecord")
                .getField("packageName")

        hook(method).intercept { chain ->
            val packageName = packageNameField.get(chain.thisObject) as? String
            if (packageName in lockedPackages && packageName !in unlockedPackages) {
                return@intercept true
            }
            chain.proceed()
        }
        Logger.log(Log.INFO, "hooked shouldUseAppThemeSnapshot")
    }.onFailure {
        Logger.log(Log.ERROR, "hookSnapshotProtection failed: ${it.message}", it)
    }
}
