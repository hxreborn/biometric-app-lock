package eu.hxreborn.biometricapplock.prefs

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import eu.hxreborn.biometricapplock.util.METHODS_DEFAULT
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val TAG = "BiometricAppLock"

data class AppPrefs(
    val themeMode: ThemeMode,
    val useDynamicColor: Boolean,
    val floatingNavBar: Boolean,
    val relockDelaySeconds: Int,
    val relockOnScreenOff: Boolean,
    val relockOnTaskRemoved: Boolean,
    val blockScreenshots: Boolean,
    val hideNotificationContent: Boolean,
    val unlockRequireConfirmation: Boolean,
    val unlockMethods: Int,
    val useOpaqueUnlockPrompt: Boolean,
    val preventModuleUninstall: Boolean,
    val selfLock: Boolean,
    val selfLockMethods: Int,
    val requireBiometricInstall: Boolean,
    val requireBiometricUninstall: Boolean,
    val autoCheckUpdate: Boolean,
    val lastDismissedAvailableVersion: String,
) {
    companion object {
        val Defaults =
            AppPrefs(
                themeMode = ThemeMode.FOLLOW_SYSTEM,
                useDynamicColor = true,
                floatingNavBar = true,
                relockDelaySeconds = 0,
                relockOnScreenOff = true,
                relockOnTaskRemoved = true,
                blockScreenshots = false,
                hideNotificationContent = false,
                unlockRequireConfirmation = false,
                unlockMethods = METHODS_DEFAULT,
                useOpaqueUnlockPrompt = false,
                preventModuleUninstall = false,
                selfLock = false,
                selfLockMethods = METHODS_DEFAULT,
                requireBiometricInstall = false,
                requireBiometricUninstall = false,
                autoCheckUpdate = true,
                lastDismissedAvailableVersion = "",
            )
    }
}

class PrefsRepository(
    private val local: SharedPreferences,
    private val remoteProvider: () -> SharedPreferences? = { null },
) {
    val state: Flow<AppPrefs> =
        callbackFlow {
            fun emit() {
                trySend(
                    AppPrefs(
                        themeMode = ThemeMode.fromPrefValue(Prefs.DARK_THEME_CONFIG.read(local)),
                        useDynamicColor = Prefs.USE_DYNAMIC_COLOR.read(local),
                        floatingNavBar = Prefs.FLOATING_NAV_BAR.read(local),
                        relockDelaySeconds = Prefs.RELOCK_DELAY_SECONDS.read(local),
                        relockOnScreenOff = Prefs.RELOCK_ON_SCREEN_OFF.read(local),
                        relockOnTaskRemoved = Prefs.RELOCK_ON_TASK_REMOVED.read(local),
                        blockScreenshots = Prefs.BLOCK_SCREENSHOTS.read(local),
                        hideNotificationContent = Prefs.HIDE_NOTIFICATION_CONTENT.read(local),
                        unlockRequireConfirmation = Prefs.UNLOCK_REQUIRE_CONFIRMATION.read(local),
                        unlockMethods = Prefs.UNLOCK_METHODS.read(local),
                        useOpaqueUnlockPrompt = Prefs.USE_OPAQUE_UNLOCK_PROMPT.read(local),
                        preventModuleUninstall = Prefs.PREVENT_MODULE_UNINSTALL.read(local),
                        selfLock = Prefs.SELF_LOCK.read(local),
                        selfLockMethods = Prefs.SELF_LOCK_METHODS.read(local),
                        requireBiometricInstall = Prefs.REQUIRE_BIOMETRIC_INSTALL.read(local),
                        requireBiometricUninstall = Prefs.REQUIRE_BIOMETRIC_UNINSTALL.read(local),
                        autoCheckUpdate = Prefs.AUTO_CHECK_UPDATE.read(local),
                        lastDismissedAvailableVersion =
                            Prefs.LAST_DISMISSED_AVAILABLE_VERSION.read(
                                local,
                            ),
                    ),
                )
            }

            emit()
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> emit() }
            local.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { local.unregisterOnSharedPreferenceChangeListener(listener) }
        }

    fun <T : Any> read(spec: PrefSpec<T>): T = spec.read(local)

    fun <T : Any> save(
        spec: PrefSpec<T>,
        value: T,
    ) {
        local.edit { spec.write(this, value) }
        runCatching {
            remoteProvider()?.edit(commit = false) {
                spec.write(this, value)
                Prefs.LAST_REMOTE_WRITE.write(this, System.currentTimeMillis())
            }
        }
    }

    fun syncToRemote() {
        val remote = remoteProvider() ?: return
        runCatching {
            var changed = false
            remote.edit(commit = false) {
                Prefs.all.forEach { spec ->
                    if (spec === Prefs.LAST_REMOTE_WRITE) return@forEach
                    if (spec === Prefs.SELF_LOCK) return@forEach
                    if (spec === Prefs.SELF_LOCK_METHODS) return@forEach
                    if (spec === Prefs.UNLOCK_REQUIRE_CONFIRMATION) return@forEach
                    if (spec === Prefs.UNLOCK_METHODS) return@forEach
                    if (spec.copyIfChanged(local, remote, this)) changed = true
                }

                local.all.forEach { (key, value) ->
                    if (!key.startsWith("app_override:")) return@forEach
                    if (remote.all[key] == value) return@forEach
                    changed = true
                    when (value) {
                        is Boolean -> {
                            putBoolean(key, value)
                        }

                        is Int -> {
                            putInt(key, value)
                        }

                        is Long -> {
                            putLong(key, value)
                        }

                        is String -> {
                            putString(key, value)
                        }

                        else -> {
                            Log.w(
                                TAG,
                                "syncToRemote skipping $key type=${value?.javaClass?.simpleName}",
                            )
                        }
                    }
                }

                if (changed) Prefs.LAST_REMOTE_WRITE.write(this, System.currentTimeMillis())
            }
        }
    }
}
