package eu.hxreborn.biometricapplock.util

import android.content.SharedPreferences
import android.util.Log
import eu.hxreborn.biometricapplock.BuildConfig
import eu.hxreborn.biometricapplock.module
import eu.hxreborn.biometricapplock.prefs.Prefs

object Logger {
    const val TAG = "BiometricAppLock"

    fun log(
        level: Int,
        msg: String,
        t: Throwable? = null,
    ) = if (t != null) module.log(level, TAG, msg, t) else module.log(level, TAG, msg)

    inline fun debug(msg: () -> String) {
        if (debugEnabled()) module.log(Log.DEBUG, TAG, msg())
    }

    @PublishedApi
    internal fun debugEnabled(): Boolean =
        BuildConfig.DEBUG ||
            cachedPrefs()?.let { Prefs.VERBOSE.read(it) } == true

    @Volatile
    private var prefs: SharedPreferences? = null

    private fun cachedPrefs(): SharedPreferences? {
        if (inSystemServer) return null
        return prefs ?: runCatching { module.getRemotePreferences(Prefs.GROUP) }
            .getOrNull()
            ?.also { prefs = it }
    }

    @Volatile
    internal var inSystemServer = false
}
