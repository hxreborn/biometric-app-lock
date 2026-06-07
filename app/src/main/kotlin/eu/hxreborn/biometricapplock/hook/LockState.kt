package eu.hxreborn.biometricapplock.hook

import android.content.SharedPreferences
import android.os.SystemClock
import eu.hxreborn.biometricapplock.BiometricAuthActivity
import eu.hxreborn.biometricapplock.prefs.Prefs
import eu.hxreborn.biometricapplock.util.Logger
import java.util.concurrent.ConcurrentHashMap

internal const val RELOCK_DELAY_NEVER = -1

internal data class TaskEntry(
    val packageName: String,
    val userId: Int,
)

@Volatile
internal var lockedPackages: Set<String> = emptySet()

private fun packageKey(
    pkg: String,
    userId: Int?,
): String = "$pkg:$userId"

// pkg:userId -> elapsedRealtime of last interaction; entry exists iff the pkg is currently considered unlocked
private val unlockedMap = ConcurrentHashMap<String, Long>()

internal val unlockedPackages: Set<String>
    get() = unlockedMap.keys.toSet()

internal fun isUnlocked(
    pkg: String,
    userId: Int,
): Boolean {
    val key = packageKey(pkg, userId)
    val ts = unlockedMap[key] ?: return false
    val delay = getEffectiveRelockDelay(pkg, userId)
    if (delay == RELOCK_DELAY_NEVER) return true
    if (delay == 0) return true
    return SystemClock.elapsedRealtime() - ts < delay * 1000L
}

internal fun shouldRelockOnTransition(
    pkg: String,
    userId: Int,
    now: Long,
): Boolean {
    val key = packageKey(pkg, userId)
    val delay = getEffectiveRelockDelay(pkg, userId)
    if (delay == RELOCK_DELAY_NEVER) return false
    if (delay == 0) return true
    val ts = unlockedMap[key] ?: return true
    return now - ts >= delay * 1000L
}

internal fun addUnlocked(
    pkg: String,
    userId: Int,
) {
    unlockedMap[packageKey(pkg, userId)] = SystemClock.elapsedRealtime()
}

internal fun refreshUnlock(
    pkg: String,
    userId: Int,
) {
    unlockedMap.computeIfPresent(packageKey(pkg, userId)) { _, _ -> SystemClock.elapsedRealtime() }
}

internal fun clearUnlocked() {
    unlockedMap.clear()
}

internal fun removeFromUnlocked(keys: Set<String>) {
    keys.forEach { unlockedMap.remove(it) }
}

internal val taskCache = ConcurrentHashMap<Int, TaskEntry>()

internal fun clearRuntimeStateForPackage(
    pkg: String,
    userId: Int? = null,
) {
    if (userId != null) {
        val key = packageKey(pkg, userId)
        unlockedMap.remove(key)
        taskCache.entries.removeIf { it.value.packageName == pkg && it.value.userId == userId }
    } else {
        unlockedMap.keys.removeIf { it.startsWith("$pkg:") }
        taskCache.entries.removeIf { it.value.packageName == pkg }
    }
}

internal fun relockOtherPackages(
    keepPkg: String?,
    userId: Int?,
) {
    if (keepPkg == BiometricAuthActivity.MODULE_PACKAGE) return
    val now = SystemClock.elapsedRealtime()
    val keepKey = keepPkg?.let { packageKey(it, userId) }
    unlockedMap.entries.removeIf { (key, _) ->
        if (key == keepKey) return@removeIf false
        val pkg = key.substringBeforeLast(':')
        val uid = key.substringAfterLast(':').toIntOrNull() ?: 0
        shouldRelockOnTransition(pkg, uid, now)
    }
}

// prefs cache — loaded once at boot, read-only in hook interceptors

@Volatile private var globalRelockDelaySeconds: Int = 0

@Volatile private var globalBlockScreenshots: Boolean = false

@Volatile private var globalRelockOnScreenOff: Boolean = true

@Volatile private var globalRelockOnTaskRemoved: Boolean = true

@Volatile private var globalPreventModuleUninstall: Boolean = false

