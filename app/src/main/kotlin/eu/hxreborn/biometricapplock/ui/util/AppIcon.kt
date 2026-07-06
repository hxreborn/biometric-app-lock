package eu.hxreborn.biometricapplock.ui.util

import android.content.Context
import android.content.pm.LauncherApps
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.graphics.drawable.toBitmap
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.util.getUserHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val iconCache = LruCache<String, ImageBitmap>(200)

@Composable
fun rememberAppIcon(
    packageName: String,
    userId: Int = 0,
): ImageBitmap? {
    val context = LocalContext.current
    val iconSizePx = with(LocalDensity.current) { Tokens.AppIconSize.roundToPx() * 2 }
    val key = "$packageName:$userId:$iconSizePx"
    return produceState<ImageBitmap?>(initialValue = iconCache.get(key), key1 = key) {
        if (value != null) return@produceState
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                    val userHandle = getUserHandle(userId)
                    val info = launcherApps.getActivityList(packageName, userHandle).firstOrNull()
                    info?.getIcon(0)?.toBitmap(iconSizePx, iconSizePx)?.asImageBitmap()
                }.getOrNull()?.also { iconCache.put(key, it) }
            }
    }.value
}
