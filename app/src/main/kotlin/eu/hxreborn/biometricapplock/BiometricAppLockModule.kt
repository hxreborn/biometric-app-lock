package eu.hxreborn.biometricapplock

import android.content.SharedPreferences
import android.os.Process
import eu.hxreborn.biometricapplock.hook.loadHookPrefs
import eu.hxreborn.biometricapplock.hook.lockedPackages
import eu.hxreborn.biometricapplock.hook.parseLockedPackages
import eu.hxreborn.biometricapplock.hook.refreshSecureSurfaces
import eu.hxreborn.biometricapplock.hook.registerSystemServerHooks
import eu.hxreborn.biometricapplock.hook.unregisterPackageEvents
import eu.hxreborn.biometricapplock.prefs.Prefs
import eu.hxreborn.biometricapplock.util.Logger
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

@PublishedApi
internal lateinit var module: BiometricAppLockModule
    private set

class BiometricAppLockModule : XposedModule() {
    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            when {
                key == null -> {
                    lockedPackages = parseLockedPackages(Prefs.LOCKED_PACKAGES.read(sp))
                    loadHookPrefs(sp)
                    refreshSecureSurfaces()
                }

                key == Prefs.LOCKED_PACKAGES.key -> {
                    lockedPackages = parseLockedPackages(Prefs.LOCKED_PACKAGES.read(sp))
                    Logger.info("config updated locked=${lockedPackages.size}")
                    refreshSecureSurfaces()
                }

                key.endsWith(Prefs.BLOCK_SCREENSHOTS.key) -> {
                    loadHookPrefs(sp)
                    refreshSecureSurfaces()
                }

                // the uninstall hook re-reads the grant timestamp on demand, nothing to reload
                key == Prefs.UNINSTALL_AUTH_GRANT_MS.key -> {}

                else -> {
                    loadHookPrefs(sp)
                }
            }
        }

    private var systemServerClassLoader: ClassLoader? = null

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        module = this
        Logger.info("loaded in ${param.processName}")
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        systemServerClassLoader = param.classLoader
        installSystemServerHooks(param.classLoader, reloaded = false)
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        Logger.debug { "hot reloading pid=${Process.myPid()}" }
        unregisterPackageEvents()
        systemServerClassLoader?.let { param.setSavedInstanceState(it) }
        return true
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        module = this
        Logger.info("hot reload unhooking old=${param.oldHookHandles.size}")
        param.oldHookHandles.forEach { runCatching { it.unhook() } }
        val classLoader = param.savedInstanceState as? ClassLoader
        if (classLoader == null) {
            Logger.error("hot reload aborted, system_server classLoader unavailable")
            return
        }
        systemServerClassLoader = classLoader
        installSystemServerHooks(classLoader, reloaded = true)
    }

    private fun installSystemServerHooks(
        classLoader: ClassLoader,
        reloaded: Boolean,
    ) {
        val prefs = getRemotePreferences(Prefs.GROUP)
        val locked = parseLockedPackages(Prefs.LOCKED_PACKAGES.read(prefs))
        val phase = if (reloaded) "hot reloaded" else "starting"
        Logger.info("system_server $phase pid=${Process.myPid()} locked=${locked.size}")
        Logger.debug { "locked=$locked" }
        loadHookPrefs(prefs)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        registerSystemServerHooks(classLoader, locked)
    }
}
