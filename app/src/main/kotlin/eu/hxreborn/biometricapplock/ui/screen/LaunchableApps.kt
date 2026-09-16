package eu.hxreborn.biometricapplock.ui.screen

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import eu.hxreborn.biometricapplock.util.getUserId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@SuppressLint("QueryPermissionsNeeded")
internal suspend fun loadInstalledPackageKeys(
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
            runCatching {
                val userContext =
                    Context::class.java
                        .getMethod(
                            "createPackageContextAsUser",
                            String::class.java,
                            Int::class.javaPrimitiveType,
                            UserHandle::class.java,
                        ).invoke(context, "android", 0, user) as? Context
                userContext?.packageManager?.getInstalledApplications(0)?.forEach { info ->
                    if (info.packageName != ownPackage) {
                        keys.add("${info.packageName}:$userId")
                    }
                }
            }
        }
        context.packageManager.getInstalledApplications(0).forEach { info ->
            if (info.packageName != ownPackage) {
                keys.add("${info.packageName}:0")
            }
        }
        keys
    }

@Composable
internal fun rememberInstalledPackageKeys(): Set<String> {
    val context = LocalContext.current
    val ownPackage = context.packageName
    val packageKeys by produceState(initialValue = emptySet(), context, ownPackage) {
        value = loadInstalledPackageKeys(context, ownPackage)
    }
    return packageKeys
}
