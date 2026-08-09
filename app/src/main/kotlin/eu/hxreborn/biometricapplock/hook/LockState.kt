package eu.hxreborn.biometricapplock.hook

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import eu.hxreborn.biometricapplock.BiometricAuthActivity
import eu.hxreborn.biometricapplock.prefs.Prefs
import eu.hxreborn.biometricapplock.util.Logger
import java.util.concurrent.ConcurrentHashMap

internal const val RELOCK_DELAY_NEVER = -1

internal data class TaskEntry(
    val packageName: String,
    val userId: Int,
    val topActivity: String? = null,
)

@Volatile
internal var lockedPackages: Set<String> = emptySet()

private fun packageKey(
    pkg: String,
    userId: Int?,
): String = "$pkg:$userId"

// pkg:userId being surfaced from recents on this thread with a posted auth already covering it
internal val recentsSurfaceKey = ThreadLocal<String?>()

// pkg:userId -> elapsedRealtime of last interaction, present only while that pkg is unlocked
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

// the grant is keyed by the authed intent action, not by package, so one prompt can't cover both
// an install and an uninstall. the short TTL spans the installer's internal navigation only
private const val SYSTEM_HANDLER_GRANT_TTL_MS = 10_000L

@Volatile
private var systemHandlerGrantedAt = 0L

@Volatile
private var systemHandlerGrantedAction: String? = null

internal fun grantSystemHandler(action: String?) {
    systemHandlerGrantedAction = action
    systemHandlerGrantedAt = SystemClock.elapsedRealtime()
}

internal fun isSystemHandler(pkg: String): Boolean = pkg in cachedSystemActionHandlers

// a null action is component-only internal navigation, it inherits the current grant
internal fun isSystemHandlerGrantFresh(action: String?): Boolean {
    val now = SystemClock.elapsedRealtime()
    if (now - systemHandlerGrantedAt >= SYSTEM_HANDLER_GRANT_TTL_MS) return false
    if (action == null) return true
    val stored = systemHandlerGrantedAction ?: return false
    return (isInstallAction(action) && isInstallAction(stored)) ||
        (
            isUninstallAction(action) &&
                isUninstallAction(
                    stored,
                )
        )
}

// hidden AOSP action PackageInstallerService fires when a Session.commit needs user confirmation
private const val ACTION_CONFIRM_INSTALL = "android.content.pm.action.CONFIRM_INSTALL"

@Suppress("DEPRECATION")
private fun isInstallAction(action: String): Boolean =
    action == Intent.ACTION_VIEW || action == Intent.ACTION_INSTALL_PACKAGE ||
        action == ACTION_CONFIRM_INSTALL

@Suppress("DEPRECATION")
private fun isUninstallAction(action: String): Boolean =
    action == Intent.ACTION_DELETE || action == Intent.ACTION_UNINSTALL_PACKAGE

