package eu.hxreborn.biometricapplock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import eu.hxreborn.biometricapplock.hook.clearRuntimeStateForPackage
import eu.hxreborn.biometricapplock.util.Logger

internal val packageEventsReceiver =
    object : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent,
        ) {
            val action = intent.action ?: return
            when (action) {
                Intent.ACTION_PACKAGE_FULLY_REMOVED,
                Intent.ACTION_PACKAGE_REPLACED,
                Intent.ACTION_PACKAGE_DATA_CLEARED,
                -> {
                    val pkg = intent.data?.schemeSpecificPart ?: return
                    clearRuntimeStateForPackage(pkg)
                    Logger.debug { "pkg event action=$action pkg=$pkg cleared runtime state" }
                }
            }
        }
    }

internal fun registerPackageEvents(
    context: Context,
    handler: Handler,
) {
    val filter =
        IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_DATA_CLEARED)
            addDataScheme("package")
        }
    runCatching {
        context.registerReceiver(packageEventsReceiver, filter, null, handler)
        Logger.info("registered package events receiver")
    }.onFailure { Logger.warn("registerPackageEvents failed: ${it.message}", it) }
}
