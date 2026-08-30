package eu.hxreborn.biometricapplock.hook

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Handler
import eu.hxreborn.biometricapplock.BiometricAuthActivity
import eu.hxreborn.biometricapplock.util.Logger

// system_server and the auth activity talk through a one-time token in the launch intent, no
// broadcasts or binder. the hook creates a token and redirects the launch to the auth activity, and a
// good auth sends the token back so the hook knows the unlock is real and replays the original.

/**
 * The returning launch carries that token. Consume it (one-shot, so a replayed intent can't unlock
 * again), mark the package unlocked, and hand back the stashed original to resume.
 */
internal fun resolveAuthToken(
    intent: Intent?,
    packageName: String?,
    userId: Int,
): PendingAuth? {
    val token = intent?.getStringExtra(BiometricAuthActivity.EXTRA_AUTH_TOKEN) ?: return null
    val pkg = packageName ?: return null
    val peeked = peekToken(token) ?: return null
    if (peeked.packageName != pkg) {
        val signalCarrier =
            pkg == BiometricAuthActivity.MODULE_PACKAGE &&
                intent.getBooleanExtra(BiometricAuthActivity.EXTRA_RESUME_SIGNAL, false)
        if (!signalCarrier) {
            // the auth activity launch itself carries the token through this intercept pass
            if (pkg != BiometricAuthActivity.MODULE_PACKAGE) {
                Logger.warn("token package mismatch: expected=${peeked.packageName} actual=$pkg")
            }
            return null
        }
    }
    val entry = consumeToken(token) ?: return null
    intent.removeExtra(BiometricAuthActivity.EXTRA_AUTH_TOKEN)
    val target = entry.packageName
    // install/uninstall handlers get the action-keyed grant, not the per-pkg unlock map, so an
    // install auth can't silently authorize an uninstall
    if (isSystemHandler(target)) {
        grantSystemHandler(entry.launch?.action)
    } else {
        addUnlocked(target, entry.userId)
        replayPendingPin(target, entry.userId)
    }
    Logger.info("unlocked pkg=$target user=${entry.userId}")
    return entry
}

private fun replayPendingPin(
    pkg: String,
    userId: Int,
) {
    val taskId = consumePendingPin(pkg, userId) ?: return
    val reflection = reflection ?: return
    val atms = atmsRef ?: return
    val startSystemLockTaskMode = reflection.startSystemLockTaskMode ?: return
    val handler = reflection.handlerField.get(atms) as? Handler ?: return
    handler.post {
        runCatching { startSystemLockTaskMode.invoke(atms, taskId) }
            .onSuccess { Logger.info("replayed pin pkg=$pkg user=$userId taskId=$taskId") }
            .onFailure { Logger.warn("pin replay failed pkg=$pkg taskId=$taskId: ${it.message}") }
    }
}

/**
 * Launcher path. Rewrite the interceptor's in-flight launch to point at the auth activity so the
 * target never starts, and stash the exact original under the token so a good auth replays it
 * exactly (keeps deep links and non-exported notification targets working). resolveIntent and
 * resolveActivity still have to run or ActivityStarter has no resolved component and crashes.
 */
internal fun tryRedirect(
    interceptor: Any,
    packageName: String,
    className: String,
): Boolean {
    val reflection = reflection ?: return false
    val userId = runCatching { reflection.userIdField.getInt(interceptor) }.getOrDefault(0)
    val callingUid =
        runCatching { reflection.callingUidField.getInt(interceptor) }.getOrDefault(-1)
    val token = createToken(packageName, userId, callingUid)

    val redirected =
        runCatching {
            val originalIntent =
                runCatching { reflection.intentField.get(interceptor) as? Intent }.getOrNull()
            val userId = reflection.userIdField.getInt(interceptor)

            val authIntent =
                buildAuthIntent(
                    packageName,
                    userId,
                    token,
                    shouldUseOpaqueUnlockPrompt(),
                    className,
                )
            originalIntent?.let { stashLaunch(token, it) }
            rewriteLaunch(interceptor, authIntent)
        }.onFailure {
            discardToken(token)
            Logger.error("redirect failed: ${it.message}", it)
        }.isSuccess

    if (redirected) {
        Logger.info("redirected pkg=$packageName comp=$className")
        return true
    }
    return blockLaunch(interceptor, packageName)
}

/**
 * Points the in-flight launch at [intent]. ActivityStarter carries on with whatever the interceptor
 * holds, so every field the resolved target implies has to move with it or the original still runs.
 */
