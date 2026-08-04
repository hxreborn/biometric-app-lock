package eu.hxreborn.biometricapplock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import eu.hxreborn.biometricapplock.hook.applyHookConfig
import eu.hxreborn.biometricapplock.util.Logger

internal const val ACTION_CONFIG_SYNC = "eu.hxreborn.biometricapplock.action.CONFIG_SYNC"
internal const val EXTRA_CONFIG = "config"

// signature level, so only a build signed with the module key can push config into system_server
internal const val CONFIG_SYNC_PERMISSION = "eu.hxreborn.biometricapplock.permission.CONFIG_SYNC"

// carries the whole pref map because the hook cannot re-read the module's own prefs file, and on
// some Xposed builds the handle it holds never sees a write
internal fun buildConfigBundle(values: Map<String, Any?>): Bundle {
    val bundle = Bundle()
    values.forEach { (key, value) ->
        when (value) {
            is Boolean -> bundle.putBoolean(key, value)
            is Int -> bundle.putInt(key, value)
            is Long -> bundle.putLong(key, value)
            is Float -> bundle.putFloat(key, value)
            is String -> bundle.putString(key, value)
        }
    }
    return bundle
}

private class BundlePrefs(
    private val bundle: Bundle,
) : SharedPreferences {
    @Suppress("DEPRECATION")
    override fun getAll(): Map<String, Any?> = bundle.keySet().associateWith { bundle.get(it) }

    override fun getString(
        key: String?,
        defValue: String?,
    ): String? = if (bundle.containsKey(key)) bundle.getString(key, defValue) else defValue

    override fun getStringSet(
        key: String?,
        defValues: Set<String>?,
    ): Set<String>? = defValues

    override fun getInt(
        key: String?,
        defValue: Int,
    ): Int = bundle.getInt(key, defValue)

    override fun getLong(
        key: String?,
        defValue: Long,
    ): Long = bundle.getLong(key, defValue)

    override fun getFloat(
        key: String?,
        defValue: Float,
    ): Float = bundle.getFloat(key, defValue)

    override fun getBoolean(
        key: String?,
        defValue: Boolean,
    ): Boolean = bundle.getBoolean(key, defValue)

    override fun contains(key: String?): Boolean = bundle.containsKey(key)

    override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit
}

internal val configSyncReceiver =
    object : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent,
        ) {
            if (intent.action != ACTION_CONFIG_SYNC) return
            val bundle = intent.getBundleExtra(EXTRA_CONFIG) ?: return
            runCatching { applyHookConfig(BundlePrefs(bundle)) }
                .onFailure { Logger.warn("config sync failed: ${it.message}", it) }
        }
    }

internal fun registerConfigSync(
    context: Context,
    handler: Handler,
) {
    runCatching {
        context.registerReceiver(
            configSyncReceiver,
            IntentFilter(ACTION_CONFIG_SYNC),
            CONFIG_SYNC_PERMISSION,
            handler,
            Context.RECEIVER_EXPORTED,
        )
        Logger.info("registered config sync receiver")
    }.onFailure { Logger.warn("registerConfigSync failed: ${it.message}", it) }
}
