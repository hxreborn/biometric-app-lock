package eu.hxreborn.biometricapplock.prefs

import android.app.UiModeManager
import android.content.Context

enum class ThemeMode {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
    ;

    val prefValue: String get() = name.lowercase()

    fun applyAsAppNightMode(context: Context) {
        context.getSystemService(UiModeManager::class.java).setApplicationNightMode(
            when (this) {
                FOLLOW_SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                LIGHT -> UiModeManager.MODE_NIGHT_NO
                DARK -> UiModeManager.MODE_NIGHT_YES
            },
        )
    }

    companion object {
        fun fromPrefValue(value: String): ThemeMode =
            entries.firstOrNull { it.prefValue == value.lowercase() } ?: FOLLOW_SYSTEM
    }
}
