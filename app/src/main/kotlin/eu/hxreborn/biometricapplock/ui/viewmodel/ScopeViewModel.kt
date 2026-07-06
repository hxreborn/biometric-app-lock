package eu.hxreborn.biometricapplock.ui.viewmodel

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.hxreborn.biometricapplock.App
import eu.hxreborn.biometricapplock.BuildConfig
import eu.hxreborn.biometricapplock.prefs.Prefs
import eu.hxreborn.biometricapplock.util.RootShell
import io.github.libxposed.service.HotReloadResult
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FrameworkInfo(
    val name: String,
    val version: String,
    val supportsHotReload: Boolean,
)

enum class ModuleStatus { NotEnabled, RebootRequired, Enabled }

sealed interface ServiceLoadEvent {
    val epochMs: Long

    data class Boot(
        override val epochMs: Long,
    ) : ServiceLoadEvent

    data class HotReload(
        override val epochMs: Long,
    ) : ServiceLoadEvent
}

class ScopeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val app = App.from(application)
    private val localPrefs = application.getSharedPreferences(Prefs.GROUP, Context.MODE_PRIVATE)

    @Volatile
    private var mService: XposedService? = null

    private val _framework = MutableStateFlow<FrameworkInfo?>(null)
    val framework: StateFlow<FrameworkInfo?> = _framework.asStateFlow()

    private val _scope = MutableStateFlow<Set<String>>(emptySet())
    val scope: StateFlow<Set<String>> = _scope.asStateFlow()

    private val _serviceLoadEvent = MutableStateFlow<ServiceLoadEvent?>(null)
    val serviceLoadEvent: StateFlow<ServiceLoadEvent?> = _serviceLoadEvent.asStateFlow()

    private val _rootGranted = MutableStateFlow<Boolean?>(null)
    val rootGranted: StateFlow<Boolean?> = _rootGranted.asStateFlow()

    private val apkUpdatedAfterBoot: Boolean by lazy {
        runCatching {
            val bootEpoch = System.currentTimeMillis() - SystemClock.elapsedRealtime()
            val info = application.packageManager.getPackageInfo(application.packageName, 0)
            info.lastUpdateTime > bootEpoch
        }.getOrDefault(false)
    }

    val moduleStatus: StateFlow<ModuleStatus> =
        _framework.map(::deriveStatus).stateIn(viewModelScope, SharingStarted.Eagerly, deriveStatus(_framework.value))

    private fun deriveStatus(framework: FrameworkInfo?): ModuleStatus =
        when {
            framework == null -> ModuleStatus.NotEnabled
            apkUpdatedAfterBoot && !framework.supportsHotReload -> ModuleStatus.RebootRequired
            else -> ModuleStatus.Enabled
        }

    private val serviceListener =
        object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                onServiceBound(service)
            }

            override fun onServiceDied(service: XposedService) {
                onServiceDied()
            }
        }

    init {
        app.addServiceListener(serviceListener)
        _scope.value = readLockedPackages()
        viewModelScope.launch(Dispatchers.IO) {
            _rootGranted.value = RootShell.isRootGranted()
        }
    }

    override fun onCleared() {
        super.onCleared()
        app.removeServiceListener(serviceListener)
    }

    fun onServiceBound(service: XposedService) {
        mService = service
        val supportsHotReload = service.apiVersion >= XposedService.API_102
        _framework.value =
            FrameworkInfo(
                name = service.frameworkName,
                version = "v${service.frameworkVersion}",
                supportsHotReload = supportsHotReload,
            )
        _serviceLoadEvent.value = deriveServiceLoadEvent(supportsHotReload)
    }

    private fun deriveServiceLoadEvent(supportsHotReload: Boolean): ServiceLoadEvent {
        val bootEpochMs = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val updateTime =
            runCatching {
                val app = getApplication<Application>()
                app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime
            }.getOrDefault(0L)
        return if (supportsHotReload && updateTime > bootEpochMs) {
            ServiceLoadEvent.HotReload(updateTime)
        } else {
            ServiceLoadEvent.Boot(bootEpochMs)
        }
    }

    fun onServiceDied() {
        mService = null
        _framework.value = null
        _serviceLoadEvent.value = null
    }

    fun triggerHotReload() {
        if (!BuildConfig.DEBUG) return
        val service = mService
        if (service == null) {
            toast("hot reload: service not bound")
            return
        }
        if (service.apiVersion < XposedService.API_102) {
            toast("hot reload needs api 102, framework is ${service.apiVersion}")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val targets = service.getRunningTargets()
                if (targets.isEmpty()) {
                    toast("hot reload: no running targets")
                    return@runCatching
                }
                targets.forEach { target ->
                    service.hotReloadModule(target, null) { reloaded, result ->
                        Log.i(TAG, "hot reload pkg=${reloaded.processName} status=${result.status()} msg=${result.message()}")
                        if (result.status() == HotReloadResult.Status.SUCCEEDED) {
                            _serviceLoadEvent.value = ServiceLoadEvent.HotReload(System.currentTimeMillis())
                        }
                        toast("hot reload ${reloaded.processName}: ${result.status()}")
                    }
                }
            }.onFailure {
                Log.w(TAG, "hot reload trigger failed", it)
                toast("hot reload failed: ${it.message}")
            }
        }
    }

    private fun toast(message: String) {
        viewModelScope.launch(Dispatchers.Main) {
            Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleScope(
        packageName: String,
        enable: Boolean,
    ) {
        val updated = if (enable) _scope.value + packageName else _scope.value - packageName
        _scope.value = updated
        saveLockedPackages(updated)
    }

    fun clearScope(packages: Set<String> = _scope.value) {
        if (packages.isEmpty()) return
        val updated = _scope.value - packages
        _scope.value = updated
        saveLockedPackages(updated)
    }

    fun restoreScope(previous: Set<String>) {
        val updated = _scope.value + previous
        _scope.value = updated
        saveLockedPackages(updated)
    }

    private fun readLockedPackages(): Set<String> {
        val raw = Prefs.LOCKED_PACKAGES.read(localPrefs)
        return if (raw.isEmpty()) emptySet() else raw.split("|").toSet()
    }

    private fun saveLockedPackages(packages: Set<String>) {
        app.prefsRepository.save(Prefs.LOCKED_PACKAGES, packages.joinToString("|"))
    }

    companion object {
        private const val TAG = "BiometricAppLock"

        val Factory =
            viewModelFactory {
                initializer {
                    ScopeViewModel(
                        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application,
                    )
                }
            }
    }
}