@Volatile private var globalUseOpaqueUnlockPrompt: Boolean = false

private val appRelockOverrides = ConcurrentHashMap<String, Int>()

private val appBlockScreenshotsOverrides = ConcurrentHashMap<String, Boolean>()

private val appAllowedActivities = ConcurrentHashMap<String, Set<String>>()

internal fun getEffectiveRelockDelay(
    pkg: String,
    userId: Int,
): Int = appRelockOverrides[packageKey(pkg, userId)] ?: globalRelockDelaySeconds

internal fun shouldBlockScreenshots(
    pkg: String,
    userId: Int,
): Boolean = appBlockScreenshotsOverrides[packageKey(pkg, userId)] ?: globalBlockScreenshots

internal fun isActivityAllowed(
    pkg: String,
    userId: Int,
    className: String?,
    targetActivity: String?,
): Boolean {
    val allowed = appAllowedActivities[packageKey(pkg, userId)] ?: return false
    if (className != null && className in allowed) return true
    return targetActivity != null && targetActivity in allowed
}

internal fun shouldRelockOnScreenOff(): Boolean = globalRelockOnScreenOff

internal fun shouldRelockOnTaskRemoved(): Boolean = globalRelockOnTaskRemoved

internal fun shouldPreventModuleUninstall(): Boolean = globalPreventModuleUninstall

internal fun shouldUseOpaqueUnlockPrompt(): Boolean = globalUseOpaqueUnlockPrompt

internal fun loadHookPrefs(prefs: SharedPreferences) {
    globalRelockDelaySeconds = Prefs.RELOCK_DELAY_SECONDS.read(prefs)
    globalRelockOnScreenOff = Prefs.RELOCK_ON_SCREEN_OFF.read(prefs)
    globalRelockOnTaskRemoved = Prefs.RELOCK_ON_TASK_REMOVED.read(prefs)
    globalBlockScreenshots = Prefs.BLOCK_SCREENSHOTS.read(prefs)
    globalPreventModuleUninstall = Prefs.PREVENT_MODULE_UNINSTALL.read(prefs)
    globalUseOpaqueUnlockPrompt = Prefs.USE_OPAQUE_UNLOCK_PROMPT.read(prefs)
    appRelockOverrides.clear()
    appBlockScreenshotsOverrides.clear()
    appAllowedActivities.clear()
    prefs.all.keys.forEach { key ->
        if (!key.startsWith("app_override:")) return@forEach
        when {
            key.endsWith(":relock_delay_seconds") -> {
                val pkgKey = key.removePrefix("app_override:").removeSuffix(":relock_delay_seconds")
                appRelockOverrides[pkgKey] = prefs.getInt(key, 0)
            }

            key.endsWith(":block_screenshots") -> {
                val pkgKey = key.removePrefix("app_override:").removeSuffix(":block_screenshots")
                appBlockScreenshotsOverrides[pkgKey] = prefs.getBoolean(key, false)
            }

            key.endsWith(":allowed_activities") -> {
                val pkgKey = key.removePrefix("app_override:").removeSuffix(":allowed_activities")
                val activities =
                    prefs
                        .getString(key, "")
                        ?.split('\n')
                        ?.filterTo(mutableSetOf()) { it.isNotBlank() }
                        .orEmpty()
                if (activities.isNotEmpty()) appAllowedActivities[pkgKey] = activities
            }
        }
    }
    Logger.debug {
        "prefs loaded relockDelay=$globalRelockDelaySeconds " +
            "relockOnScreenOff=$globalRelockOnScreenOff " +
            "relockOnTaskRemoved=$globalRelockOnTaskRemoved " +
            "blockScreenshots=$globalBlockScreenshots " +
            "preventUninstall=$globalPreventModuleUninstall " +
            "opaquePrompt=$globalUseOpaqueUnlockPrompt " +
            "relockOverrides=${appRelockOverrides.size} " +
            "blockOverrides=${appBlockScreenshotsOverrides.size} " +
            "allowActivityOverrides=${appAllowedActivities.size}"
    }
}
