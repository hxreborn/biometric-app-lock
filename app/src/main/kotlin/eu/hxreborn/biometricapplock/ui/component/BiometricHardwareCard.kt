package eu.hxreborn.biometricapplock.ui.component

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators
import android.os.SystemClock
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.ui.screen.settings.PreferenceRow
import eu.hxreborn.biometricapplock.ui.screen.settings.SettingsSectionHeader
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class ChipKind { Enrolled, NotEnrolled, NoSensor, Unavailable, UpdateRequired }

private enum class ClassLabel { STRONG, WEAK }

private data class ModalityState(
    val chip: ChipKind,
    val enrolledCount: Int? = null,
)

private data class BiometricState(
    val fingerprint: ModalityState,
    val face: ModalityState,
    val strongestClass: ClassLabel? = null,
    val lastAuthAgo: String? = null,
)

private val BiometricStateUnknown =
    BiometricState(
        fingerprint = ModalityState(ChipKind.NoSensor),
        face = ModalityState(ChipKind.NoSensor),
    )

@Composable
fun BiometricHardwareSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by produceState(initialValue = BiometricStateUnknown, key1 = Unit) {
        value = withContext(Dispatchers.IO) { readBiometricState(context) }
    }

    SettingsSectionHeader(
        title = stringResource(R.string.dashboard_biometric_title),
        modifier = modifier,
    )

    val rows =
        buildList<@Composable (SectionPosition) -> Unit> {
            add { position ->
                ModalityRow(
                    Icons.Outlined.Fingerprint,
                    stringResource(R.string.dashboard_biometric_fingerprint),
                    state.fingerprint,
                    position,
                )
            }
            add { position ->
                ModalityRow(Icons.Outlined.Face, stringResource(R.string.dashboard_biometric_face), state.face, position)
            }
            state.strongestClass?.let { strongest ->
                add { position ->
                    PreferenceRow(
                        icon = Icons.Outlined.Security,
                        title = stringResource(R.string.dashboard_biometric_class_label),
                        summary = classLabelText(strongest),
                        position = position,
                    )
                }
            }
            state.lastAuthAgo?.let { ago ->
                add { position ->
                    PreferenceRow(
                        icon = Icons.Outlined.Schedule,
                        title = stringResource(R.string.dashboard_biometric_last_auth),
                        summary = ago,
                        position = position,
                    )
                }
            }
        }

    rows.forEachIndexed { index, row -> row(positionFor(index, rows.size)) }
}

private fun positionFor(
    index: Int,
    count: Int,
): SectionPosition =
    when {
        count == 1 -> SectionPosition.Single
        index == 0 -> SectionPosition.Top
        index == count - 1 -> SectionPosition.Bottom
        else -> SectionPosition.Middle
    }

@Composable
private fun ModalityRow(
    icon: ImageVector,
    name: String,
    state: ModalityState,
    position: SectionPosition,
) {
    PreferenceRow(
        icon = icon,
        title = name,
        summary = null,
        position = position,
        trailing = { ChipBadge(kind = state.chip, count = state.enrolledCount) },
    )
}

@Composable
private fun classLabelText(classLabel: ClassLabel): String =
    stringResource(
        when (classLabel) {
            ClassLabel.STRONG -> R.string.dashboard_biometric_class_strong
            ClassLabel.WEAK -> R.string.dashboard_biometric_class_weak
        },
    )

@Composable
private fun ChipBadge(
    kind: ChipKind,
    count: Int?,
) {
    val (container, content) =
        when (kind) {
            ChipKind.Enrolled -> {
                MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
            }

            ChipKind.UpdateRequired -> {
                MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
            }

            ChipKind.NotEnrolled, ChipKind.NoSensor, ChipKind.Unavailable -> {
                MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
            }
        }
    val label =
        when (kind) {
            ChipKind.Enrolled -> {
                if (count != null && count > 0) {
                    pluralStringResource(R.plurals.dashboard_biometric_chip_count, count, count)
                } else {
                    stringResource(R.string.dashboard_biometric_chip_enrolled)
                }
            }

            ChipKind.NotEnrolled -> {
                stringResource(R.string.dashboard_biometric_chip_not_enrolled)
            }

            ChipKind.NoSensor -> {
                stringResource(R.string.dashboard_biometric_chip_no_sensor)
            }

            ChipKind.Unavailable -> {
                stringResource(R.string.dashboard_biometric_chip_unavailable)
            }

            ChipKind.UpdateRequired -> {
                stringResource(R.string.dashboard_biometric_chip_update)
            }
        }
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(Tokens.SmallCornerRadius),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = Tokens.SpacingSm, vertical = Tokens.SpacingXs),
        )
    }
}

