package eu.hxreborn.biometricapplock.hook

import android.app.TaskInfo
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Handler
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

private fun atmsHandler(): Handler? {
    val atms = atmsRef ?: return null
    return reflection?.handlerField?.get(atms) as? Handler
}

private fun atmsContext(): Context? {
    val atms = atmsRef ?: return null
    return reflection?.contextField?.get(atms) as? Context
}

private fun ensurePackageEventsRegistered() {
    val ctx = atmsContext() ?: return
    val handler = atmsHandler() ?: return
    if (!packageEventsRegistered.compareAndSet(false, true)) return
    registerPackageEvents(ctx, handler)
}

// replays loadHookPrefs once the system is up since the install/uninstall handler resolve
// comes back empty at boot, before PackageManager is published
private fun postBootPrefsRefresh() {
    val handler = atmsHandler() ?: return
    val prefs = hookPrefs ?: return
    handler.post {
        runCatching { loadHookPrefs(prefs) }
            .onFailure { Logger.warn("post-boot prefs refresh failed: ${it.message}") }
    }
}

// shows the toast on the ATMS handler since the PMS lock is held here
private fun postUninstallBlockedToast() {
    val handler = atmsHandler() ?: return
    val ctx = atmsContext() ?: return
    handler.post {
        runCatching {
            val pkgCtx = ctx.createPackageContext(BiometricAuthActivity.MODULE_PACKAGE, 0)
            val message = pkgCtx.getString(R.string.uninstall_blocked_toast)
            Toast.makeText(pkgCtx, message, Toast.LENGTH_LONG).show()
        }.onFailure { Logger.warn("uninstall toast failed: ${it.message}") }
    }
}

internal fun refreshSecureSurfaces() {
    val r = reflection ?: return
    val atms = atmsRef ?: return
    val rwc = r.rootWindowContainerField.get(atms) ?: return
    val handler = atmsHandler() ?: return
    handler.post {
        runCatching { r.refreshSecureSurfaceState.invoke(rwc) }
            .onFailure { Logger.warn("refreshSecureSurfaceState failed: ${it.message}", it) }
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
    reflection?.taskLookup?.let { Logger.info(it.capability()) }

    // one line naming which framework target each hook found, the first thing to read on an
    // unfamiliar ROM. a false points straight at the hook whose method moved or vanished
    val installed =
        mapOf(
            "intercept" to hookLaunchIntercept(classLoader),
            "onActivityLaunched" to hookActivityLaunched(classLoader),
            "startActivityFromRecents" to hookRecentsLaunch(classLoader),
            "cleanUpRemovedTask" to hookTaskRemoved(classLoader),
            "onScreenAwakeChanged" to hookScreenAwake(classLoader),
            "isSecureLocked" to hookFlagSecure(classLoader),
            "deletePackageX" to hookUninstall(classLoader),
        )
    val summary = installed.entries.joinToString(" ") { "${it.key}=${it.value}" }
    Logger.info("hooks installed $summary")
}

// runs once on the first intercept, the earliest point where the supervisor is reachable
private fun bootstrapFromIntercept(interceptor: Any) {
    atmsRef = captureAtms(interceptor)
    ensurePackageEventsRegistered()
    postBootPrefsRefresh()
}

// install and uninstall dialogs share one OS package, so the action picks which toggle applies
private fun interceptSystemHandlerLaunch(
    interceptor: Any,
    packageName: String,
    className: String,
    action: String?,
): Boolean {
    if (!shouldInterceptSystemHandler(action)) {
        Logger.debug { "intercept skip system-handler pkg=$packageName action=$action" }
        return false
    }
    if (isSystemHandlerGrantFresh(action)) {
        Logger.debug { "intercept pass system-handler pkg=$packageName action=$action" }
        return false
    }
    Logger.debug { "intercept gating system-handler pkg=$packageName action=$action" }
    return tryRedirect(interceptor, packageName, className)
}

// decides whether a locked package's launch gets redirected to the prompt
private fun interceptLockedLaunch(
    interceptor: Any,
    intent: Intent?,
    activityInfo: ActivityInfo,
    userId: Int,
): Boolean {
    val packageName = activityInfo.packageName
    if ("$packageName:$userId" !in lockedPackages) return false
    if (intent?.hasCategory(Intent.CATEGORY_HOME) == true) return false
    if (isActivityAllowed(packageName, userId, activityInfo.name, activityInfo.targetActivity)) {
        Logger.debug {
            "intercept allowlisted pkg=$packageName user=$userId comp=${activityInfo.name}"
        }
        return false
    }
    if (isUnlocked(packageName, userId)) {
        refreshUnlock(packageName, userId)
        Logger.debug { "intercept pass pkg=$packageName user=$userId comp=${activityInfo.name}" }
        return false
    }
    Logger.debug {
        "intercept gating pkg=$packageName user=$userId comp=${activityInfo.name} " +
            "action=${intent?.action}"
    }
    return tryRedirect(interceptor, packageName, activityInfo.name)
}

// launcher path: swaps the launch for the prompt and replays the original once auth passes
private fun XposedModule.hookLaunchIntercept(classLoader: ClassLoader): Boolean =
    runCatching {
        val method =
            classLoader.findMethod(
                "com.android.server.wm.ActivityStartInterceptor",
                "intercept",
                11,
            )
        // grabs the arg indices once up front since the framework shuffles them between versions
        val intentIdx = method.firstArgIndexOfType("Intent").let { if (it >= 0) it else 0 }
        val actInfoIdx = method.firstArgIndexOfType("ActivityInfo").let { if (it >= 0) it else 2 }
        Logger.info(
            "intercept indices intent=$intentIdx aInfo=$actInfoIdx args=${method.parameterCount}",
        )
        hook(method).intercept { chain ->
            if (atmsRef == null) bootstrapFromIntercept(chain.thisObject)

            val intent = chain.args[intentIdx] as? Intent
            val activityInfo = chain.args[actInfoIdx] as? ActivityInfo
            val packageName = activityInfo?.packageName
            val userId = reflection?.userIdField?.get(chain.thisObject) as? Int ?: 0

            val auth = resolveAuthToken(intent, packageName, userId)
            if (auth != null) {
                if (auth.launch == null) return@intercept chain.proceed()
                Logger.debug { "resume original pkg=$packageName user=$userId" }
                resumeOriginalLaunch(auth)
                return@intercept true
            }

            // skips relock since a null keep key wipes every unlock
            if (packageName != null) relockOtherPackages(packageName, userId)

            val result = chain.proceed()
            if (result == true) return@intercept true
            if (activityInfo == null || packageName == null) return@intercept false
            if (isSystemHandler(packageName)) {
                return@intercept interceptSystemHandlerLaunch(
                    chain.thisObject,
                    packageName,
                    activityInfo.name,
                    intent?.action,
                )
            }
            interceptLockedLaunch(chain.thisObject, intent, activityInfo, userId)
        }
        Logger.info("hooked intercept args=${method.parameterCount}")
    }.onFailure { Logger.error("hookLaunchIntercept failed: ${it.message}", it) }.isSuccess

// bookkeeping: taskId -> package so the recents and task-removed hooks can find it
private fun XposedModule.hookActivityLaunched(classLoader: ClassLoader): Boolean =
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
    }.onFailure { Logger.error("hookActivityLaunched failed: ${it.message}", it) }.isSuccess

