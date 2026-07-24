package eu.hxreborn.biometricapplock.util

import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators

fun pickAuthenticators(
    bm: BiometricManager,
    allowCredential: Boolean = true,
): Int? {
    // Weak includes Class 3 sensors
    val weak = Authenticators.BIOMETRIC_WEAK
    val cred = Authenticators.DEVICE_CREDENTIAL
    return listOfNotNull(
        if (allowCredential) weak or cred else weak,
        weak,
        cred.takeIf { allowCredential },
    ).firstOrNull { bm.canAuthenticate(it) == BiometricManager.BIOMETRIC_SUCCESS }
}
