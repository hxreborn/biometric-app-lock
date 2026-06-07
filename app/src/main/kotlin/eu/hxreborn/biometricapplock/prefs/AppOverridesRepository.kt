package eu.hxreborn.biometricapplock.prefs

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class AppOverrides(
    val relockDelaySeconds: Int?,
    val blockScreenshots: Boolean?,
    val allowedActivities: Set<String> = emptySet(),
)

data class RecentActivity(
    val className: String,
    val lastSeen: Long,
)

private const val MAX_RECENT_ACTIVITIES = 12

private fun SharedPreferences.getIntOrNull(key: String): Int? =
    if (contains(key)) getInt(key, 0) else null

private fun SharedPreferences.getBooleanOrNull(key: String): Boolean? =
    if (contains(key)) getBoolean(key, false) else null

private fun parseActivities(raw: String?): Set<String> =
    raw?.split('\n')?.filterTo(linkedSetOf()) { it.isNotBlank() }.orEmpty()

private fun parseRecents(raw: String?): List<RecentActivity> =
    raw
        ?.split('\n')
        ?.mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val name = line.substringBeforeLast(' ', line)
            val ts = line.substringAfterLast(' ', "").toLongOrNull() ?: 0L
            RecentActivity(name, ts)
        }.orEmpty()

private fun serializeRecents(recents: List<RecentActivity>): String =
    recents.joinToString("\n") { "${it.className} ${it.lastSeen}" }

class AppOverridesRepository(
    private val local: SharedPreferences,
    private val remoteProvider: () -> SharedPreferences? = { null },
) {
    private fun relockKey(pkg: String) = "app_override:$pkg:relock_delay_seconds"

    private fun blockScreenshotsKey(pkg: String) = "app_override:$pkg:block_screenshots"

    private fun allowedActivitiesKey(pkg: String) = "app_override:$pkg:allowed_activities"

    private fun recentsKey(pkg: String) = "recents:$pkg"

    private fun prefix(pkg: String) = "app_override:$pkg:"

    private fun currentOverrides(pkg: String) =
        AppOverrides(
            relockDelaySeconds = local.getIntOrNull(relockKey(pkg)),
            blockScreenshots = local.getBooleanOrNull(blockScreenshotsKey(pkg)),
            allowedActivities = parseActivities(local.getString(allowedActivitiesKey(pkg), null)),
        )

    fun observe(pkg: String): Flow<AppOverrides> =
        callbackFlow {
            trySend(currentOverrides(pkg))
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key?.startsWith(prefix(pkg)) == true) trySend(currentOverrides(pkg))
                }
            local.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { local.unregisterOnSharedPreferenceChangeListener(listener) }
        }

    fun observeRecentActivities(pkg: String): Flow<List<RecentActivity>> =
        callbackFlow {
            val key = recentsKey(pkg)
            trySend(parseRecents(local.getString(key, null)))
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
                    if (changed == key) trySend(parseRecents(local.getString(key, null)))
                }
            local.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { local.unregisterOnSharedPreferenceChangeListener(listener) }
        }

    private fun editLocalAndRemote(action: SharedPreferences.Editor.() -> Unit) {
        local.edit(action = action)
        runCatching {
            remoteProvider()?.edit(commit = false) {
                action()
                Prefs.LAST_REMOTE_WRITE.write(this, System.currentTimeMillis())
            }
        }
    }

    fun setRelockDelaySeconds(
        pkg: String,
        seconds: Int?,
    ) {
        val key = relockKey(pkg)
        editLocalAndRemote { if (seconds == null) remove(key) else putInt(key, seconds) }
    }

    fun setBlockScreenshots(
        pkg: String,
        blocked: Boolean?,
    ) {
        val key = blockScreenshotsKey(pkg)
        editLocalAndRemote { if (blocked == null) remove(key) else putBoolean(key, blocked) }
    }

    fun setAllowedActivities(
        pkg: String,
        activities: Set<String>,
    ) {
        val key = allowedActivitiesKey(pkg)
        editLocalAndRemote {
            if (activities.isEmpty()) remove(key) else putString(key, activities.joinToString("\n"))
        }
    }

    fun recordRecentActivity(
        pkg: String,
        className: String,
    ) {
        if (className.isBlank()) return
        val key = recentsKey(pkg)
        val existing = parseRecents(local.getString(key, null)).filter { it.className != className }
        val updated =
            (listOf(RecentActivity(className, System.currentTimeMillis())) + existing)
                .take(MAX_RECENT_ACTIVITIES)
        local.edit { putString(key, serializeRecents(updated)) }
    }

    fun reset(pkg: String) =
        editLocalAndRemote {
            remove(relockKey(pkg))
            remove(blockScreenshotsKey(pkg))
        }

    fun prune(installedPackageKeys: Set<String>) {
        val keys = local.all.keys
        val overrideKeys =
            keys.filter { key ->
                if (!key.startsWith("app_override:")) return@filter false
                // app_override:pkg:userId:suffix
                val pkgPart = key.removePrefix("app_override:")
                val firstColon = pkgPart.indexOf(':')
                if (firstColon == -1) return@filter false
                val secondColon = pkgPart.indexOf(':', firstColon + 1)
                if (secondColon == -1) return@filter false
                val packageKey = pkgPart.substring(0, secondColon)
                packageKey !in installedPackageKeys
            }
        if (overrideKeys.isNotEmpty()) {
            editLocalAndRemote { overrideKeys.forEach { remove(it) } }
        }
        val recentsKeys =
            keys.filter {
                it.startsWith("recents:") &&
                    it.removePrefix("recents:") !in installedPackageKeys
            }
        if (recentsKeys.isNotEmpty()) {
            local.edit { recentsKeys.forEach { remove(it) } }
        }
    }
}