// install and uninstall dialogs often share one package, so the package alone can't say which
// toggle applies. an unidentified action could be either, so honor both
internal fun shouldInterceptSystemHandler(action: String?): Boolean =
    when {
        action != null && isInstallAction(action) -> globalRequireBiometricInstall
        action != null && isUninstallAction(action) -> globalRequireBiometricUninstall
        else -> globalRequireBiometricInstall || globalRequireBiometricUninstall
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

// relocks every unlocked package whose delay elapsed and reports how many got dropped
internal fun relockElapsedUnlocks(): Int {
    val now = SystemClock.elapsedRealtime()
    var relocked = 0
    unlockedMap.keys.removeIf { key ->
        val pkg = key.substringBeforeLast(':')
        val uid = key.substringAfterLast(':').toIntOrNull() ?: 0
        val due = shouldRelockOnTransition(pkg, uid, now)
        if (due) relocked++
        due
    }
    return relocked
}

// prefs cache are loaded once at boot, read-only in hook interceptors

@Volatile
private var globalRelockDelaySeconds: Int = 0

@Volatile
private var globalBlockScreenshots: Boolean = false

@Volatile
private var globalRelockOnScreenOff: Boolean = true

@Volatile
private var globalRelockOnTaskRemoved: Boolean = true

@Volatile
private var globalPreventModuleUninstall: Boolean = false

@Volatile
private var globalUseOpaqueUnlockPrompt: Boolean = false

@Volatile
private var globalRequireBiometricInstall: Boolean = false

@Volatile
private var globalRequireBiometricUninstall: Boolean = false

// kept so the uninstall grant timestamp can be re-read on demand, not via the prefs listener
@Volatile
internal var hookPrefs: SharedPreferences? = null

private const val UNINSTALL_AUTH_GRANT_TTL_MS = 60_000L

private val appRelockOverrides = ConcurrentHashMap<String, Int>()

private val appBlockScreenshotsOverrides = ConcurrentHashMap<String, Boolean>()

private val appListedActivities = ConcurrentHashMap<String, Set<String>>()

private val appLockListedOnly = ConcurrentHashMap.newKeySet<String>()

internal fun getEffectiveRelockDelay(
    pkg: String,
    userId: Int,
): Int = appRelockOverrides[packageKey(pkg, userId)] ?: globalRelockDelaySeconds

internal fun shouldBlockScreenshots(
    pkg: String,
    userId: Int,
): Boolean = appBlockScreenshotsOverrides[packageKey(pkg, userId)] ?: globalBlockScreenshots

internal fun shouldForceSecure(
    pkg: String,
    userId: Int,
): Boolean =
    packageKey(pkg, userId) in lockedPackages &&
        isUnlocked(pkg, userId) &&
        shouldBlockScreenshots(pkg, userId)

internal fun isActivityExempt(
    pkg: String,
    userId: Int,
    className: String?,
    targetActivity: String?,
): Boolean {
    val key = packageKey(pkg, userId)
    val listed = appListedActivities[key] ?: return false
    // an unresolved name never proves exemption in either mode
    if (className == null && targetActivity == null) return false
    val inList =
        (className != null && className in listed) ||
            (targetActivity != null && targetActivity in listed)
    return if (key in appLockListedOnly) !inList else inList
}

internal fun shouldRelockOnScreenOff(): Boolean = globalRelockOnScreenOff

internal fun shouldRelockOnTaskRemoved(): Boolean = globalRelockOnTaskRemoved

internal fun shouldPreventModuleUninstall(): Boolean = globalPreventModuleUninstall

internal fun shouldUseOpaqueUnlockPrompt(): Boolean = globalUseOpaqueUnlockPrompt

internal fun requireBiometricForUninstall(): Boolean = globalRequireBiometricUninstall

internal fun hasFreshUninstallAuth(): Boolean {
    val prefs = hookPrefs ?: return false
    val ts = runCatching { Prefs.UNINSTALL_AUTH_GRANT_MS.read(prefs) }.getOrDefault(0L)
    if (ts <= 0L) return false
    return System.currentTimeMillis() - ts < UNINSTALL_AUTH_GRANT_TTL_MS
}

// last successful resolution. PackageManager isn't up at onSystemServerStarting, so the boot call
// comes back empty and the deferred refresh on the first launch intercept fills it in
@Volatile
private var cachedSystemActionHandlers: Set<String> = emptySet()

// system_server context for PM access. atms hands out the same one ATMS uses, the ActivityThread
// fallback covers the boot window before the first intercept captures atms
private fun systemContext(): Context? {
    atmsRef?.let { atms ->
        runCatching { reflection?.contextField?.get(atms) as? Context }
            .getOrNull()
            ?.let { return it }
    }
    return runCatching {
        val cls = Class.forName("android.app.ActivityThread")
        val thread = cls.getMethod("currentActivityThread").invoke(null) ?: return@runCatching null
        cls.getMethod("getSystemContext").invoke(thread) as? Context
    }.getOrNull()
}

// resolves the OS install/uninstall handler packages so the launch intercept covers their
// dialogs. MATCH_SYSTEM_ONLY drops third-party installers the user invoked deliberately, and the
// handler names differ per OEM so they are never hardcoded
private fun resolveSystemActionHandlers(): Set<String> {
    val needsInstall = globalRequireBiometricInstall
    val needsUninstall = globalRequireBiometricUninstall
    if (!needsInstall && !needsUninstall) {
        cachedSystemActionHandlers = emptySet()
        return emptySet()
    }
    val pm = systemContext()?.packageManager ?: return cachedSystemActionHandlers
    val handlers = mutableSetOf<String>()
    if (needsInstall) {
        runCatching {
            val install =
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        Uri.parse("content://placeholder/x.apk"),
                        "application/vnd.android.package-archive",
                    )
                    addCategory(Intent.CATEGORY_DEFAULT)
                }
            pm
                .queryIntentActivities(install, PackageManager.MATCH_SYSTEM_ONLY)
                .forEach { handlers += it.activityInfo.packageName }
        }.onFailure { Logger.warn("install handler resolve failed: ${it.message}") }
    }
    if (needsUninstall) {
        runCatching {
            val uninstall =
                Intent(Intent.ACTION_DELETE, Uri.fromParts("package", "placeholder", null))
            pm
                .queryIntentActivities(uninstall, PackageManager.MATCH_SYSTEM_ONLY)
                .forEach { handlers += it.activityInfo.packageName }
        }.onFailure { Logger.warn("uninstall handler resolve failed: ${it.message}") }
    }
    // self-protection lives on the deletePackageX hook, never on the launch path
    handlers -= BiometricAuthActivity.MODULE_PACKAGE
    if (handlers.isNotEmpty()) cachedSystemActionHandlers = handlers
    return handlers
}

