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
    context: Context,
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
        // Note: if canAuthenticate rejects a mask we do NOT add DEVICE_CREDENTIAL.
        // The user's "require fingerprint or face only" setting must be respected.
        // If nothing is usable, authenticators stays 0, we return null, and the app stays locked.
    }
    return authenticators.takeIf { it != 0 }
}

// the OS names the sensors each tier reaches, so two tiers reading alike is the honest answer
fun sensorSettingName(
    context: Context,
    authenticators: Int,
): String? =
    runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            context
                .getSystemService(BiometricManager::class.java)
                .getStrings(authenticators)
                .settingName
                ?.toString()
        } else {
            null
        }
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

// per-sensor strength sits behind USE_BIOMETRIC_INTERNAL, only the root shell can reach it.
// Handles two dump formats:
//   AOSP/A13+: "ID(n), oemStrength: S, updatedStrength: S, modality M"
//   Samsung/A11 compact: "{M, S}"
private fun readSensorClasses(): Map<Int, BiometricClass> {
    val dump = RootShell.exec("dumpsys biometric").out.joinToString("\n")

    // AOSP format: updatedStrength: <strength>, modality <modality>
    val aosp =
        Regex("""updatedStrength:\s*(\d+), modality (\d+)""")
            .findAll(dump)
            .mapNotNull { match ->
                val strength = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val modality = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                classifyStrength(strength)?.let { modality to it }
            }

    // Samsung compact format: {<modality>, <strength>}
    val samsung =
        if (android.os.Build.MANUFACTURER
                .equals("samsung", ignoreCase = true)
        ) {
            Regex("""\{(\d+),\s*(\d+)\}""")
                .findAll(dump)
                .mapNotNull { match ->
                    val modality = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                    val strength = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                    classifyStrength(strength)?.let { modality to it }
                }
        } else {
            emptySequence()
        }

    return (samsung + aosp).toMap()
}

private fun classifyStrength(strength: Int): BiometricClass? =
    when {
        strength <= Authenticators.BIOMETRIC_STRONG -> BiometricClass.STRONG
        strength <= Authenticators.BIOMETRIC_WEAK -> BiometricClass.WEAK
        else -> null
    }

// Checks if modality 8 (face) appears in the biometric dump at ANY strength level,
// including CONVENIENCE class sensors (e.g. Samsung face at strength 4095).
// This is the root-based fallback for devices that don't expose FEATURE_FACE.
@Volatile private var faceSensorDumpCache: Boolean? = null

fun hasFaceSensorInDump(): Boolean =
    faceSensorDumpCache ?: checkFaceSensorInDump().also { faceSensorDumpCache = it }

private fun checkFaceSensorInDump(): Boolean {
    val dump = RootShell.exec("dumpsys biometric").out.joinToString("\n")
    // AOSP format: modality 8
    if (Regex("""modality\s+8\b""").containsMatchIn(dump)) return true
    // Samsung compact format: {8, *}
    if (Regex("""\{8,\s*\d+\}""").containsMatchIn(dump)) return true
    return false
}

// MIUI/HyperOS devices keep face unlock in a separate service (miui.face.FaceService)
// that is completely outside the Android BiometricManager stack.
// Returns: null = no MIUI face hardware on this device, 0 = not enrolled, 1 = enrolled
// Re-reads on every call so enrollment changes are always reflected immediately.
fun miuiFaceEnrollmentCount(context: Context): Int? {
    // Covers Xiaomi and POCO (Xiaomi sub-brand)
    if (!android.os.Build.MANUFACTURER
            .equals("xiaomi", ignoreCase = true) &&
        !android.os.Build.MANUFACTURER
            .equals("poco", ignoreCase = true)
    ) {
        return null
    }
    return try {
        // face_unlock_valid_feature = 1 means a face is enrolled in HyperOS / MIUI.
        // Non-Xiaomi devices simply won't have this key (returns the default -1).
        val settingValue =
            android.provider.Settings.Secure.getInt(
                context.contentResolver,
                "face_unlock_valid_feature",
                -1,
            )
        when (settingValue) {
            1 -> 1

            // face enrolled
            0 -> 0

            // service present but nothing enrolled
            else -> 0 // key absent — treat as not enrolled
        }
    } catch (e: Exception) {
        0
    }
}

// hasMiuiFace is true only when at least one face is enrolled (count >= 1).
// count == 0 means the service exists but nothing enrolled — face hardware card must not show face.
fun hasMiuiFace(context: Context): Boolean = (miuiFaceEnrollmentCount(context) ?: -1) >= 1

// Combined face hardware detection: standard API + root biometric dump + MIUI service.
// This covers devices like Samsung (CONVENIENCE-class face) and Xiaomi MIUI (separate service)
// that don't expose android.hardware.biometrics.face via PackageManager.
fun hasFaceHardware(context: Context): Boolean =
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_FACE) ||
        hasFaceSensorInDump() ||
        hasMiuiFace(context)

// Samsung convenience-class face doesn't expose its enrollment status to BiometricManager.canAuthenticate(WEAK).
// We check the face_screen_lock secure setting which is 1 when face unlock is set up.
fun samsungFaceEnrollmentCount(context: Context): Int? {
    if (!android.os.Build.MANUFACTURER
            .equals("samsung", ignoreCase = true)
    ) {
        return null
    }
    val faceScreenLock =
        android.provider.Settings.Secure.getInt(
            context.contentResolver,
            "face_screen_lock",
            -1,
        )
    return when (faceScreenLock) {
        1 -> 1
        0 -> 0
        else -> null
    }
}
