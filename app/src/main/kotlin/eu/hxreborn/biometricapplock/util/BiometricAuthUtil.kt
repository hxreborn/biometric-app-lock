package eu.hxreborn.biometricapplock.util

import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators

fun pickAuthenticators(
    bm: BiometricManager,
    allowCredential: Boolean = true,
): Int? {
    val strong = Authenticators.BIOMETRIC_STRONG
    val weak = Authenticators.BIOMETRIC_WEAK
    val cred = Authenticators.DEVICE_CREDENTIAL
    return listOfNotNull(
        if (allowCredential) strong or cred else strong,
        weak,
        cred.takeIf { allowCredential },
    ).firstOrNull { bm.canAuthenticate(it) == BiometricManager.BIOMETRIC_SUCCESS }
}
