package eu.hxreborn.biometricapplock.util

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators

const val METHOD_BIOMETRIC = 1
const val METHOD_CREDENTIAL = 1 shl 1
const val METHODS_ALL = METHOD_BIOMETRIC or METHOD_CREDENTIAL

// class floor for METHOD_BIOMETRIC rather than a method of its own
const val METHOD_WEAK_OK = 1 shl 2

const val METHODS_DEFAULT = METHODS_ALL or METHOD_WEAK_OK

enum class BiometricClass { STRONG, WEAK }

enum class BiometricChoice { NONE, STRONGEST, ANY }

// BiometricAuthenticator.TYPE_* values
const val MODALITY_FINGERPRINT = 2
const val MODALITY_FACE = 8

// an empty mask would leave no way to authenticate
fun normalizeMethods(methods: Int): Int =
    if (methods and METHODS_ALL == 0) methods or METHODS_ALL else methods

fun methodAuthenticators(
    method: Int,
    weakOk: Boolean = true,
): Int =
    when (method) {
        // Weak is a floor so Class 3 sensors still qualify
        METHOD_BIOMETRIC -> {
            if (weakOk) Authenticators.BIOMETRIC_WEAK else Authenticators.BIOMETRIC_STRONG
        }

        METHOD_CREDENTIAL -> {
            Authenticators.DEVICE_CREDENTIAL
        }

        else -> {
            0
        }
    }

fun biometricChoice(methods: Int): BiometricChoice =
    when {
        methods and METHOD_BIOMETRIC == 0 -> BiometricChoice.NONE
        methods and METHOD_WEAK_OK != 0 -> BiometricChoice.ANY
        else -> BiometricChoice.STRONGEST
    }

fun choiceAuthenticators(choice: BiometricChoice): Int =
    when (choice) {
        BiometricChoice.NONE -> 0
        BiometricChoice.STRONGEST -> Authenticators.BIOMETRIC_STRONG
        BiometricChoice.ANY -> Authenticators.BIOMETRIC_WEAK
    }

fun withBiometricChoice(
    methods: Int,
    choice: BiometricChoice,
): Int =
    when (choice) {
        // dropping biometrics leaves the screen lock as the only way in
        BiometricChoice.NONE -> {
            (methods or METHOD_CREDENTIAL) and (METHOD_BIOMETRIC or METHOD_WEAK_OK).inv()
        }

        BiometricChoice.STRONGEST -> {
            (methods or METHOD_BIOMETRIC) and METHOD_WEAK_OK.inv()
        }

        BiometricChoice.ANY -> {
            methods or METHOD_BIOMETRIC or METHOD_WEAK_OK
        }
    }

// the last remaining method never turns off
fun withCredential(
    methods: Int,
    allowed: Boolean,
): Int =
    if (allowed || methods and METHOD_BIOMETRIC == 0) {
        methods or METHOD_CREDENTIAL
    } else {
        methods and METHOD_CREDENTIAL.inv()
    }

// one unavailable method never blocks the others
fun usableAuthenticators(
    bm: BiometricManager,
    methods: Int,
): Int? {
    val weakOk = methods and METHOD_WEAK_OK != 0
    var authenticators = 0
    for (method in intArrayOf(METHOD_BIOMETRIC, METHOD_CREDENTIAL)) {
        if (methods and method == 0) continue
        val requested = methodAuthenticators(method, weakOk)
        if (bm.canAuthenticate(requested) == BiometricManager.BIOMETRIC_SUCCESS) {
            authenticators = authenticators or requested
        }
    }
    return authenticators.takeIf { it != 0 }
}

// the OS names the sensors each tier reaches, so two tiers reading alike is the honest answer
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
