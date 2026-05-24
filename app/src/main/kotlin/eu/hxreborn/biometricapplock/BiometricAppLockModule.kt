package eu.hxreborn.biometricapplock

import android.os.Process
import android.util.Log
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
    override fun onModuleLoaded(param: ModuleLoadedParam) {
        module = this
        Logger.log(Log.INFO, "loaded in ${param.processName}")
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        Logger.inSystemServer = true
        val locked = readLockedPackages()
        Logger.log(Log.INFO, "system_server starting pid=${Process.myPid()} locked=$locked")
        runCatching { registerSystemServerHooks(param.classLoader, locked) }
            .onFailure {
                Logger.log(
                    Log.ERROR,
                    "registerSystemServerHooks failed: ${it.message}",
                    it,
                )
            }
    }

    private fun readLockedPackages(): Set<String> =
        runCatching {
            val prefs = getRemotePreferences(Prefs.GROUP)
            val raw = Prefs.LOCKED_PACKAGES.read(prefs)
            if (raw.isEmpty()) emptySet() else raw.split("|").toSet()
        }.getOrElse {
            Logger.log(Log.ERROR, "failed to read locked packages: ${it.message}", it)
            emptySet()
        }
}