// reads the task itself, only locked packages produce an entry
private fun taskEntryFromTask(task: Any): TaskEntry? {
    val lookup = reflection?.taskLookup ?: return null
    val pkg = lookup.packageOf(task) ?: return null
    val userId = runCatching { lookup.userIdField?.getInt(task) }.getOrNull() ?: 0
    if ("$pkg:$userId" !in lockedPackages) return null
    return TaskEntry(pkg, userId)
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
        val anyTaskForId = lookup.anyTaskForId ?: return null
        val matchMode = lookup.matchAttachedOrRecents ?: return null
        val atms = r.activityTaskManagerServiceField.get(supervisor) ?: return null
        val rwc = r.rootWindowContainerField.get(atms) ?: return null
        val task = anyTaskForId.invoke(rwc, taskId, matchMode) ?: return null
        taskEntryFromTask(task)?.also { taskCache[taskId] = it }
    }.getOrNull()
}

/**
 * recents path: card taps and the nav-bar quick switch, which skip ActivityStarter.
 * - translucent: let the task come up, then drop the prompt over it (old 1.3 behavior)
 * - opaque: keep the task off-screen, return START_SUCCESS, let auth own the screen
 */
private fun XposedModule.hookRecentsLaunch(classLoader: ClassLoader): Boolean =
    runCatching {
        val method =
            classLoader.findMethod(
                "com.android.server.wm.ActivityTaskSupervisor",
                "startActivityFromRecents",
                4,
            )
        hook(method).intercept { chain ->
            val taskId = chain.args[2] as? Int
            // don't touch unknown tasks since a null keep key wipes the resumed app's unlock
            val entry =
                taskId?.let { resolveTaskEntry(chain.thisObject, it) }
                    ?: return@intercept chain.proceed()

            relockOtherPackages(entry.packageName, entry.userId)

            if (isUnlocked(entry.packageName, entry.userId)) {
                refreshUnlock(entry.packageName, entry.userId)
                Logger.debug {
                    "recents pass pkg=${entry.packageName} user=${entry.userId} " +
                        "taskId=$taskId ${recentsGesture(chain.args.getOrNull(3))}"
                }
                return@intercept chain.proceed()
            }

            val opaque = shouldUseOpaqueUnlockPrompt()
            Logger.debug {
                "recents gate pkg=${entry.packageName} user=${entry.userId} taskId=$taskId " +
                    "pid=${chain.args[0]} uid=${chain.args[1]} " +
                    "${recentsGesture(chain.args.getOrNull(3))} " +
                    "mode=${if (opaque) "block" else "surface"}"
            }
            // opaque returns START_SUCCESS without surfacing the task so the prompt keeps focus
            val result = if (opaque) 0 else chain.proceed()
            runCatching { postAuthLaunch(chain.thisObject, entry) }
                .onFailure { Logger.error("recents auth failed: ${it.message}", it) }
            result
        }
        Logger.info("hooked startActivityFromRecents args=${method.parameterCount}")
    }.onFailure { Logger.error("hookRecentsLaunch failed: ${it.message}", it) }.isSuccess

