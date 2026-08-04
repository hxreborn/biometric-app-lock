package eu.hxreborn.biometricapplock.ui.component

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.ui.viewmodel.ModuleStatus
import eu.hxreborn.biometricapplock.ui.viewmodel.ServiceLoadEvent

private data class StatusVisual(
    val icon: ImageVector,
    val container: Color,
    val content: Color,
)

@Composable
fun StatusCard(
    status: ModuleStatus,
    lockedAppCount: Int,
    serviceLoadEvent: ServiceLoadEvent?,
    modifier: Modifier = Modifier,
) {
    val visual = statusVisual(status)
    val title =
        stringResource(
            when (status) {
                ModuleStatus.Enabled -> R.string.status_active
                ModuleStatus.RebootRequired -> R.string.status_reboot_required
                ModuleStatus.NotEnabled -> R.string.status_inactive
            },
        )
    val summary =
        when (status) {
            ModuleStatus.NotEnabled -> {
                stringResource(R.string.status_inactive_action)
            }

            ModuleStatus.RebootRequired -> {
                stringResource(R.string.status_reboot_required_reason)
            }

            ModuleStatus.Enabled -> {
                if (lockedAppCount == 0) {
                    stringResource(R.string.status_locked_empty)
                } else {
                    pluralStringResource(R.plurals.status_locked_count, lockedAppCount, lockedAppCount)
                }
            }
        }
    val loadLine: String? =
        if (status == ModuleStatus.Enabled && serviceLoadEvent != null) {
            val relative =
                DateUtils
                    .getRelativeTimeSpanString(
                        serviceLoadEvent.epochMs,
                        System.currentTimeMillis(),
                        DateUtils.SECOND_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE,
                    ).toString()
            when (serviceLoadEvent) {
                is ServiceLoadEvent.Boot -> stringResource(R.string.status_loaded_at_boot, relative)
                is ServiceLoadEvent.HotReload -> stringResource(R.string.status_loaded_via_hot_reload, relative)
            }
        } else {
            null
        }

    ElevatedCard(
        modifier = modifier.fillMaxWidth().padding(horizontal = Tokens.SectionHorizontalMargin, vertical = Tokens.SectionItemSpacing),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = visual.container,
                contentColor = visual.content,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Tokens.SpacingLg),
            horizontalArrangement = Arrangement.spacedBy(Tokens.PreferenceRowIconTextSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = visual.icon,
                contentDescription = null,
                modifier = Modifier.size(Tokens.StatusIconSize),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Tokens.SpacingXs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (loadLine != null) {
                    Text(
                        text = loadLine,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun statusVisual(status: ModuleStatus): StatusVisual {
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        ModuleStatus.Enabled -> {
            StatusVisual(
                icon = Icons.Filled.CheckCircle,
                container = scheme.primary,
                content = scheme.onPrimary,
            )
        }

        ModuleStatus.RebootRequired -> {
            StatusVisual(
                icon = Icons.Filled.RestartAlt,
                container = scheme.tertiaryContainer,
                content = scheme.onTertiaryContainer,
            )
        }

        ModuleStatus.NotEnabled -> {
            StatusVisual(
                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                container = scheme.surfaceContainerHighest,
                content = scheme.onSurfaceVariant,
            )
        }
    }
}