@SuppressLint("MissingPermission")
private fun readBiometricState(context: Context): BiometricState {
    val pm = context.packageManager
    val bm = context.getSystemService(BiometricManager::class.java)

    val hasFingerprint = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
    val hasFace = pm.hasSystemFeature(PackageManager.FEATURE_FACE)

    val strongStatus =
        runCatching { bm?.canAuthenticate(Authenticators.BIOMETRIC_STRONG) }
            .getOrNull() ?: BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
    val weakStatus =
        runCatching { bm?.canAuthenticate(Authenticators.BIOMETRIC_WEAK) }
            .getOrNull() ?: BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

    val strongestClass: ClassLabel? =
        when {
            strongStatus == BiometricManager.BIOMETRIC_SUCCESS -> ClassLabel.STRONG
            weakStatus == BiometricManager.BIOMETRIC_SUCCESS -> ClassLabel.WEAK
            else -> null
        }

    val fpEnrolled = readFingerprintCount(context)
    val lastAuthAgo = readLastAuthAgo(bm)

    return BiometricState(
        fingerprint =
            modalityState(
                hasHardware = hasFingerprint,
                weakStatus = weakStatus,
                strongStatus = strongStatus,
                explicitCount = fpEnrolled,
            ),
        face =
            modalityState(
                hasHardware = hasFace,
                weakStatus = weakStatus,
                strongStatus = strongStatus,
                explicitCount = null,
            ),
        strongestClass = strongestClass,
        lastAuthAgo = lastAuthAgo,
    )
}

private fun modalityState(
    hasHardware: Boolean,
    weakStatus: Int,
    strongStatus: Int,
    explicitCount: Int?,
): ModalityState {
    if (!hasHardware) {
        return ModalityState(ChipKind.NoSensor)
    }
    val chip =
        when {
            explicitCount != null && explicitCount > 0 -> ChipKind.Enrolled

            explicitCount != null && explicitCount == 0 -> ChipKind.NotEnrolled

            weakStatus == BiometricManager.BIOMETRIC_SUCCESS -> ChipKind.Enrolled

            weakStatus == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ||
                strongStatus == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> ChipKind.Unavailable

            weakStatus == BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ||
                strongStatus == BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> ChipKind.UpdateRequired

            else -> ChipKind.NotEnrolled
        }
    return ModalityState(chip = chip, enrolledCount = explicitCount)
}

@SuppressLint("MissingPermission")
private fun readFingerprintCount(context: Context): Int? {
    val fpm = context.getSystemService("fingerprint") ?: return null
    return try {
        val isHwDetected = fpm.javaClass.getMethod("isHardwareDetected").invoke(fpm) as? Boolean
        if (isHwDetected != true) return null
        val list = fpm.javaClass.getMethod("getEnrolledFingerprints").invoke(fpm) as? List<*>
        if (list != null) return list.size
        val hasEnrolled = fpm.javaClass.getMethod("hasEnrolledFingerprints").invoke(fpm) as? Boolean
        if (hasEnrolled == true) 1 else 0
    } catch (_: Throwable) {
        null
    }
}

private fun readLastAuthAgo(bm: BiometricManager?): String? {
    if (bm == null || android.os.Build.VERSION.SDK_INT < 34) return null
    return try {
        val method = bm.javaClass.getMethod("getLastAuthenticationTime", Int::class.javaPrimitiveType)
        val elapsedMs = method.invoke(bm, Authenticators.BIOMETRIC_STRONG) as Long
        if (elapsedMs <= 0) return null
        val agoMs = SystemClock.elapsedRealtime() - elapsedMs
        formatDuration(agoMs)
    } catch (_: Throwable) {
        null
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days > 0 -> "${days}d ${hours % 24}h ago"
        hours > 0 -> "${hours}h ${minutes % 60}m ago"
        minutes > 0 -> "${minutes}m ago"
        else -> "just now"
    }
}
