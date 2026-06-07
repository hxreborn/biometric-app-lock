package eu.hxreborn.biometricapplock.ui.component

import android.content.Context
import android.content.pm.LauncherApps
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.graphics.drawable.toBitmap
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.ui.screen.settings.SettingsSectionHeader
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.util.getUserHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_VISIBLE_APPS = 5

@Composable
fun LockedAppsSection(
    scope: Set<String>,
    onNavigateToApps: () -> Unit,
    onNavigateToAppDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSectionHeader(
        title = stringResource(R.string.dashboard_locked_apps_title),
        modifier = modifier,
    )
    SectionCard {
        if (scope.isEmpty()) {
            EmptyContent(onClick = onNavigateToApps)
        } else {
            FilledContent(
                scope = scope,
                onAllClick = onNavigateToApps,
                onAppClick = onNavigateToAppDetail,
            )
        }
    }
}

@Composable
private fun EmptyContent(onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(
                    horizontal = Tokens.PreferenceRowHorizontalPadding,
                    vertical = Tokens.PreferenceRowVerticalPadding,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.PreferenceHorizontalSpacing),
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Tokens.SettingsIconSize),
        )
        Text(
            text = stringResource(R.string.dashboard_locked_apps_empty),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Tokens.SmallIconSize),
        )
    }
}

@Composable
private fun FilledContent(
    scope: Set<String>,
    onAllClick: () -> Unit,
    onAppClick: (String) -> Unit,
) {
    val visible = scope.take(MAX_VISIBLE_APPS)
    val overflow = (scope.size - MAX_VISIBLE_APPS).coerceAtLeast(0)

    Column(
        modifier =
            Modifier.padding(
                horizontal = Tokens.PreferenceRowHorizontalPadding,
                vertical = Tokens.PreferenceRowVerticalPadding,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Tokens.SpacingSm),
        ) {
            visible.forEach { key ->
                AppChip(
                    packageKey = key,
                    onClick = { onAppClick(key) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (overflow > 0) {
                OverflowChip(
                    count = overflow,
                    onClick = onAllClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AppChip(
    packageKey: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val packageName = remember(packageKey) { packageKey.substringBeforeLast(':') }
    val userId = remember(packageKey) { packageKey.substringAfterLast(':').toIntOrNull() ?: 0 }

    val appName by produceState(initialValue = packageName.substringAfterLast('.'), key1 = packageKey) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                    val userHandle = getUserHandle(userId)
                    val info = launcherApps.getActivityList(packageName, userHandle).firstOrNull()
                    info?.label?.toString() ?: packageName.substringAfterLast('.')
                }.getOrDefault(packageName.substringAfterLast('.'))
            }
    }
    val icon by produceState<ImageBitmap?>(initialValue = null, key1 = packageKey) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                    val userHandle = getUserHandle(userId)
                    val info = launcherApps.getActivityList(packageName, userHandle).firstOrNull()
                    info?.getIcon(0)?.toBitmap()?.asImageBitmap()
                }.getOrNull()
            }
    }

    Column(
        modifier =
            modifier
                .clickable(onClick = onClick)
                .padding(vertical = Tokens.SpacingXs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(Tokens.AppIconSize),
            shape = RoundedCornerShape(Tokens.AppIconCornerRadius),
            color = if (icon != null) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            val bitmap = icon
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(Tokens.SpacingXs))
        Text(
            text = appName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun OverflowChip(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clickable(onClick = onClick)
                .padding(vertical = Tokens.SpacingXs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(Tokens.AppIconSize),
            shape = RoundedCornerShape(Tokens.AppIconCornerRadius),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "+$count",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(Tokens.SpacingXs))
        Text(
            text = stringResource(R.string.dashboard_locked_apps_more),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
