package eu.hxreborn.biometricapplock.hook

import android.app.TaskInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.widget.Toast
import eu.hxreborn.biometricapplock.BiometricAuthActivity
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.receiver.registerPackageEvents
import eu.hxreborn.biometricapplock.util.Logger
import io.github.libxposed.api.XposedModule
import java.util.concurrent.atomic.AtomicBoolean

@Volatile
internal var atmsRef: Any? = null

private val packageEventsRegistered = AtomicBoolean(false)

private fun captureAtms(interceptor: Any): Any? =
    runCatching {
        val r = reflection ?: return null
        val sup = r.supervisorField.get(interceptor) ?: return null
        r.activityTaskManagerServiceField.get(sup)
    }.getOrNull()

private fun ensurePackageEventsRegistered() {
    val atms = atmsRef ?: return
    if (!packageEventsRegistered.compareAndSet(false, true)) return
    val r = reflection ?: return
    val ctx = r.contextField.get(atms) as? Context ?: return
    val handler = r.handlerField.get(atms) as? Handler ?: return
    registerPackageEvents(ctx, handler)
}

// PackageManager is not published at onSystemServerStarting, so the install/uninstall handler
// resolution comes back empty at boot. once an intercept fires the system is up, so replay
// loadHookPrefs off the lock and the handler set fills in for subsequent launches
private fun postBootPrefsRefresh() {
    val atms = atmsRef ?: return
    val r = reflection ?: return
    val handler = r.handlerField.get(atms) as? Handler ?: return
    val prefs = hookPrefs ?: return
    handler.post {
        runCatching {
            loadHookPrefs(prefs)
        }.onFailure { Logger.warn("post-boot prefs refresh failed: ${it.message}") }
    }
}

// toast on the ATMS handler, the PMS lock is held here
private fun postUninstallBlockedToast() {
    val atms = atmsRef ?: return
    val r = reflection ?: return
    val handler = r.handlerField.get(atms) as? Handler ?: return
    val ctx = r.contextField.get(atms) as? Context ?: return
    handler.post {
        runCatching {
            val pkgCtx = ctx.createPackageContext(BiometricAuthActivity.MODULE_PACKAGE, 0)
            val message = pkgCtx.getString(R.string.uninstall_blocked_toast)
            Toast.makeText(pkgCtx, message, Toast.LENGTH_LONG).show()
        }.onFailure { Logger.warn("uninstall toast failed: ${it.message}") }
    }
}

internal fun refreshSecureSurfaces() {
    val atms = atmsRef ?: return
    val r = reflection ?: return
    val rwc = r.rootWindowContainerField.get(atms) ?: return
    val handler = r.handlerField.get(atms) as? Handler ?: return
    handler.post {
        runCatching { r.refreshSecureSurfaceState.invoke(rwc) }.onFailure {
            Logger.warn(
                "refreshSecureSurfaceState failed: ${it.message}",
                it,
            )
        }
    }
}

/**
 * Wires up all the system_server hooks. A locked app can hit the foreground three ways, through
 * two framework methods:
 * - launcher tap, deep link, notification -> ActivityStarter.intercept ([hookLaunchIntercept])
 * - recents card tap -> ActivityTaskSupervisor.startActivityFromRecents ([hookRecentsLaunch])
 * - nav-bar quick switch -> ActivityTaskSupervisor.startActivityFromRecents ([hookRecentsLaunch])
 */
internal fun XposedModule.registerSystemServerHooks(
    classLoader: ClassLoader,
    locked: Set<String>,
) {
    lockedPackages = locked
    Logger.info("registering system_server hooks sdk=${Build.VERSION.SDK_INT}")
    reflection =
        runCatching { SystemServerReflection(classLoader) }
            .onFailure {
                Logger.error(
                    "reflection init failed: ${it.message}",
                    it,
                )
            }.getOrNull()

    hookLaunchIntercept(classLoader)
    hookActivityLaunched(classLoader)
    hookRecentsLaunch(classLoader)
    hookTaskRemoved(classLoader)
    hookScreenAwake(classLoader)
    hookFlagSecure(classLoader)
    hookUninstall(classLoader)
}

