package eu.hxreborn.biometricapplock.util

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators

enum class BiometricClass { STRONG, WEAK }

// BiometricAuthenticator.TYPE_* values
const val MODALITY_FINGERPRINT = 2
const val MODALITY_FACE = 8

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

fun sensorSettingName(
    context: Context,
    authenticators: Int,
): String? =
    runCatching {
        context
            .getSystemService(BiometricManager::class.java)
            .getStrings(authenticators)
            .settingName
            ?.toString()
    }.getOrNull()?.takeIf { it.isNotBlank() }

// the OS only names a different sensor set for Weak when a Class 2 sensor adds one
fun inferredFaceClass(context: Context): BiometricClass? {
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_FACE)) return null
    val strong = sensorSettingName(context, Authenticators.BIOMETRIC_STRONG) ?: return null
    val weak = sensorSettingName(context, Authenticators.BIOMETRIC_WEAK) ?: return null
    return if (strong == weak) BiometricClass.STRONG else BiometricClass.WEAK
}

@Volatile private var sensorClassCache: Map<Int, BiometricClass>? = null

fun sensorClasses(): Map<Int, BiometricClass> =
    sensorClassCache ?: readSensorClasses().also { sensorClassCache = it }

// per-sensor strength sits behind USE_BIOMETRIC_INTERNAL, only the root shell can reach it
private fun readSensorClasses(): Map<Int, BiometricClass> =
    Regex("updatedStrength:\\s*(\\d+), modality (\\d+)")
        .findAll(RootShell.exec("dumpsys biometric").out.joinToString("\n"))
        .mapNotNull { match ->
            val strength = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val modality = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            when {
                strength <= Authenticators.BIOMETRIC_STRONG -> modality to BiometricClass.STRONG
                strength <= Authenticators.BIOMETRIC_WEAK -> modality to BiometricClass.WEAK
                else -> null
            }
        }.toMap()
