package eu.hxreborn.biometricapplock.ui.viewmodel

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import eu.hxreborn.biometricapplock.App
import eu.hxreborn.biometricapplock.prefs.Prefs
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class FrameworkInfo(
    val name: String,
    val version: String,
)

enum class ModuleStatus { NotEnabled, RebootRequired, Enabled }

class ScopeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val localPrefs =
        application.getSharedPreferences(Prefs.GROUP, Context.MODE_PRIVATE)

    private val _framework = MutableStateFlow<FrameworkInfo?>(null)
    val framework: StateFlow<FrameworkInfo?> = _framework.asStateFlow()

    private val _scope = MutableStateFlow<Set<String>>(emptySet())
    val scope: StateFlow<Set<String>> = _scope.asStateFlow()

    // APK installed/updated after last boot means system_server is still running old hook code
    // (or none at all). Computed once at VM construction; the values don't change without process death
    private val apkUpdatedAfterBoot: Boolean by lazy {
        runCatching {
            val bootEpoch = System.currentTimeMillis() - SystemClock.elapsedRealtime()
            val info = application.packageManager.getPackageInfo(application.packageName, 0)
            info.lastUpdateTime > bootEpoch
        }.getOrDefault(false)
    }

    val moduleStatus: StateFlow<ModuleStatus> =
        _framework
            .map(::deriveStatus)
            .stateIn(viewModelScope, SharingStarted.Eagerly, deriveStatus(_framework.value))

    private fun deriveStatus(framework: FrameworkInfo?): ModuleStatus =
        when {
            framework == null -> ModuleStatus.NotEnabled
            apkUpdatedAfterBoot -> ModuleStatus.RebootRequired
            else -> ModuleStatus.Enabled
        }

    init {
        App.boundService?.let { onServiceBound(it) }
        _scope.value = readLockedPackages()
    }

    fun onServiceBound(service: XposedService) {
        _framework.value = FrameworkInfo(service.frameworkName, "v${service.frameworkVersion}")
    }

    fun onServiceDied() {
        _framework.value = null
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
        App.prefsRepository.save(Prefs.LOCKED_PACKAGES, packages.joinToString("|"))
    }

    companion object {
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
