
package eu.hxreborn.biometricapplock.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.ui.viewmodel.ModuleStatus

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusCard(
    status: ModuleStatus,
    lockedAppCount: Int,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val container =
        when (status) {
            ModuleStatus.Enabled -> scheme.primaryContainer
            ModuleStatus.RebootRequired -> scheme.tertiaryContainer
            ModuleStatus.NotEnabled -> scheme.surfaceContainerHighest
        }
    val contentColor =
        when (status) {
            ModuleStatus.Enabled -> scheme.onPrimaryContainer
            ModuleStatus.RebootRequired -> scheme.onTertiaryContainer
            ModuleStatus.NotEnabled -> scheme.onSurfaceVariant
        }
    val leadingIcon: ImageVector =
        when (status) {
            ModuleStatus.Enabled -> Icons.Filled.CheckCircle
            ModuleStatus.RebootRequired -> Icons.Filled.RestartAlt
            ModuleStatus.NotEnabled -> Icons.AutoMirrored.Outlined.HelpOutline
        }
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

    val cardColors =
        CardDefaults.cardColors(
            containerColor = container,
            contentColor = contentColor,
        )
    val itemColors =
        ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = contentColor,
            supportingColor = contentColor,
            leadingIconColor = contentColor,
            trailingIconColor = contentColor,
        )
    val cardModifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.SpacingSm, vertical = Tokens.CardVerticalSpacing)
    val leading: @Composable () -> Unit = {
        Icon(imageVector = leadingIcon, contentDescription = null, modifier = Modifier.size(32.dp))
    }

    Card(modifier = cardModifier, colors = cardColors) {
        ListItem(
            leadingContent = leading,
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(summary) },
            colors = itemColors,
        )
    }
}
