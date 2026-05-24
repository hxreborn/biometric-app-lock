package eu.hxreborn.biometricapplock.hook

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import eu.hxreborn.biometricapplock.BiometricAuthActivity
import eu.hxreborn.biometricapplock.util.Logger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val pendingTokens: MutableSet<String> = ConcurrentHashMap.newKeySet()

internal fun isValidAuthToken(
    intent: Intent?,
    packageName: String?,
): Boolean {
    val token = intent?.getStringExtra(BiometricAuthActivity.EXTRA_AUTH_TOKEN) ?: return false
    if (packageName !in lockedPackages || !pendingTokens.remove(token)) return false
    intent.removeExtra(BiometricAuthActivity.EXTRA_AUTH_TOKEN)
    unlockedPackages.add(packageName!!)
    Logger.log(Log.INFO, "token accepted pkg=$packageName")
    return true
}

internal fun tryRedirect(
    interceptor: Any,
    packageName: String,
    className: String,
): Boolean {
    val reflection = reflection ?: return false
    val token = UUID.randomUUID().toString()
    pendingTokens.add(token)

    runCatching { applyRedirect(reflection, interceptor, packageName, className, token) }
        .onFailure {
            pendingTokens.remove(token)
            Logger.log(Log.ERROR, "redirect failed: ${it.message}", it)
            return false
        }

    Logger.log(Log.INFO, "redirected pkg=$packageName comp=$className")
    return true
}

private fun applyRedirect(
    reflection: SystemServerReflection,
    interceptor: Any,
    targetPackageName: String,
    targetClassName: String,
    token: String,
) {
    val activityTaskSupervisor = reflection.supervisorField.get(interceptor)
    val realPid = reflection.realCallingPidField.getInt(interceptor)
    val realUid = reflection.realCallingUidField.getInt(interceptor)
    val userId = reflection.userIdField.getInt(interceptor)
    val startFlags = reflection.startFlagsField.getInt(interceptor)

    val authIntent = buildAuthIntent(targetPackageName, targetClassName, token)
    val resolvedInfo =
        reflection.resolveIntent.invoke(
            activityTaskSupervisor,
            authIntent,
            null,
            userId,
            0,
            realUid,
            realPid,
        )
    val activityInfo =
        reflection.resolveActivity.invoke(
            activityTaskSupervisor,
            authIntent,
            resolvedInfo,
            startFlags,
            null,
        )

    reflection.intentField.set(interceptor, authIntent)
    reflection.resolvedInfoField.set(interceptor, resolvedInfo)
    reflection.activityInfoField.set(interceptor, activityInfo)
    reflection.callingPidField.setInt(interceptor, realPid)
    reflection.callingUidField.setInt(interceptor, realUid)
    reflection.resolvedTypeField.set(interceptor, null)
}

internal fun postAuthLaunch(
    activityTaskSupervisor: Any,
    entry: TaskEntry,
) {
    val reflection = reflection ?: return
    val activityTaskManagerService =
        reflection.activityTaskManagerServiceField.get(activityTaskSupervisor)
    val handler = reflection.handlerField.get(activityTaskManagerService) as Handler
    val context = reflection.contextField.get(activityTaskManagerService) as Context

    val token = UUID.randomUUID().toString()
    pendingTokens.add(token)
    val intent = buildAuthIntent(entry.packageName, entry.topActivityClassName, token)

    handler.post {
        runCatching { context.startActivity(intent) }
            .onFailure {
                pendingTokens.remove(token)
                Logger.log(Log.ERROR, "posted auth launch failed: ${it.message}", it)
            }
    }
}

private fun buildAuthIntent(
    targetPackageName: String,
    targetClassName: String,
    token: String,
) = Intent().apply {
    component =
        ComponentName(BiometricAuthActivity.MODULE_PACKAGE, BiometricAuthActivity.AUTH_ACTIVITY)
    putExtra(BiometricAuthActivity.EXTRA_TARGET_PKG, targetPackageName)
    putExtra(BiometricAuthActivity.EXTRA_TARGET_CLS, targetClassName)
    putExtra(BiometricAuthActivity.EXTRA_AUTH_TOKEN, token)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
