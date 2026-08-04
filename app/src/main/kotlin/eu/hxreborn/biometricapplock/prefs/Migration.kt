package eu.hxreborn.biometricapplock.prefs

import android.content.SharedPreferences
import androidx.core.content.edit
import eu.hxreborn.biometricapplock.util.METHODS_DEFAULT
import eu.hxreborn.biometricapplock.util.METHOD_BIOMETRIC
import eu.hxreborn.biometricapplock.util.METHOD_WEAK_OK

// TODO remove every migration after 2026-10-04
internal object Migration {
    private const val PREF_VERSION = "pref_version"
    private const val CURRENT_VERSION = 2
    private const val LEGACY_CRED_FALLBACK = "cred_fallback"
    private const val LEGACY_SELF_LOCK_CRED_FALLBACK = "self_lock_cred_fallback"

    fun migrateIfNeeded(prefs: SharedPreferences) {
        val version = prefs.getInt(PREF_VERSION, 0)
        if (version >= CURRENT_VERSION) return
        if (version < 1) migrateToMultiUser(prefs)
        if (version < 2) migrateCredFallbackToMethods(prefs)
        prefs.edit { putInt(PREF_VERSION, CURRENT_VERSION) }
    }

    private fun migrateCredFallbackToMethods(prefs: SharedPreferences) {
        migrateCredFallback(prefs, LEGACY_CRED_FALLBACK, Prefs.UNLOCK_METHODS.key)
        migrateCredFallback(prefs, LEGACY_SELF_LOCK_CRED_FALLBACK, Prefs.SELF_LOCK_METHODS.key)
    }

    private fun migrateCredFallback(
        prefs: SharedPreferences,
        legacyKey: String,
        methodsKey: String,
    ) {
        if (!prefs.contains(legacyKey)) return
        val methods =
            if (prefs.getBoolean(legacyKey, true)) {
                METHODS_DEFAULT
            } else {
                METHOD_BIOMETRIC or METHOD_WEAK_OK
            }
        prefs.edit {
            putInt(methodsKey, methods)
            remove(legacyKey)
        }
    }

    private fun migrateToMultiUser(prefs: SharedPreferences) {
        val lockedPackagesRaw = prefs.getString(Prefs.LOCKED_PACKAGES.key, "") ?: ""
        val allEntries = prefs.all

        prefs.edit {
            if (lockedPackagesRaw.isNotEmpty()) {
                val migrated =
                    lockedPackagesRaw.split("|").joinToString("|") { pkg ->
                        if (pkg.contains(":")) pkg else "$pkg:0"
                    }
                if (migrated != lockedPackagesRaw) {
                    putString(Prefs.LOCKED_PACKAGES.key, migrated)
                }
            }

            allEntries.forEach { (key, value) ->
                when {
                    key.startsWith("app_override:") -> {
                        val parts = key.split(":")
                        if (parts.size == 3) {
                            val pkg = parts[1]
                            val suffix = parts[2]
                            if (!pkg.contains(":")) {
                                val newKey = "app_override:$pkg:0:$suffix"
                                when (value) {
                                    is Int -> putInt(newKey, value)
                                    is Boolean -> putBoolean(newKey, value)
                                    is String -> putString(newKey, value)
                                }
                                remove(key)
                            }
                        }
                    }

                    key.startsWith("recents:") -> {
                        val pkg = key.removePrefix("recents:")
                        if (!pkg.contains(":")) {
                            val newKey = "recents:$pkg:0"
                            putString(newKey, value as? String)
                            remove(key)
                        }
                    }
                }
            }
        }
    }
}
