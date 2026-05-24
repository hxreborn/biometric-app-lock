package eu.hxreborn.biometricapplock.prefs

object Prefs {
    const val GROUP = "biometric_app_lock_prefs"

    val DARK_THEME_CONFIG = StringPref("dark_theme_config", ThemeMode.FOLLOW_SYSTEM.prefValue)
    val USE_DYNAMIC_COLOR = BoolPref("use_dynamic_color", true)
    val LAUNCHER_HIDDEN = BoolPref("launcher_hidden", false)
    val VERBOSE = BoolPref("verbose", false)

    val LOCKED_PACKAGES = StringPref("locked_packages", "")
    val RELOCK_DELAY_SECONDS = IntPref("relock_delay_seconds", 0)
    val DISABLE_FLAG_SECURE = BoolPref("disable_flag_secure", false)

    // Written on every sync so the hook can detect how stale its cached prefs are
    val LAST_REMOTE_WRITE = LongPref("_last_remote_write", 0L)
}
