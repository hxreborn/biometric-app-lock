@file:Suppress("AssignedValueIsNeverRead")
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package eu.hxreborn.biometricapplock.ui.screen

import android.content.Context
import android.content.pm.LauncherApps
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.hxreborn.biometricapplock.App
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.prefs.AppOverrides
import eu.hxreborn.biometricapplock.ui.component.ExpandedTitle
import eu.hxreborn.biometricapplock.ui.component.LockSwitch
import eu.hxreborn.biometricapplock.ui.screen.settings.PreferenceRow
import eu.hxreborn.biometricapplock.ui.screen.settings.SettingsSectionHeader
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.ui.util.openAppInfo
import eu.hxreborn.biometricapplock.util.getUserHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppDetailScreen(
    packageKey: String,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    onNavigateToAllowedActivities: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = App.from(context)

    val packageName = remember(packageKey) { packageKey.substringBeforeLast(':') }
    val userId = remember(packageKey) { packageKey.substringAfterLast(':').toIntOrNull() ?: 0 }

    val appName by produceState(initialValue = packageName, key1 = packageKey) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                    val userHandle = getUserHandle(userId)
                    val info = launcherApps.getActivityList(packageName, userHandle).firstOrNull()
                    info?.label?.toString() ?: packageName
                }.getOrDefault(packageName)
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

    val versionName by produceState<String?>(initialValue = null, key1 = packageKey) {
        value =
            withContext(Dispatchers.IO) {
                runCatching { context.packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
            }
    }

    val overrides by app.appOverridesRepository
        .observe(packageKey)
        .collectAsStateWithLifecycle(initialValue = AppOverrides(null, null))

    val hasOverrides =
        overrides.relockDelaySeconds != null ||
            overrides.blockScreenshots != null
    val disabledModifier = if (hasOverrides) Modifier else Modifier.alpha(Tokens.DISABLED_ALPHA)

    var showRelockDialog by remember { mutableStateOf(false) }

    if (showRelockDialog) {
        RelockDelayDialog(
            currentSeconds = overrides.relockDelaySeconds ?: 0,
            onSelect = { seconds ->
                app.appOverridesRepository.setRelockDelaySeconds(packageKey, seconds)
                showRelockDialog = false
            },
            onDismiss = { showRelockDialog = false },
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                navigationIcon = {
                    Surface(
                        modifier = Modifier.padding(start = Tokens.SpacingSm).size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.about_back_cd),
                            )
                        }
                    }
                },
                title = { ExpandedTitle(stringResource(R.string.app_detail_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding() + Tokens.SpacingLg,
                ),
        ) {
            item {
                AppHeaderCard(
                    appName = appName,
                    packageName = if (userId == 0) packageName else "$packageName (${stringResource(R.string.apps_user_id, userId)})",
                    versionName = versionName,
                    icon = icon,
                )
            }

            item { SettingsSectionHeader(title = stringResource(R.string.settings_lock)) }

            item {
                PreferenceRow(
                    icon = Icons.Outlined.Tune,
                    title = stringResource(R.string.app_detail_override_title),
                    summary = null,
                    onClick = {
                        if (hasOverrides) {
                            app.appOverridesRepository.reset(packageKey)
                        } else {
                            app.appOverridesRepository.setRelockDelaySeconds(packageKey, 0)
                        }
                    },
                    trailing = {
                        LockSwitch(
                            checked = hasOverrides,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    app.appOverridesRepository.setRelockDelaySeconds(packageKey, 0)
                                } else {
                                    app.appOverridesRepository.reset(packageKey)
                                }
                            },
                        )
                    },
                )
            }

            item {
                PreferenceRow(
                    icon = Icons.Outlined.Timer,
                    title = stringResource(R.string.app_detail_relock_delay_title),
                    summary = relockDelaySummary(overrides.relockDelaySeconds ?: 0),
                    onClick = if (hasOverrides) ({ showRelockDialog = true }) else null,
                    modifier = disabledModifier,
                )
            }

            item {
                PreferenceRow(
                    icon = Icons.Outlined.Screenshot,
                    title = stringResource(R.string.app_detail_block_screenshots_title),
                    summary = stringResource(R.string.app_detail_block_screenshots_summary),
                    onClick =
                        if (hasOverrides) {
                            {
                                app.appOverridesRepository.setBlockScreenshots(
                                    packageKey,
                                    overrides.blockScreenshots != true,
                                )
                            }
                        } else {
                            null
                        },
                    trailing = {
                        LockSwitch(
                            checked = overrides.blockScreenshots == true,
                            onCheckedChange = null,
                            enabled = hasOverrides,
                        )
                    },
                    modifier = disabledModifier,
                )
            }

            item {
                PreferenceRow(
                    icon = Icons.Outlined.Apps,
                    title = stringResource(R.string.app_detail_allowed_section),
                    summary =
                        if (overrides.allowedActivities.isEmpty()) {
                            stringResource(R.string.app_detail_allowed_row_empty)
                        } else {
                            stringResource(
                                R.string.app_detail_allowed_count,
                                overrides.allowedActivities.size,
                            )
                        },
                    onClick = { onNavigateToAllowedActivities(packageKey) },
                )
            }

            item { SettingsSectionHeader(title = stringResource(R.string.app_detail_section_advanced)) }

            item {
                PreferenceRow(
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    title = stringResource(R.string.app_detail_open_app_info_title),
                    summary = stringResource(R.string.app_detail_open_app_info_summary),
                    onClick = { openAppInfo(context, packageName) },
                )
            }
        }
    }
}

@Composable
private fun AppHeaderCard(
    appName: String,
    packageName: String,
    versionName: String?,
    icon: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Tokens.DetailHeaderCardPadding,
                    vertical = Tokens.DetailHeaderCardPadding,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetailAppIcon(icon = icon, modifier = Modifier.size(Tokens.DetailHeaderIconSize))
        Spacer(Modifier.width(Tokens.DetailHeaderIconTextSpacing))
        Column {
            Text(appName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = packageName,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (versionName != null) {
                Text(
                    text = versionName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DetailAppIcon(
    icon: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Tokens.AppIconCornerRadius),
        color = if (icon != null) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(modifier = Modifier.size(Tokens.LoadingIndicatorSize))
            }
        }
    }
}