private fun rewriteLaunch(
    interceptor: Any,
    intent: Intent,
    resumeUserId: Int? = null,
    resumeCallingUid: Int? = null,
) {
    val reflection = reflection ?: error("reflection unavailable")
    val activityTaskSupervisor = reflection.supervisorField.get(interceptor)
    val realPid = reflection.realCallingPidField.getInt(interceptor)
    val realUid = reflection.realCallingUidField.getInt(interceptor)
    val userId = resumeUserId ?: reflection.userIdField.getInt(interceptor)
    val startFlags = reflection.startFlagsField.getInt(interceptor)

    val resolveArgs =
        if (reflection.resolveIntent.parameterCount >= 6) {
            // A14+ (API 34+) takes a trailing callingPid arg
            arrayOf(intent, null, userId, 0, realUid, realPid)
        } else {
            // A13 (API 33) has no callingPid arg
            arrayOf(intent, null, userId, 0, realUid)
        }
    val resolvedInfo = reflection.resolveIntent.invoke(activityTaskSupervisor, *resolveArgs)
    val activityInfo =
        reflection.resolveActivity.invoke(
            activityTaskSupervisor,
            intent,
            resolvedInfo,
            startFlags,
            null,
        ) as? ActivityInfo
            ?: error(
                "unresolved ${intent.component ?: intent.action} rInfo=${resolvedInfo != null} user=$userId",
            )

    reflection.intentField.set(interceptor, intent)
    reflection.resolvedInfoField.set(interceptor, resolvedInfo)
    reflection.activityInfoField.set(interceptor, activityInfo)
    reflection.callingPidField.setInt(interceptor, realPid)
    reflection.callingUidField.setInt(interceptor, resumeCallingUid ?: realUid)
    reflection.userIdField.setInt(interceptor, resumeUserId ?: 0)
    reflection.resolvedTypeField.set(interceptor, null)
}

/**
 * Last resort when the prompt cannot be resolved, which happens in the seconds after boot before
 * PackageManager can see the module. Sending the launch home keeps the locked app shut, since
 * ActivityStarter has no way to cancel a start and letting it run would open the app unauthenticated.
 */
private fun blockLaunch(
    interceptor: Any,
    packageName: String,
): Boolean {
    val home =
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    val blocked = runCatching { rewriteLaunch(interceptor, home) }.isSuccess
    if (blocked) {
        Logger.warn("blocked pkg=$packageName, prompt unresolvable, sent home")
    } else {
        Logger.error("pkg=$packageName opens unauthenticated, neither prompt nor home resolvable")
    }
    return blocked
}

/**
 * The installer aborts a session dialog unless the caller owns the session.
 */
internal fun resumeInPlace(
    interceptor: Any,
    auth: PendingAuth,
): Boolean {
    val launch = auth.launch ?: return false
    if (auth.callingUid < 0) return false
    return runCatching { rewriteLaunch(interceptor, launch, auth.userId, auth.callingUid) }
        .onFailure { Logger.warn("in-place resume failed pkg=${auth.packageName}: ${it.message}") }
        .isSuccess
}

/**
 * Replay the stashed original from the system_server context (uid 1000). That is what makes resume
 * reliable: it holds START_ANY_ACTIVITY and skips background-launch limits, so the exact task comes
 * forward and deep links / non-exported targets land. Post off the lock, mGlobalLock is held upstream.
 */
internal fun resumeOriginalLaunch(auth: PendingAuth) {
    val reflection = reflection ?: return
    val atms = atmsRef ?: return
    val handler = reflection.handlerField.get(atms) as? Handler ?: return
    val context = reflection.contextField.get(atms) as? Context ?: return
    val original = auth.launch ?: return
    val launch =
        Intent(original).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // the replay runs as the system uid, so without this documented hint the installer
            // would stack an unknown-sources warning in front of its dialog
            if (isSystemHandler(auth.packageName)) {
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }
        }
    val userHandle = reflection.userHandleOf.invoke(null, auth.userId)
    handler.post {
        regrantUriPermissions(auth, launch)
        runCatching {
            reflection.startActivityAsUser.invoke(context, launch, userHandle)
        }.onFailure {
            Logger.error(
                "resume launch failed: ${it.message}",
                it,
            )
        }
    }
}

/**
 * The replay runs as the system uid, which AOSP refuses to mint content grants for, so the target
 * would launch without access to a content:// payload and crash reading it (e.g. the installer
 * staging a downloaded APK). Re-issue the original caller's grants before replaying.
 */
