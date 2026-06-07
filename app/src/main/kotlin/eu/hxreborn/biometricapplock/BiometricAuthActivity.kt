package eu.hxreborn.biometricapplock

import android.app.Activity
import android.content.Intent
import android.content.pm.LauncherApps
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.os.UserManager
import android.util.Log
import eu.hxreborn.biometricapplock.prefs.Prefs
import eu.hxreborn.biometricapplock.util.pickAuthenticators

private const val TAG = "BiometricAppLock"

/**
 * Thin activity the hook redirects locked launches to. Runs the system BiometricPrompt and, once it
 * passes, sends a one-time token back so the hook resumes the real launch. Translucent by default.
 * [OpaqueAuthActivity] is the solid variant for OEMs that cancel the see-through prompt.
 */
open class BiometricAuthActivity : Activity() {
    private var targetPkg: String? = null
    private var authToken: String? = null
    private var replied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPkg = intent.getStringExtra(EXTRA_TARGET_PKG)
        authToken = intent.getStringExtra(EXTRA_AUTH_TOKEN)

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
                val userManager = getSystemService(UserManager::class.java)
                val userHandle = userManager.getUserForSerialNumber(userId.toLong())
                val info = launcherApps.getActivityList(pkg, userHandle).firstOrNull()
                info?.label?.toString() ?: pkg
            }.getOrDefault(pkg)
        showPrompt(getString(R.string.biometric_prompt_unlock_title, label))
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy replied=$replied pkg=$targetPkg")
        if (!replied) onResult(AUTH_CANCELLED)
        super.onDestroy()
    }

    private fun showPrompt(title: String) {
        val bm = getSystemService(BiometricManager::class.java)
        val authenticators = pickAuthenticators(bm)
        if (authenticators == null) {
            Log.w(TAG, "no enrolled authenticator, keeping $targetPkg locked")
            onResult(AUTH_CANCELLED)
            return
        }
        val cancellation = CancellationSignal()
        val executor = mainExecutor
        val requireConfirmation =
            App.from(this).prefsRepository.read(Prefs.UNLOCK_REQUIRE_CONFIRMATION)

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
        builder
            .build()
            .authenticate(
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
        Log.d(TAG, "onResult code=$code pkg=$targetPkg")
        if (code == AUTH_OK && launchTarget()) return
        goHome()
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
        // just carries the token back so the hook can resume the real launch
        val signal =
            packageManager.getLaunchIntentForPackage(pkg) ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(pkg)
            }
        signal.putExtra(EXTRA_AUTH_TOKEN, token)
        signal.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        Log.i(TAG, "auth ok, signaling resume pkg=$pkg")
        return runCatching { startActivity(signal) }
            .onFailure { Log.w(TAG, "signal failed pkg=$pkg: ${it.message}") }
            .isSuccess
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

        private const val AUTH_OK = 1
        private const val AUTH_CANCELLED = 2
        private const val AUTH_ERROR = 3
    }
}