// launcher path. swap the launch for the prompt, then replay the original once auth passes
private fun XposedModule.hookLaunchIntercept(classLoader: ClassLoader) {
    runCatching {
        val method =
            classLoader.findMethod(
                "com.android.server.wm.ActivityStartInterceptor",
                "intercept",
                11,
            )
        // grab the arg indices once up front, the framework likes to shuffle them between versions
        val intentIdx = method.firstArgIndexOfType("Intent").let { if (it >= 0) it else 0 }
        val actInfoIdx = method.firstArgIndexOfType("ActivityInfo").let { if (it >= 0) it else 2 }
        Logger.info(
            "intercept indices intent=$intentIdx aInfo=$actInfoIdx args=${method.parameterCount}",
        )
        hook(method).intercept { chain ->
            if (atmsRef == null) {
                atmsRef = captureAtms(chain.thisObject)
                ensurePackageEventsRegistered()
                postBootPrefsRefresh()
            }

            val intent = chain.args[intentIdx] as? Intent
            val activityInfo = chain.args[actInfoIdx] as? ActivityInfo
            val packageName = activityInfo?.packageName
            val userId = reflection?.userIdField?.get(chain.thisObject) as? Int ?: 0

            val auth = resolveAuthToken(intent, packageName, userId)
            if (auth != null) {
                if (auth.launch != null) {
                    Logger.debug { "resume original pkg=$packageName user=$userId" }
                    resumeOriginalLaunch(auth)
                    return@intercept true
                }
                return@intercept chain.proceed()
            }

            // skips relock since a null keep key wipes every unlock
            if (packageName != null) relockOtherPackages(packageName, userId)

            val result = chain.proceed()
            if (result == true) return@intercept true
            if (packageName == null) return@intercept false
            if (isSystemHandler(packageName)) {
                if (!shouldInterceptSystemHandler(intent?.action)) {
                    Logger.debug {
                        "intercept skip system-handler pkg=$packageName action=${intent?.action}"
                    }
                    return@intercept false
                }
                if (isSystemHandlerGrantFresh(intent?.action)) {
                    Logger.debug {
                        "intercept pass system-handler pkg=$packageName action=${intent?.action}"
                    }
                    return@intercept false
                }
                Logger.debug {
                    "intercept gating system-handler pkg=$packageName action=${intent?.action}"
                }
                return@intercept tryRedirect(chain.thisObject, packageName, activityInfo.name)
            }
            val pkgKey = "$packageName:$userId"
            if (pkgKey !in lockedPackages) return@intercept false
            if (intent?.hasCategory(Intent.CATEGORY_HOME) == true) return@intercept false
            if (isActivityAllowed(
                    packageName,
                    userId,
                    activityInfo.name,
                    activityInfo.targetActivity,
                )
            ) {
                Logger.debug {
                    "intercept allowlisted pkg=$packageName user=$userId comp=${activityInfo.name}"
                }
                return@intercept false
            }
            if (isUnlocked(packageName, userId)) {
                refreshUnlock(packageName, userId)
                Logger.debug {
                    "intercept pass pkg=$packageName user=$userId comp=${activityInfo.name}"
                }
                return@intercept false
            }

            Logger.debug {
                "intercept gating pkg=$packageName user=$userId comp=${activityInfo.name} action=${intent?.action}"
            }
            tryRedirect(chain.thisObject, packageName, activityInfo.name)
        }
        Logger.info("hooked intercept args=${method.parameterCount}")
    }.onFailure { Logger.error("hookLaunchIntercept failed: ${it.message}", it) }
}

// bookkeeping: taskId -> package so the recents and task-removed hooks can find it
private fun XposedModule.hookActivityLaunched(classLoader: ClassLoader) {
    runCatching {
        val method =
            classLoader.findMethod(
                "com.android.server.wm.ActivityStartInterceptor",
                "onActivityLaunched",
                2,
            )
        hook(method).intercept { chain ->
            val taskInfo = chain.args[0] as? TaskInfo ?: return@intercept chain.proceed()
            val topActivity = taskInfo.topActivity ?: return@intercept chain.proceed()
            val packageName = topActivity.packageName
            val userId = reflection?.taskInfoUserIdField?.get(taskInfo) as? Int ?: 0
            val pkgKey = "$packageName:$userId"
            if (pkgKey in lockedPackages) {
                taskCache[taskInfo.taskId] = TaskEntry(packageName, userId)
                Logger.debug {
                    "launched pkg=$packageName user=$userId taskId=${taskInfo.taskId} top=${topActivity.shortClassName}"
                }
            }
            chain.proceed()
        }
        Logger.info("hooked onActivityLaunched args=${method.parameterCount}")
    }.onFailure { Logger.error("hookActivityLaunched failed: ${it.message}", it) }
}

// falls back to the window hierarchy since ROMs without onActivityLaunched never fill taskCache
private fun resolveTaskEntry(
    supervisor: Any,
    taskId: Int,
): TaskEntry? {
    taskCache[taskId]?.let { return it }
    return runCatching {
        val r = reflection ?: return null
        val lookup = r.taskLookup ?: return null
        val atms = r.activityTaskManagerServiceField.get(supervisor) ?: return null
        val rwc = r.rootWindowContainerField.get(atms) ?: return null
        val task =
            lookup.anyTaskForId.invoke(rwc, taskId, lookup.matchAttachedOrRecents) ?: return null
        val pkg =
            (lookup.realActivityField.get(task) as? ComponentName)?.packageName ?: return null
        val userId = lookup.userIdField.getInt(task)
        if ("$pkg:$userId" !in lockedPackages) return null
        TaskEntry(pkg, userId).also { taskCache[taskId] = it }
    }.getOrNull()
}

