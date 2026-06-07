package eu.hxreborn.biometricapplock.ui.screen

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.UserManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import eu.hxreborn.biometricapplock.util.getUserId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@SuppressLint("QueryPermissionsNeeded")
internal suspend fun loadLaunchablePackageKeys(
    context: Context,
    ownPackage: String,
): Set<String> =
    withContext(Dispatchers.IO) {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val profiles = userManager.userProfiles

        val keys = mutableSetOf<String>()
        for (user in profiles) {
            val userId = getUserId(user)
            launcherApps.getActivityList(null, user).forEach { info ->
                val pkg = info.applicationInfo.packageName
                if (pkg != ownPackage) {
                    keys.add("$pkg:$userId")
                }
            }
        }
        keys
    }

@Composable
internal fun rememberLaunchablePackageKeys(): Set<String> {
    val context = LocalContext.current
    val ownPackage = context.packageName
    val packageKeys by produceState(initialValue = emptySet(), context, ownPackage) {
        value = loadLaunchablePackageKeys(context, ownPackage)
    }
    return packageKeys
}
