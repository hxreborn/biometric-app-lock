package eu.hxreborn.biometricapplock

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.util.Log

private const val TAG = "BiometricAppLock"

class BiometricAuthActivity : Activity() {
    private var targetPkg: String? = null
    private var targetCls: String? = null
    private var authToken: String? = null
    private var replied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPkg = intent.getStringExtra(EXTRA_TARGET_PKG)
        targetCls = intent.getStringExtra(EXTRA_TARGET_CLS)
        authToken = intent.getStringExtra(EXTRA_AUTH_TOKEN)

        if (targetPkg.isNullOrEmpty() || authToken.isNullOrEmpty()) {
            Log.w(TAG, "missing extras, finishing")
            finish()
            return
        }
        Log.i(TAG, "gating $targetPkg")
        showPrompt("Unlock $targetPkg")
    }

    override fun onDestroy() {
        if (!replied) onResult(AUTH_CANCELLED)
        super.onDestroy()
    }

    private fun showPrompt(title: String) {
        val cancellation = CancellationSignal()
        val executor = mainExecutor

        BiometricPrompt
            .Builder(this)
            .setTitle(title)
            .setNegativeButton(getString(android.R.string.cancel), executor) { _, _ ->
                cancellation.cancel()
                onResult(AUTH_CANCELLED)
            }.build()
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
                    ) = onResult(AUTH_ERROR)
                },
            )
    }

    private fun onResult(code: Int) {
        if (replied) return
        replied = true
        Log.d(TAG, "onResult code=$code pkg=$targetPkg")
        if (code == AUTH_OK) {
            launchTarget()
        } else {
            goHome()
        }
        finish()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    private fun launchTarget() {
        val pkg = targetPkg ?: return
        val nonce = authToken ?: return

        val target =
            if (targetCls != null) {
                Intent().apply { component = ComponentName(pkg, targetCls!!) }
            } else {
                packageManager.getLaunchIntentForPackage(pkg)
            } ?: return

        target.putExtra(EXTRA_AUTH_TOKEN, nonce)
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        Log.i(TAG, "auth success, launching $pkg")
        startActivity(target)
    }

    companion object {
        const val EXTRA_TARGET_PKG = "eu.hxreborn.biometricapplock.TARGET_PKG"
        const val EXTRA_TARGET_CLS = "eu.hxreborn.biometricapplock.TARGET_CLS"
        const val EXTRA_AUTH_TOKEN = "eu.hxreborn.biometricapplock.AUTH_TOKEN"

        private const val AUTH_OK = 1
        private const val AUTH_CANCELLED = 2
        private const val AUTH_ERROR = 3

        const val MODULE_PACKAGE = "eu.hxreborn.biometricapplock"
        const val AUTH_ACTIVITY = "$MODULE_PACKAGE.BiometricAuthActivity"
    }
}