private fun regrantUriPermissions(
    auth: PendingAuth,
    launch: Intent,
) {
    if (auth.callingUid < 0) return
    val grantFlags =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    if (launch.flags and grantFlags == 0) return
    if (launch.data == null && launch.clipData == null) return
    val reflection = reflection ?: return
    val ugmi = reflection.uriGrantsInternal ?: return
    val check = reflection.checkGrantUriPermissionFromIntent
    val grant = reflection.grantUriPermissionUncheckedFromIntent
    if (check == null || grant == null) {
        Logger.warn("uri regrant unavailable pkg=${auth.packageName}")
        return
    }
    runCatching {
        val needed =
            check
                .invoke(ugmi, launch, auth.callingUid, auth.packageName, auth.userId)
                ?: return
        grant.invoke(ugmi, needed, null)
        Logger.debug {
            "regranted uris pkg=${auth.packageName} uid=${auth.callingUid} uri=${launch.data}"
        }
    }.onFailure {
        Logger.warn("uri regrant failed pkg=${auth.packageName}: ${it.message}")
    }
}

/**
 * Recents path. No in-flight intent to rewrite, so just start the auth activity off the lock.
 * Nothing gets stashed, so after auth the tokened launcher intent re-enters and opens the app fresh
 * ([resolveAuthToken] hands back a null launch and the hook just proceeds it) instead of restoring
 * the exact task.
 */
internal fun postAuthLaunch(
    activityTaskSupervisor: Any,
    entry: TaskEntry,
) {
    val reflection = reflection ?: return
    val activityTaskManagerService =
        reflection.activityTaskManagerServiceField.get(activityTaskSupervisor)
    val handler = reflection.handlerField.get(activityTaskManagerService) as Handler
    val context = reflection.contextField.get(activityTaskManagerService) as Context

    val token = createToken(entry.packageName, entry.userId)
    val intent =
        buildAuthIntent(entry.packageName, entry.userId, token, shouldUseOpaqueUnlockPrompt())

    handler.post {
        runCatching { context.startActivity(intent) }.onFailure {
            discardToken(token)
            Logger.error("posted auth launch failed: ${it.message}", it)
        }
    }
}

/**
 * Uninstall backstop prompt, fired from the deletePackageX hook. No token round-trip and no
 * resume: the prompt writes a grant timestamp to remote prefs and the hook reads it when the
 * caller retries. Always opaque, there is no foreground task behind it to surface.
 */
internal fun launchUninstallAuth(targetPackage: String?) {
    val reflection = reflection ?: return
    val atms = atmsRef ?: return
    val handler = reflection.handlerField.get(atms) as? Handler ?: return
    val context = reflection.contextField.get(atms) as? Context ?: return
    val intent =
        Intent().apply {
            component =
                ComponentName(
                    BiometricAuthActivity.MODULE_PACKAGE,
                    BiometricAuthActivity.OPAQUE_AUTH_ACTIVITY,
                )
            putExtra(BiometricAuthActivity.EXTRA_UNINSTALL_AUTH, true)
            if (!targetPackage.isNullOrEmpty()) {
                putExtra(BiometricAuthActivity.EXTRA_TARGET_PKG, targetPackage)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    handler.post {
        runCatching { context.startActivity(intent) }.onFailure {
            Logger.error("uninstall auth launch failed: ${it.message}", it)
        }
    }
}

// translucent by default, opaque is the compat fallback for OEMs that cancel the see-through prompt
private fun buildAuthIntent(
    targetPackageName: String,
    targetUserId: Int,
    token: String,
    opaque: Boolean,
    className: String? = null,
) = Intent().apply {
    val authActivity =
        if (opaque) {
            BiometricAuthActivity.OPAQUE_AUTH_ACTIVITY
        } else {
            BiometricAuthActivity.AUTH_ACTIVITY
        }
    component = ComponentName(BiometricAuthActivity.MODULE_PACKAGE, authActivity)
    putExtra(BiometricAuthActivity.EXTRA_TARGET_PKG, targetPackageName)
    putExtra(BiometricAuthActivity.EXTRA_TARGET_USER_ID, targetUserId)
    putExtra(BiometricAuthActivity.EXTRA_AUTH_TOKEN, token)
    if (!className.isNullOrEmpty()) {
        putExtra(BiometricAuthActivity.EXTRA_TARGET_ACTIVITY, className)
    }
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
