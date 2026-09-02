package eu.hxreborn.biometricapplock.ui

import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.hxreborn.biometricapplock.App
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.prefs.AppPrefs
import eu.hxreborn.biometricapplock.prefs.Prefs
import eu.hxreborn.biometricapplock.prefs.ThemeMode
import eu.hxreborn.biometricapplock.ui.theme.BiometricAppLockTheme
import eu.hxreborn.biometricapplock.ui.viewmodel.ScopeViewModel
import eu.hxreborn.biometricapplock.ui.viewmodel.SelfLockState
import eu.hxreborn.biometricapplock.ui.viewmodel.SelfLockViewModel
import eu.hxreborn.biometricapplock.util.METHOD_CREDENTIAL
import eu.hxreborn.biometricapplock.util.normalizeMethods
import eu.hxreborn.biometricapplock.util.usableAuthenticators
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

class MainActivity :
    ComponentActivity(),
    XposedServiceHelper.OnServiceListener {
    private val viewModel: ScopeViewModel by viewModels { ScopeViewModel.Factory }
    private val selfLock: SelfLockViewModel by viewModels()

    private var promptInFlight = false
    private var cancellationSignal: CancellationSignal? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        App.from(this).addServiceListener(this)
        setContent {
            val app = App.from(this@MainActivity)
            val prefs by app.prefsRepository.state.collectAsStateWithLifecycle(initialValue = AppPrefs.Defaults)
            val lockState by selfLock.state.collectAsStateWithLifecycle()

            SideEffect {
                if (prefs.selfLock) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            val darkTheme =
                when (prefs.themeMode) {
                    ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            LaunchedEffect(darkTheme) {
                val style =
                    if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                    }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            BiometricAppLockTheme(
                themeMode = prefs.themeMode,
                useDynamicColor = prefs.useDynamicColor,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    MainScaffold(viewModel = viewModel)
                    if (prefs.selfLock) {
                        val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                        AnimatedVisibility(
                            visible = lockState !is SelfLockState.Unlocked,
                            enter = fadeIn(spec),
                            exit = fadeOut(spec),
                        ) {
                            Surface(modifier = Modifier.fillMaxSize()) {
                                SelfLockScreen(
                                    state = lockState,
                                    onUnlock = { promptUnlock() },
                                    onUseCredential = { promptUnlock(credentialOnly = true) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val pref = App.from(this).prefsRepository.read(Prefs.SELF_LOCK)
        if (!pref) {
            selfLock.setUnlocked()
        } else if (selfLock.state.value !is SelfLockState.Unlocked && !promptInFlight) {
            promptUnlock()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !promptInFlight) {
            selfLock.setLocked()
        }
    }

    private fun promptUnlock(credentialOnly: Boolean = false) {
        if (promptInFlight) return
        val bm = getSystemService(BiometricManager::class.java)
        val methods =
            if (credentialOnly) {
                METHOD_CREDENTIAL
            } else {
                normalizeMethods(App.from(this).prefsRepository.read(Prefs.SELF_LOCK_METHODS))
            }
        val authenticators = usableAuthenticators(this, bm, methods)
        if (authenticators == null) {
            // allow if no security enrolled for module settings app
            selfLock.setUnlocked()
            return
        }
        promptInFlight = true
        selfLock.setAuthenticating()
        var sawFailure = false
        val builder =
            BiometricPrompt
                .Builder(this)
                .setTitle(getString(R.string.self_lock_prompt_title))
                .setConfirmationRequired(false)
                .setAllowedAuthenticators(authenticators)
        if (authenticators and Authenticators.DEVICE_CREDENTIAL == 0) {
            builder.setNegativeButton(getString(android.R.string.cancel), mainExecutor) { _, _ -> }
        }
        val signal = CancellationSignal()
        cancellationSignal = signal
        runCatching {
            builder.build().authenticate(
                signal,
                mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        promptInFlight = false
                        selfLock.setUnlocked()
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        promptInFlight = false
                        when (errorCode) {
                            BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT,
                            BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT,
                            -> selfLock.setLockedOut()

                            else -> selfLock.setLocked(error = sawFailure)
                        }
                    }

                    override fun onAuthenticationFailed() {
                        sawFailure = true
                    }
                },
            )
        }.onFailure {
            promptInFlight = false
            selfLock.setLocked(error = true)
        }
    }

    override fun onServiceBind(service: XposedService) {
        viewModel.onServiceBound(service)
    }

    override fun onServiceDied(service: XposedService) {
        viewModel.onServiceDied()
    }

    override fun onDestroy() {
        cancellationSignal?.cancel()
        super.onDestroy()
        App.from(this).removeServiceListener(this)
    }
}
