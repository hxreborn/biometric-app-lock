package eu.hxreborn.biometricapplock

import android.app.Application
import android.content.Context
import android.util.Log
import eu.hxreborn.biometricapplock.prefs.AppOverridesRepository
import eu.hxreborn.biometricapplock.prefs.Migration
import eu.hxreborn.biometricapplock.prefs.Prefs
import eu.hxreborn.biometricapplock.prefs.PrefsRepository
import eu.hxreborn.biometricapplock.updates.UpdateRepository
import eu.hxreborn.biometricapplock.util.RootShell
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArrayList

class App :
    Application(),
    XposedServiceHelper.OnServiceListener {
    @Volatile
    private var mService: XposedService? = null

    lateinit var prefsRepository: PrefsRepository
        private set

    lateinit var updateRepository: UpdateRepository
        private set

    lateinit var appOverridesRepository: AppOverridesRepository
        private set

    private val listeners = CopyOnWriteArrayList<XposedServiceHelper.OnServiceListener>()

    override fun onCreate() {
        super.onCreate()
        val localPrefs = getSharedPreferences(Prefs.GROUP, Context.MODE_PRIVATE)
        Migration.migrateIfNeeded(localPrefs)
        prefsRepository =
            PrefsRepository(localPrefs) {
                runCatching { mService?.getRemotePreferences(Prefs.GROUP) }.getOrNull()
            }
        updateRepository = UpdateRepository(this)
        appOverridesRepository =
            AppOverridesRepository(localPrefs) {
                runCatching { mService?.getRemotePreferences(Prefs.GROUP) }.getOrNull()
            }
        XposedServiceHelper.registerListener(this)
        if (BuildConfig.DEBUG) raiseLogBuffers()
    }

    private fun raiseLogBuffers() {
        Thread {
            runCatching { RootShell.exec("logcat -b main -G 8M", "logcat -b crash -G 4M") }
        }.apply { isDaemon = true }.start()
    }

    override fun onServiceBind(service: XposedService) {
        Log.i(TAG, "service bound: ${service.frameworkName} v${service.frameworkVersion}")
        mService = service
        prefsRepository.syncToRemote()
        listeners.forEach { it.onServiceBind(service) }
    }

    override fun onServiceDied(service: XposedService) {
        Log.w(TAG, "service died")
        mService = null
        listeners.forEach { it.onServiceDied(service) }
    }

    fun addServiceListener(listener: XposedServiceHelper.OnServiceListener) {
        listeners.add(listener)
        mService?.let {
            Log.d(TAG, "listener add replay=true")
            listener.onServiceBind(it)
        }
    }

    fun removeServiceListener(listener: XposedServiceHelper.OnServiceListener) {
        listeners.remove(listener)
        Log.d(TAG, "listener remove")
    }

    companion object {
        private const val TAG = "BiometricAppLock"

        fun from(context: Context): App = context.applicationContext as App
    }
}
