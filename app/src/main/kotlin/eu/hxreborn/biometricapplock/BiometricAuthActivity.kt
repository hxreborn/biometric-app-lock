package eu.hxreborn.biometricapplock

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.util.Log
import eu.hxreborn.biometricapplock.prefs.Prefs
import eu.hxreborn.biometricapplock.util.METHODS_ALL
import eu.hxreborn.biometricapplock.util.METHOD_BIOMETRIC
import eu.hxreborn.biometricapplock.util.METHOD_WEAK_OK
import eu.hxreborn.biometricapplock.util.getUserHandle
import eu.hxreborn.biometricapplock.util.miuiFaceEnrollmentCount
import eu.hxreborn.biometricapplock.util.normalizeMethods
import eu.hxreborn.biometricapplock.util.usableAuthenticators

private const val TAG = "BiometricAppLock"

/**
 * Thin activity the hook redirects locked launches to. Runs the system BiometricPrompt and, once it
 * passes, sends a one-time token back so the hook resumes the real launch. Translucent by default.
 * [OpaqueAuthActivity] is the solid variant for OEMs that cancel the see-through prompt.
 */
open class BiometricAuthActivity : Activity() {
    private var targetPkg: String? = null
    private var authToken: String? = null
    private var uninstallAuth = false
    private var replied = false

    // the launch transition cancels a prompt started before the window gains focus
    private var pendingPrompt: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPkg = intent.getStringExtra(EXTRA_TARGET_PKG)
        authToken = intent.getStringExtra(EXTRA_AUTH_TOKEN)
        uninstallAuth = intent.getBooleanExtra(EXTRA_UNINSTALL_AUTH, false)

        if (uninstallAuth) {
            Log.i(TAG, "uninstall auth pkg=$targetPkg")
            val label =
                targetPkg?.let(::loadLabel) ?: getString(R.string.biometric_prompt_default_target)
            pendingPrompt = {
                showPrompt(getString(R.string.biometric_prompt_uninstall_title, label))
            }
            return
        }

        if (targetPkg.isNullOrEmpty() || authToken.isNullOrEmpty()) {
            Log.w(TAG, "missing extras, finishing")
            finish()
            return
        }
        Log.i(TAG, "gating $targetPkg via=${javaClass.simpleName}")
        val pkg = targetPkg ?: return
        val userId = intent.getIntExtra(EXTRA_TARGET_USER_ID, 0)
        intent.getStringExtra(EXTRA_TARGET_ACTIVITY)?.let { activity ->
            runCatching {
                App
                    .from(
                        this,
                    ).appOverridesRepository
                    .recordRecentActivity("$pkg:$userId", activity)
            }
        }
        val label =
            runCatching {
                val launcherApps = getSystemService(LauncherApps::class.java)
                val info = launcherApps.getActivityList(pkg, getUserHandle(userId)).firstOrNull()
                // installer handlers have no launcher activities, fall back to the app label
                info?.label?.toString() ?: loadLabel(pkg)
            }.getOrDefault(pkg)
        pendingPrompt = {
            showPrompt(getString(R.string.biometric_prompt_unlock_title, label), "$pkg:$userId")
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        pendingPrompt?.invoke()
        pendingPrompt = null
    }

    private fun loadLabel(pkg: String): String =
        runCatching {
            packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString()
        }.getOrDefault(pkg)

    private var miuiFaceAuth: eu.hxreborn.biometricapplock.util.MiuiFaceAuthenticator? = null
    private var faceScanOverlay: eu.hxreborn.biometricapplock.util.FaceScanOverlay? = null

    override fun onDestroy() {
        miuiFaceAuth?.cancel()
        faceScanOverlay?.dismiss()
        Log.d(TAG, "onDestroy replied=$replied pkg=$targetPkg")
        if (!replied) onResult(AUTH_CANCELLED)
        super.onDestroy()
    }

