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
    val entry = consumeToken(token) ?: return null
    if (entry.packageName != pkg) {
        Logger.warn("token package mismatch: expected=${entry.packageName} actual=$pkg")
        return null
    }
    intent.removeExtra(BiometricAuthActivity.EXTRA_AUTH_TOKEN)
    addUnlocked(pkg, entry.userId)
    Logger.info("unlocked pkg=$pkg user=${entry.userId}")
    return entry
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
    val token = createToken(packageName, userId)

    val redirected =
        runCatching {
            val originalIntent =
                runCatching { reflection.intentField.get(interceptor) as? Intent }.getOrNull()
            val activityTaskSupervisor = reflection.supervisorField.get(interceptor)
            val realPid = reflection.realCallingPidField.getInt(interceptor)
            val realUid = reflection.realCallingUidField.getInt(interceptor)
            val userId = reflection.userIdField.getInt(interceptor)
            val startFlags = reflection.startFlagsField.getInt(interceptor)

            val authIntent =
                buildAuthIntent(
                    packageName,
                    userId,
                    token,
                    shouldUseOpaqueUnlockPrompt(),
                    className,
                )
            originalIntent?.let { stashLaunch(token, it) }

            val resolveArgs =
                if (reflection.resolveIntent.parameterCount >= 6) {
                    // A14+ (API 34+) takes a trailing callingPid arg
                    arrayOf(authIntent, null, userId, 0, realUid, realPid)
                } else {
                    // A13 (API 33) has no callingPid arg
                    arrayOf(authIntent, null, userId, 0, realUid)
                }
            val resolvedInfo = reflection.resolveIntent.invoke(activityTaskSupervisor, *resolveArgs)
            val activityInfo =
                reflection.resolveActivity.invoke(
                    activityTaskSupervisor,
                    authIntent,
                    resolvedInfo,
                    startFlags,
                    null,
                ) as? ActivityInfo
                    ?: error("BiometricAuthActivity not resolvable: PackageManager not ready")

            reflection.intentField.set(interceptor, authIntent)
            reflection.resolvedInfoField.set(interceptor, resolvedInfo)
            reflection.activityInfoField.set(interceptor, activityInfo)
            reflection.callingPidField.setInt(interceptor, realPid)
            reflection.callingUidField.setInt(interceptor, realUid)
            reflection.userIdField.setInt(interceptor, 0)
            reflection.resolvedTypeField.set(interceptor, null)
        }.onFailure {
            discardToken(token)
            Logger.error("redirect failed: ${it.message}", it)
        }.isSuccess

    if (redirected) Logger.info("redirected pkg=$packageName comp=$className")
    return redirected
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
    val launch = Intent(original).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    val userHandle = reflection.userHandleOf.invoke(null, auth.userId)
    handler.post {
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
