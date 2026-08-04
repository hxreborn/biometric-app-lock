package eu.hxreborn.biometricapplock.prefs

import eu.hxreborn.biometricapplock.util.METHODS_DEFAULT

object Prefs {
    const val GROUP = "biometric_app_lock_prefs"

    val DARK_THEME_CONFIG = StringPref("dark_theme_config", ThemeMode.FOLLOW_SYSTEM.prefValue)
    val USE_DYNAMIC_COLOR = BoolPref("use_dynamic_color", true)
    val LOCKED_PACKAGES = StringPref("locked_packages", "")
    val RELOCK_DELAY_SECONDS = IntPref("relock_delay_seconds", 0)
    val RELOCK_ON_SCREEN_OFF = BoolPref("relock_on_screen_off", true)
    val RELOCK_ON_TASK_REMOVED = BoolPref("relock_on_task_removed", true)
    val BLOCK_SCREENSHOTS = BoolPref("block_screenshots", false)
    val UNLOCK_REQUIRE_CONFIRMATION = BoolPref("unlock_require_confirmation", false)

    // METHOD_* mask the unlock prompt offers for apps with no per-app override
    val UNLOCK_METHODS = IntPref("unlock_methods", METHODS_DEFAULT)

    // compat fallback for OEMs (e.g. HyperOS) where the translucent prompt loses focus and
    // self-cancels. AOSP is fine with translucent, so this stays off by default
    val USE_OPAQUE_UNLOCK_PROMPT = BoolPref("use_opaque_unlock_prompt", false)
    val PREVENT_MODULE_UNINSTALL = BoolPref("prevent_module_uninstall", false)
    val SELF_LOCK = BoolPref("self_lock", false)
    val SELF_LOCK_METHODS = IntPref("self_lock_methods", METHODS_DEFAULT)

    // require biometric before the system install/uninstall dialogs, regardless of which app or
    // source started the action. uninstall is also backstopped at the binder delete path
    val REQUIRE_BIOMETRIC_INSTALL = BoolPref("require_biometric_install", false)
    val REQUIRE_BIOMETRIC_UNINSTALL = BoolPref("require_biometric_uninstall", false)

    // wall-clock millis the prompt writes after a successful uninstall auth. any value within the
    // last 60s counts as a fresh grant, so the user authenticates once and the retry proceeds
    val UNINSTALL_AUTH_GRANT_MS = LongPref("uninstall_auth_grant_ms", 0L)

    // hook reads this to gauge cache staleness
    val LAST_REMOTE_WRITE = LongPref("_last_remote_write", 0L)

    val FLOATING_NAV_BAR = BoolPref("floating_nav_bar", true)

    val AUTO_CHECK_UPDATE = BoolPref("auto_check_update", true)
    val LAST_UPDATE_CHECK_MS = LongPref("last_update_check_ms", 0L)
    val LAST_RELEASE_JSON = StringPref("last_release_json", "")
    val LAST_REMOTE_VERSION_CODE = IntPref("last_remote_version_code", 0)
    val LAST_CHANGELOG_JSON = StringPref("last_changelog_json", "")
    val LAST_DISMISSED_AVAILABLE_VERSION = StringPref("last_dismissed_available_version", "")
    val RELEASE_ETAG = StringPref("release_etag", "")
    val CHANGELOG_ETAG = StringPref("changelog_etag", "")

    val all: List<PrefSpec<*>> =
        listOf(
            DARK_THEME_CONFIG,
            USE_DYNAMIC_COLOR,
            LOCKED_PACKAGES,
            RELOCK_DELAY_SECONDS,
            RELOCK_ON_SCREEN_OFF,
            RELOCK_ON_TASK_REMOVED,
            BLOCK_SCREENSHOTS,
            UNLOCK_REQUIRE_CONFIRMATION,
            UNLOCK_METHODS,
            USE_OPAQUE_UNLOCK_PROMPT,
            PREVENT_MODULE_UNINSTALL,
            SELF_LOCK,
            SELF_LOCK_METHODS,
            REQUIRE_BIOMETRIC_INSTALL,
            REQUIRE_BIOMETRIC_UNINSTALL,
            UNINSTALL_AUTH_GRANT_MS,
            LAST_REMOTE_WRITE,
            FLOATING_NAV_BAR,
            AUTO_CHECK_UPDATE,
            LAST_UPDATE_CHECK_MS,
            LAST_RELEASE_JSON,
            LAST_REMOTE_VERSION_CODE,
            LAST_CHANGELOG_JSON,
            LAST_DISMISSED_AVAILABLE_VERSION,
            RELEASE_ETAG,
            CHANGELOG_ETAG,
        )
}