// drops the unlock when a locked task gets swiped off recents
private fun XposedModule.hookTaskRemoved(classLoader: ClassLoader): Boolean =
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
            // runs after proceed since mGlobalLock is held in here
            runCatching {
                val task = chain.args.getOrNull(0) ?: return@runCatching
                val taskId = taskIdField.getInt(task)
                // always evicts the dead taskId so the cache can't go stale or grow unbounded
                val cached = taskCache.remove(taskId)
                if (!shouldRelockOnTaskRemoved()) return@runCatching
                // the cache misses tasks that never passed the recents hook, so read the task
                val entry = cached ?: taskEntryFromTask(task) ?: return@runCatching
                removeFromUnlocked(setOf("${entry.packageName}:${entry.userId}"))
                Logger.debug { "task removed relock pkg=${entry.packageName} taskId=$taskId" }
            }
            result
        }
        Logger.info("hooked ${method.name} args=${method.parameterCount}")
    }.onFailure {
        Logger.warn("hookTaskRemoved unavailable (cleanUpRemovedTask/mTaskId): ${it.message}")
    }.isSuccess

// screen off wipes unlock state and screen on relocks whatever's past its delay
private fun XposedModule.hookScreenAwake(classLoader: ClassLoader): Boolean =
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
                // wipes everything so locked apps ask again next time
                if (shouldRelockOnScreenOff() && unlockedPackages.isNotEmpty()) {
                    val cleared = unlockedPackages.size
                    clearUnlocked()
                    Logger.debug { "screen off relock cleared=$cleared" }
                }
                return@intercept chain.proceed()
            }
            if (awake == true && unlockedPackages.isNotEmpty()) {
                // relocks only the ones past their delay
                val relocked = relockElapsedUnlocks()
                Logger.debug {
                    val topPkg =
                        runCatching {
                            reflection?.findTopResumedPackageKey(chain.thisObject)
                        }.getOrNull()
                    "screen on relocked=$relocked topPkg=$topPkg"
                }
            }
            chain.proceed()
        }
        Logger.info("hooked onScreenAwakeChanged args=${method.parameterCount}")
    }.onFailure { Logger.error("hookScreenAwake failed: ${it.message}", it) }.isSuccess

// force-blocks screenshots in unlocked locked apps when BLOCK_SCREENSHOTS is on
private fun XposedModule.hookFlagSecure(classLoader: ClassLoader): Boolean =
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
            if (shouldForceSecure(pkg, userId)) {
                Logger.debug { "flagsecure force-block pkg=$pkg user=$userId" }
                return@intercept true
            }
            chain.proceed()
        }
        Logger.info("hooked isSecureLocked args=${method.parameterCount}")
    }.onFailure { Logger.warn("hookFlagSecure not available: ${it.message}") }.isSuccess

// every user uninstall (launcher, Settings, Play Store, adb) ends up here at deletePackageX
private fun XposedModule.hookUninstall(classLoader: ClassLoader): Boolean =
    runCatching {
        val method =
            classLoader.findMethod(
                "com.android.server.pm.DeletePackageHelper",
                "deletePackageX",
                5,
            )
        hook(method).intercept { chain ->
            // fails open: shifted args let the delete run instead of crashing system_server
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

            // backstops silent deletes (pm, adb) and lets a fresh auth grant pass the retry
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
    }.onFailure { Logger.warn("hookUninstall not available: ${it.message}") }.isSuccess
