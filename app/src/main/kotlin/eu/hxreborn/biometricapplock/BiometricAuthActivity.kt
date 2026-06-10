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
import eu.hxreborn.biometricapplock.util.getUserHandle
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
    private var uninstallAuth = false
    private var replied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPkg = intent.getStringExtra(EXTRA_TARGET_PKG)
        authToken = intent.getStringExtra(EXTRA_AUTH_TOKEN)
        uninstallAuth = intent.getBooleanExtra(EXTRA_UNINSTALL_AUTH, false)

        if (uninstallAuth) {
            Log.i(TAG, "uninstall auth pkg=$targetPkg")
            val label =
                targetPkg?.let(::loadLabel) ?: getString(R.string.biometric_prompt_default_target)
            showPrompt(getString(R.string.biometric_prompt_uninstall_title, label))
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
        showPrompt(getString(R.string.biometric_prompt_unlock_title, label))
    }

    private fun loadLabel(pkg: String): String =
        runCatching {
            packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString()
        }.getOrDefault(pkg)

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
        val signal =
            buildSignalIntent(pkg) ?: run {
                Log.w(TAG, "no startable activity pkg=$pkg, cannot signal resume")
                return false
            }
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
    private fun buildSignalIntent(pkg: String): Intent? {
        packageManager.getLaunchIntentForPackage(pkg)?.let { return it }
        val implicit =
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(pkg)
            }
        if (packageManager.resolveActivity(implicit, 0) != null) return implicit
        return runCatching {
            packageManager
                .getPackageInfo(
                    pkg,
                    PackageManager.GET_ACTIVITIES,
                ).activities
                ?.firstOrNull { it.exported && it.enabled && it.permission.isNullOrEmpty() }
                ?.let { Intent().apply { component = ComponentName(pkg, it.name) } }
        }.getOrNull()
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

        // uninstall backstop mode: no token, a good auth writes the grant pref instead of resuming
        const val EXTRA_UNINSTALL_AUTH = "$MODULE_PACKAGE.UNINSTALL_AUTH"

        private const val AUTH_OK = 1
        private const val AUTH_CANCELLED = 2
        private const val AUTH_ERROR = 3
    }
}