/**
 * recents path: card taps and the nav-bar quick switch, which skip ActivityStarter.
 * - translucent: let the task come up, then drop the prompt over it (old 1.3 behavior)
 * - opaque: keep the task off-screen, return START_SUCCESS, let auth own the screen
 */
private fun XposedModule.hookRecentsLaunch(classLoader: ClassLoader) {
    runCatching {
        val method =
            classLoader.findMethod(
                "com.android.server.wm.ActivityTaskSupervisor",
                "startActivityFromRecents",
                4,
            )
        hook(method).intercept { chain ->
            val callingPid = chain.args[0] as? Int
            val callingUid = chain.args[1] as? Int
            val taskId = chain.args[2] as? Int
            val entry = taskId?.let { resolveTaskEntry(chain.thisObject, it) }

            // skips relock since a null keep key wipes the resumed app's unlock
            if (entry != null) relockOtherPackages(entry.packageName, entry.userId)

            if (entry != null && !isUnlocked(entry.packageName, entry.userId)) {
                val opaque = shouldUseOpaqueUnlockPrompt()
                Logger.debug {
                    "recents gate pkg=${entry.packageName} user=${entry.userId} taskId=$taskId " +
                        "pid=$callingPid uid=$callingUid ${
                            recentsGesture(
                                chain.args.getOrNull(3),
                            )
                        } " + "mode=${if (opaque) "block" else "surface"}"
                }
                if (opaque) {
                    // don't surface the task or it steals focus from the solid prompt.
                    // a quick switch still backgrounds the prompt though, so it self-cancels there
                    runCatching {
                        postAuthLaunch(
                            chain.thisObject,
                            entry,
                        )
                    }.onFailure { Logger.error("recents auth failed: ${it.message}", it) }
                    return@intercept 0
                }
                val result = chain.proceed()
                runCatching {
                    postAuthLaunch(
                        chain.thisObject,
                        entry,
                    )
                }.onFailure { Logger.error("recents auth failed: ${it.message}", it) }
                return@intercept result
            }
            if (entry != null) {
                refreshUnlock(entry.packageName, entry.userId)
                Logger.debug {
                    "recents pass pkg=${entry.packageName} user=${entry.userId} taskId=$taskId " +
                        recentsGesture(
                            chain.args.getOrNull(3),
                        )
                }
            }
            chain.proceed()
        }
        Logger.info("hooked startActivityFromRecents args=${method.parameterCount}")
    }.onFailure { Logger.error("hookRecentsLaunch failed: ${it.message}", it) }
}

// drop the unlock when a locked task gets swiped off recents
private fun XposedModule.hookTaskRemoved(classLoader: ClassLoader) {
    runCatching {
        val supervisorClass =
            classLoader.anyClassFromNames(
                "com.android.server.wm.ActivityTaskSupervisor",
                "com.android.server.wm.ActivityStackSupervisor",
            )
        // the method got renamed between A13 and A14, so try both
        val method =
            supervisorClass.declaredMethods.firstOrNull {
                it.name == "cleanUpRemovedTask" || it.name == "cleanUpRemovedTaskLocked"
            } ?: error("cleanUpRemovedTask not found")
        val taskIdField =
            classLoader
                .loadClass("com.android.server.wm.Task")
                .getDeclaredField("mTaskId")
                .apply { isAccessible = true }
        hook(method).intercept { chain ->
            val result = chain.proceed()
            // do this after proceed and off the lock, mGlobalLock is held in here
            runCatching {
                // always evict the dead taskId so the cache can't go stale or grow unbounded
                val taskId = chain.args.getOrNull(0)?.let { taskIdField.getInt(it) }
                val entry = taskId?.let { taskCache.remove(it) } ?: return@runCatching
                if (!shouldRelockOnTaskRemoved()) return@runCatching
                removeFromUnlocked(setOf("${entry.packageName}:${entry.userId}"))
                Logger.debug { "task removed relock pkg=${entry.packageName} taskId=$taskId" }
            }
            result
        }
        Logger.info("hooked ${method.name} args=${method.parameterCount}")
    }.onFailure {
        Logger.warn("hookTaskRemoved unavailable (cleanUpRemovedTask/mTaskId): ${it.message}")
    }
}

