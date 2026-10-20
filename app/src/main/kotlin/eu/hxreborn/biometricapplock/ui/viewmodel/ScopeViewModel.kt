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
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FrameworkInfo(
    val name: String,
    val version: String,
    val api: Int,
    val supportsHotReload: Boolean,
    val systemInScope: Boolean,
)

enum class ModuleStatus { NotEnabled, RebootRequired, Enabled }

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
            framework == null || !framework.systemInScope -> ModuleStatus.NotEnabled
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
                version = service.frameworkVersion,
                api = service.apiVersion,
                supportsHotReload = supportsHotReload,
                systemInScope = readSystemInScope(service),
            )
    }

    fun refreshFrameworkScope() {
        val service = mService ?: return
        _framework.update { it?.copy(systemInScope = readSystemInScope(service)) }
    }

    private fun readSystemInScope(service: XposedService): Boolean = runCatching { SYSTEM_SCOPE in service.scope }.getOrDefault(true)

    fun onServiceDied() {
        mService = null
        _framework.value = null
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
        val key = if (':' in packageName) packageName else "$packageName:0"
        val updated = if (enable) _scope.value + key else _scope.value - key
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
        val normalizedPrevious = Prefs.parseLockedPackages(previous.joinToString("|"))
        val updated = _scope.value + normalizedPrevious
        _scope.value = updated
        saveLockedPackages(updated)
    }

    private fun readLockedPackages(): Set<String> {
        val raw = Prefs.LOCKED_PACKAGES.read(localPrefs)
        val normalized = Prefs.parseLockedPackages(raw)
        val cleanRaw = Prefs.serializeLockedPackages(normalized)
        if (cleanRaw != raw) {
            saveLockedPackages(normalized)
        }
        return normalized
    }

    private fun saveLockedPackages(packages: Set<String>) {
        val serialized = Prefs.serializeLockedPackages(packages)
        app.prefsRepository.save(Prefs.LOCKED_PACKAGES, serialized)
    }

    companion object {
        private const val TAG = "BiometricAppLock"
        private const val SYSTEM_SCOPE = "system"

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
