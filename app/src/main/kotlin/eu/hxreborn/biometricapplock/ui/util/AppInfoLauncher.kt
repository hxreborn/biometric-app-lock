package eu.hxreborn.biometricapplock.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

private const val TAG = "BiometricAppLock"

fun openAppInfo(
    context: Context,
    packageName: String,
) {
    val intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { context.startActivity(intent) }.onFailure { Log.w(TAG, "openAppInfo failed pkg=$packageName: ${it.message}") }
}