    private fun showPrompt(
        title: String,
        packageKey: String? = null,
    ) {
        val bm = getSystemService(BiometricManager::class.java)
        // an unusable per-app policy keeps the app locked, never widens to the global one
        val methods = packageKey?.let(::appMethods) ?: globalMethods()
        val authenticators = usableAuthenticators(bm, methods)
        if (authenticators == null) {
            Log.w(TAG, "no usable auth method methods=$methods pkg=$targetPkg")
            onResult(AUTH_CANCELLED)
            return
        }

        val cancellation = CancellationSignal()

        val requireConfirmation =
            App.from(this).prefsRepository.read(Prefs.UNLOCK_REQUIRE_CONFIRMATION)

        // Run the custom MIUI face path when:
        //   - Device is Xiaomi or POCO (POCO is a Xiaomi sub-brand with the same face service)
        //   - Method mask includes biometric AND weak is allowed (METHOD_WEAK_OK). Strong-only
        //     apps must not be unlocked by a Class 2 MIUI face sensor.
        //   - Confirmation is not required (face unlock cannot satisfy an explicit confirm step)
        //   - This is not the uninstall backstop (which must require explicit credential entry)
        // methods is always non-null here — it is resolved by showPrompt's caller before this line.
        val isXiaomiOrPoco =
            android.os.Build.MANUFACTURER
                .equals("Xiaomi", ignoreCase = true) ||
                android.os.Build.MANUFACTURER
                    .equals("Poco", ignoreCase = true)
        val shouldRunCustomMiuiFace =
            isXiaomiOrPoco &&
                methods and METHOD_BIOMETRIC != 0 &&
                methods and METHOD_WEAK_OK != 0 &&
                !requireConfirmation &&
                !uninstallAuth

        if (shouldRunCustomMiuiFace) {
            miuiFaceAuth =
                eu.hxreborn.biometricapplock.util.MiuiFaceAuthenticator { success ->
                    runOnUiThread {
                        if (success) {
                            faceScanOverlay?.setState(
                                eu.hxreborn.biometricapplock.util.FaceScanOverlay.State.SUCCESS,
                            )
                            // Brief delay so the user sees the success animation before the screen closes
                            window.decorView.postDelayed({
                                cancellation.cancel()
                                onResult(AUTH_OK)
                            }, 600)
                        } else {
                            faceScanOverlay?.setState(
                                eu.hxreborn.biometricapplock.util.FaceScanOverlay.State.FAILED,
                            )
                        }
                    }
                }
            // Guard: only start the scanner if the service is present AND a face is enrolled.
            // isAvailable() checks the binder; miuiFaceEnrollmentCount checks the feature flag.
            val enrolledCount = miuiFaceEnrollmentCount(this) ?: 0
            if (miuiFaceAuth?.isAvailable() == true && enrolledCount >= 1) {
                faceScanOverlay =
                    eu.hxreborn.biometricapplock.util
                        .FaceScanOverlay(this)
                faceScanOverlay?.show()
                miuiFaceAuth?.authenticate()
            } else {
                val svc = miuiFaceAuth?.isAvailable()
                Log.d(TAG, "MIUI face skipped: service=$svc enrolled=$enrolledCount")
            }
        }

        val executor = mainExecutor

        val builder =
            BiometricPrompt
                .Builder(this)
                .setTitle(title)
                .setConfirmationRequired(requireConfirmation)
                .setAllowedAuthenticators(authenticators)
        if (authenticators and Authenticators.DEVICE_CREDENTIAL == 0) {
            builder.setNegativeButton(getString(android.R.string.cancel), executor) { _, _ ->
                cancellation.cancel()
                onResult(AUTH_CANCELLED)
            }
        }
        builder.build().authenticate(
            cancellation,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) = onResult(AUTH_OK)

                override fun onAuthenticationFailed() {
                    Log.w(TAG, "attempt failed, prompt stays open")
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    Log.w(TAG, "auth error code=$errorCode msg=$errString pkg=$targetPkg")
                    onResult(AUTH_ERROR)
                }
            },
        )
    }

    private fun appMethods(packageKey: String): Int? =
        runCatching { App.from(this).appOverridesRepository.authMethods(packageKey) }
            .getOrNull()
            ?.takeIf { it and METHODS_ALL != 0 }

    private fun globalMethods(): Int =
        normalizeMethods(App.from(this).prefsRepository.read(Prefs.UNLOCK_METHODS))

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop replied=$replied pkg=$targetPkg")
        // the system prompt steals focus and stops this activity, so only finish once there is a
        // result, or the prompt dies before the user can answer
        if (replied) finish()
    }

    private fun onResult(code: Int) {
        if (replied) return
        replied = true
        faceScanOverlay?.dismiss()
        faceScanOverlay = null
        Log.d(TAG, "onResult code=$code pkg=$targetPkg uninstallAuth=$uninstallAuth")
        if (uninstallAuth) {
            // nothing is waiting behind this prompt, so grant on success and leave the screen
            if (code == AUTH_OK) grantUninstall()
        } else if (code == AUTH_OK) {
            if (!launchTarget()) goHome()
        } else {
            goHome()
        }
        // the system prompt grabbed focus on open, so onStop already ran and won't run again to
        // finish the activity. without this the translucent window lingers and eats touches
        finish()
    }

    private fun grantUninstall() {
        runCatching {
            App.from(this).prefsRepository.save(
                Prefs.UNINSTALL_AUTH_GRANT_MS,
                System.currentTimeMillis(),
            )
        }.onFailure { Log.w(TAG, "grant write failed: ${it.message}") }
        Log.i(TAG, "uninstall auth granted pkg=$targetPkg")
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    private fun launchTarget(): Boolean {
        val pkg = targetPkg ?: return false
        val token = authToken ?: return false
        // only carries the token back so the hook can resume the real launch
        val signal = buildSignalIntent(pkg)
        signal.putExtra(EXTRA_AUTH_TOKEN, token)
        signal.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        Log.i(TAG, "auth ok, signaling resume pkg=$pkg")
        return runCatching { startActivity(signal) }
            .onFailure {
                Log.w(
                    TAG,
                    "signal failed pkg=$pkg: ${it.message}",
                )
            }.isSuccess
    }

    // the launcher entry covers normal apps. installer handlers have none, so fall back to any
    // exported unguarded activity. it never actually runs, the hook consumes the token and aborts
    private fun buildSignalIntent(pkg: String): Intent {
        packageManager.getLaunchIntentForPackage(pkg)?.let { return it }
        val implicit =
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(pkg)
            }
        if (packageManager.resolveActivity(implicit, 0) != null) return implicit
        runCatching {
            packageManager
                .getPackageInfo(
                    pkg,
                    PackageManager.GET_ACTIVITIES,
                ).activities
                ?.firstOrNull { it.exported && it.enabled && it.permission.isNullOrEmpty() }
                ?.let { return Intent().apply { component = ComponentName(pkg, it.name) } }
        }
        return Intent().apply {
            component = ComponentName(MODULE_PACKAGE, AUTH_ACTIVITY)
            putExtra(EXTRA_RESUME_SIGNAL, true)
        }
    }

    companion object {
        const val MODULE_PACKAGE = "eu.hxreborn.biometricapplock"
        const val AUTH_ACTIVITY = "$MODULE_PACKAGE.BiometricAuthActivity"

        // opaque-window variant, launched when the solid unlock screen is enabled
        const val OPAQUE_AUTH_ACTIVITY = "$MODULE_PACKAGE.OpaqueAuthActivity"

        const val EXTRA_TARGET_PKG = "$MODULE_PACKAGE.TARGET_PKG"
        const val EXTRA_TARGET_USER_ID = "$MODULE_PACKAGE.TARGET_USER_ID"
        const val EXTRA_AUTH_TOKEN = "$MODULE_PACKAGE.AUTH_TOKEN"
        const val EXTRA_TARGET_ACTIVITY = "$MODULE_PACKAGE.TARGET_ACTIVITY"
        const val EXTRA_RESUME_SIGNAL = "$MODULE_PACKAGE.RESUME_SIGNAL"

        // uninstall backstop mode: no token, a good auth writes the grant pref instead of resuming
        const val EXTRA_UNINSTALL_AUTH = "$MODULE_PACKAGE.UNINSTALL_AUTH"

        private const val AUTH_OK = 1
        private const val AUTH_CANCELLED = 2
        private const val AUTH_ERROR = 3
    }
}
