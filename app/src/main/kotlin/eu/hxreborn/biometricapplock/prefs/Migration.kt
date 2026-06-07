package eu.hxreborn.biometricapplock.prefs

import android.content.SharedPreferences
import androidx.core.content.edit

internal object Migration {
    private const val PREF_VERSION = "pref_version"
    private const val CURRENT_VERSION = 1

    fun migrateIfNeeded(prefs: SharedPreferences) {
        val version = prefs.getInt(PREF_VERSION, 0)
        if (version < CURRENT_VERSION) {
            migrateToMultiUser(prefs)
            prefs.edit { putInt(PREF_VERSION, CURRENT_VERSION) }
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