internal fun parseLockedPackages(raw: String): Set<String> =
    if (raw.isEmpty()) {
        emptySet()
    } else {
        // pre-1.5 entries carry no userId and a downgrade can write them back, key them to user 0
        raw.split("|").mapTo(mutableSetOf()) { if (':' in it) it else "$it:0" }
    }

internal fun applyHookConfig(prefs: SharedPreferences) {
    val locked = parseLockedPackages(Prefs.LOCKED_PACKAGES.read(prefs))
    val changed = locked != lockedPackages
    lockedPackages = locked
    loadHookPrefs(prefs)
    if (changed) {
        Logger.info("config applied locked=${locked.size}")
        refreshSecureSurfaces()
    }
}

internal fun loadHookPrefs(prefs: SharedPreferences) {
    hookPrefs = prefs
    globalRelockDelaySeconds = Prefs.RELOCK_DELAY_SECONDS.read(prefs)
    globalRelockOnScreenOff = Prefs.RELOCK_ON_SCREEN_OFF.read(prefs)
    globalRelockOnTaskRemoved = Prefs.RELOCK_ON_TASK_REMOVED.read(prefs)
    globalBlockScreenshots = Prefs.BLOCK_SCREENSHOTS.read(prefs)
    globalPreventModuleUninstall = Prefs.PREVENT_MODULE_UNINSTALL.read(prefs)
    globalUseOpaqueUnlockPrompt = Prefs.USE_OPAQUE_UNLOCK_PROMPT.read(prefs)
    globalRequireBiometricInstall = Prefs.REQUIRE_BIOMETRIC_INSTALL.read(prefs)
    globalRequireBiometricUninstall = Prefs.REQUIRE_BIOMETRIC_UNINSTALL.read(prefs)
    appRelockOverrides.clear()
    appBlockScreenshotsOverrides.clear()
    appListedActivities.clear()
    appLockListedOnly.clear()
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
                if (activities.isNotEmpty()) appListedActivities[pkgKey] = activities
            }

            key.endsWith(":lock_listed_activities") -> {
                val pkgKey =
                    key.removePrefix("app_override:").removeSuffix(":lock_listed_activities")
                if (prefs.getBoolean(key, false)) appLockListedOnly += pkgKey
            }
        }
    }
    val systemHandlers = resolveSystemActionHandlers()
    Logger.debug {
        "prefs loaded relockDelay=$globalRelockDelaySeconds " +
            "relockOnScreenOff=$globalRelockOnScreenOff " +
            "relockOnTaskRemoved=$globalRelockOnTaskRemoved " +
            "blockScreenshots=$globalBlockScreenshots " +
            "preventUninstall=$globalPreventModuleUninstall " +
            "opaquePrompt=$globalUseOpaqueUnlockPrompt " +
            "reqInstall=$globalRequireBiometricInstall " +
            "reqUninstall=$globalRequireBiometricUninstall " +
            "systemHandlers=${systemHandlers.size} " +
            "relockOverrides=${appRelockOverrides.size} " +
            "blockOverrides=${appBlockScreenshotsOverrides.size} " +
            "activityOverrides=${appListedActivities.size} " +
            "lockListedOnly=${appLockListedOnly.size}"
    }
}