// screen off wipes unlock state, screen on relocks whatever's past its delay
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
            if (awake == false) {
                // screen went off, wipe everything so locked apps ask again next time
                if (shouldRelockOnScreenOff() && unlockedPackages.isNotEmpty()) {
                    val cleared = unlockedPackages.size
                    clearUnlocked()
                    Logger.debug { "screen off relock cleared=$cleared" }
                }
                return@intercept chain.proceed()
            }
            if (awake == true && unlockedPackages.isNotEmpty()) {
                // back awake, only relock the ones past their delay
                val now = SystemClock.elapsedRealtime()
                val toRelock =
                    unlockedPackages
                        .filter { key ->
                            val pkg = key.substringBeforeLast(':')
                            val uid = key.substringAfterLast(':').toIntOrNull() ?: 0
                            shouldRelockOnTransition(pkg, uid, now)
                        }.toSet()
                if (toRelock.isNotEmpty()) removeFromUnlocked(toRelock)
                Logger.debug {
                    val topPkg =
                        runCatching {
                            reflection?.findTopResumedPackageKey(chain.thisObject)
                        }.getOrNull()
                    "screen on relocked=${toRelock.size} topPkg=$topPkg"
                }
            }
            chain.proceed()
        }
        Logger.info("hooked onScreenAwakeChanged args=${method.parameterCount}")
    }.onFailure { Logger.error("hookScreenAwake failed: ${it.message}", it) }
}

// force-blocks screenshots in unlocked locked apps when BLOCK_SCREENSHOTS is on
private fun XposedModule.hookFlagSecure(classLoader: ClassLoader) {
    runCatching {
        val method =
            classLoader.findMethod(
                "com.android.server.wm.WindowState",
                "isSecureLocked",
                0,
            )
        val windowStateClass = classLoader.loadClass("com.android.server.wm.WindowState")
        val activityRecordField =
            windowStateClass.getDeclaredField("mActivityRecord").apply { isAccessible = true }
        val packageNameField =
            reflection?.activityRecordPackageNameField ?: error("reflection not ready")
        val userIdField = reflection?.activityRecordUserIdField ?: error("reflection not ready")
        hook(method).intercept { chain ->
            val ar = activityRecordField.get(chain.thisObject) ?: return@intercept chain.proceed()
            val pkg = packageNameField.get(ar) as? String ?: return@intercept chain.proceed()
            val userId = userIdField.get(ar) as? Int ?: 0
            val pkgKey = "$pkg:$userId"
            if (pkgKey in lockedPackages && isUnlocked(pkg, userId) &&
                shouldBlockScreenshots(
                    pkg,
                    userId,
                )
            ) {
                Logger.debug { "flagsecure force-block pkg=$pkg user=$userId" }
                return@intercept true
            }
            chain.proceed()
        }
        Logger.info("hooked isSecureLocked args=${method.parameterCount}")
    }.onFailure { Logger.warn("hookFlagSecure not available: ${it.message}") }
}

// every user uninstall (launcher, Settings, Play Store, adb) ends up here at deletePackageX
private fun XposedModule.hookUninstall(classLoader: ClassLoader) {
    runCatching {
        val method =
            classLoader.findMethod(
                "com.android.server.pm.DeletePackageHelper",
                "deletePackageX",
                5,
            )
        hook(method).intercept { chain ->
            // fail open: if the args shifted, let the delete run instead of crashing system_server
            val packageName = runCatching { chain.args.getOrNull(0) as? String }.getOrNull()
            val removedBySystem =
                runCatching { chain.args.getOrNull(4) as? Boolean }.getOrDefault(false)

            val selfProtect =
                packageName == BiometricAuthActivity.MODULE_PACKAGE && removedBySystem == false &&
                    shouldPreventModuleUninstall()
            if (selfProtect) {
                Logger.info("blocked uninstall pkg=${BiometricAuthActivity.MODULE_PACKAGE}")
                postUninstallBlockedToast()
                // DELETE_FAILED_INTERNAL_ERROR aborts the deletion without running it
                return@intercept -1
            }

            // backstop for deletes that never show the system dialog (pm/adb, silent callers).
            // blocked attempts prompt, the grant lets the user's retry through
            val needsBiometric =
                removedBySystem == false && requireBiometricForUninstall() &&
                    !packageName.isNullOrEmpty() &&
                    packageName != BiometricAuthActivity.MODULE_PACKAGE
            if (needsBiometric) {
                if (hasFreshUninstallAuth() || isSystemHandlerGrantFresh(Intent.ACTION_DELETE)) {
                    Logger.info("uninstall grant fresh pkg=$packageName")
                    return@intercept chain.proceed()
                }
                Logger.info("blocked uninstall pkg=$packageName awaiting biometric")
                runCatching {
                    launchUninstallAuth(packageName)
                }.onFailure { Logger.warn("uninstall auth launch failed: ${it.message}") }
                return@intercept -1
            }

            chain.proceed()
        }
        Logger.info("hooked deletePackageX args=${method.parameterCount}")
    }.onFailure { Logger.warn("hookUninstall not available: ${it.message}") }
}
