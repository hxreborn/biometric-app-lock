package eu.hxreborn.biometricapplock

import android.content.SharedPreferences
import android.os.Process
import eu.hxreborn.biometricapplock.hook.loadHookPrefs
import eu.hxreborn.biometricapplock.hook.lockedPackages
import eu.hxreborn.biometricapplock.hook.refreshSecureSurfaces
import eu.hxreborn.biometricapplock.hook.registerSystemServerHooks
import eu.hxreborn.biometricapplock.prefs.Prefs
import eu.hxreborn.biometricapplock.util.Logger
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

@PublishedApi
internal lateinit var module: BiometricAppLockModule
    private set

class BiometricAppLockModule : XposedModule() {
    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == Prefs.LOCKED_PACKAGES.key) {
                lockedPackages = parseLockedPackages(Prefs.LOCKED_PACKAGES.read(sp))
                Logger.info("config updated locked=${lockedPackages.size}")
            }
            loadHookPrefs(sp)
            refreshSecureSurfaces()
        }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        module = this
        Logger.info("loaded in ${param.processName}")
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        val prefs = getRemotePreferences(Prefs.GROUP)
        val locked = parseLockedPackages(Prefs.LOCKED_PACKAGES.read(prefs))
        Logger.info("system_server starting pid=${Process.myPid()} locked=${locked.size}")
        Logger.debug { "locked=$locked" }
        loadHookPrefs(prefs)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        registerSystemServerHooks(param.classLoader, locked)
    }

    private fun parseLockedPackages(raw: String): Set<String> =
        if (raw.isEmpty()) emptySet() else raw.split("|").toSet()
}
