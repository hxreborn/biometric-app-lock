@file:Suppress("AssignedValueIsNeverRead")
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package eu.hxreborn.biometricapplock.ui.screen.settings

import android.hardware.biometrics.BiometricManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AppBlocking
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RoundedCorner
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.hxreborn.biometricapplock.App
import eu.hxreborn.biometricapplock.BuildConfig
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.prefs.AppPrefs
import eu.hxreborn.biometricapplock.prefs.Prefs
import eu.hxreborn.biometricapplock.prefs.ThemeMode
import eu.hxreborn.biometricapplock.ui.component.ExpandedTitle
import eu.hxreborn.biometricapplock.ui.component.FeatureSheetItem
import eu.hxreborn.biometricapplock.ui.component.LockSwitch
import eu.hxreborn.biometricapplock.ui.component.LogActionsSheet
import eu.hxreborn.biometricapplock.ui.component.SectionPosition
import eu.hxreborn.biometricapplock.ui.component.WhatsNewSheet
import eu.hxreborn.biometricapplock.ui.component.changeTypeLabelRes
import eu.hxreborn.biometricapplock.ui.screen.BiometricChoiceDialog
import eu.hxreborn.biometricapplock.ui.screen.RelockDelayDialog
import eu.hxreborn.biometricapplock.ui.screen.biometricChoiceLabel
import eu.hxreborn.biometricapplock.ui.screen.relockDelaySummary
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.ui.util.LauncherIconHelper
import eu.hxreborn.biometricapplock.ui.viewmodel.FrameworkInfo
import eu.hxreborn.biometricapplock.updates.ChangeType
import eu.hxreborn.biometricapplock.updates.UpdateSheetState
import eu.hxreborn.biometricapplock.util.DiagnosticsExporter
import eu.hxreborn.biometricapplock.util.METHOD_BIOMETRIC
import eu.hxreborn.biometricapplock.util.METHOD_CREDENTIAL
import eu.hxreborn.biometricapplock.util.biometricChoice
import eu.hxreborn.biometricapplock.util.methodAuthenticators
import eu.hxreborn.biometricapplock.util.normalizeMethods
import eu.hxreborn.biometricapplock.util.withBiometricChoice
import eu.hxreborn.biometricapplock.util.withCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen(
    framework: FrameworkInfo?,
    rootGranted: Boolean?,
    onNavigateToAbout: () -> Unit,
    onNavigateToAppManagement: () -> Unit,
    onShowUpdateSheet: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val app = App.from(context)
    val coroutineScope = rememberCoroutineScope()
    val prefs by app.prefsRepository.state.collectAsStateWithLifecycle(initialValue = AppPrefs.Defaults)
    val cachedAvailable by app.updateRepository.cachedAvailable.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showRelockDialog by remember { mutableStateOf(false) }
    var showWhatsNew by remember { mutableStateOf(false) }
    var showLogActions by remember { mutableStateOf(false) }
    var pendingSaveFile by remember { mutableStateOf<File?>(null) }
    var launcherIconHidden by remember { mutableStateOf(!LauncherIconHelper.isLauncherIconVisible(context)) }
    var showHideLauncherConfirm by remember { mutableStateOf(false) }

    val bm = remember { context.getSystemService(BiometricManager::class.java) }
    val unlockMethods = normalizeMethods(prefs.unlockMethods)
    val credentialAvailable = remember { bm.canAuthenticate(methodAuthenticators(METHOD_CREDENTIAL)) == BiometricManager.BIOMETRIC_SUCCESS }
    val unlockCredentialToggleable = credentialAvailable && unlockMethods and METHOD_BIOMETRIC != 0
    val selfLockMethods = normalizeMethods(prefs.selfLockMethods)
    val selfLockCredentialToggleable = credentialAvailable && selfLockMethods and METHOD_BIOMETRIC != 0
    var showBiometricDialog by remember { mutableStateOf(false) }
    var showSelfLockDialog by remember { mutableStateOf(false) }

    if (showBiometricDialog) {
        BiometricChoiceDialog(
            current = biometricChoice(unlockMethods),
            allowDefault = false,
            onSelect = { choice ->
                choice?.let { app.prefsRepository.save(Prefs.UNLOCK_METHODS, withBiometricChoice(unlockMethods, it)) }
                showBiometricDialog = false
            },
            onDismiss = { showBiometricDialog = false },
        )
    }

    if (showSelfLockDialog) {
        BiometricChoiceDialog(
            current = biometricChoice(selfLockMethods),
            allowDefault = false,
            onSelect = { choice ->
                choice?.let { app.prefsRepository.save(Prefs.SELF_LOCK_METHODS, withBiometricChoice(selfLockMethods, it)) }
                showSelfLockDialog = false
            },
            onDismiss = { showSelfLockDialog = false },
        )
    }

    val saveLogsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            val file = pendingSaveFile
            pendingSaveFile = null
            if (uri == null || file == null) return@rememberLauncherForActivityResult
            coroutineScope.launch {
                val saved = DiagnosticsExporter.saveTo(context, file, uri)
                Toast
                    .makeText(
                        context,
                        if (saved) R.string.logs_saved else R.string.logs_save_failed,
                        Toast.LENGTH_LONG,
                    ).show()
            }
        }

    if (showThemeDialog) {
        ThemeDialog(
            current = prefs.themeMode,
            onSelect = { mode ->
                app.prefsRepository.save(Prefs.DARK_THEME_CONFIG, mode.prefValue)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showRelockDialog) {
        RelockDelayDialog(
            currentSeconds = prefs.relockDelaySeconds,
            onSelect = { seconds ->
                app.prefsRepository.save(Prefs.RELOCK_DELAY_SECONDS, seconds)
                showRelockDialog = false
            },
            onDismiss = { showRelockDialog = false },
        )
    }

    if (showHideLauncherConfirm) {
        AlertDialog(
            onDismissRequest = { showHideLauncherConfirm = false },
            title = { Text(stringResource(R.string.settings_hide_launcher_confirm_title)) },
            text = { Text(stringResource(R.string.settings_hide_launcher_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        LauncherIconHelper.setLauncherIconVisible(context, false)
                        launcherIconHidden = true
                        showHideLauncherConfirm = false
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showHideLauncherConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (showWhatsNew) {
        val uriHandler = LocalUriHandler.current
        val whatsNewItems =
            app.updateRepository.bundledChangelog.filter { it.version == BuildConfig.VERSION_NAME }.map { entry ->
                val type = ChangeType.from(entry.type, entry.breaking)
                FeatureSheetItem(
                    label = stringResource(changeTypeLabelRes(type)),
                    scope = entry.scope?.takeIf { it.isNotBlank() },
                    changeType = type,
                    title = entry.title,
                    body = entry.description,
                    onClick = entry.url?.let { url -> { uriHandler.openUri(url) } },
                )
            }

        WhatsNewSheet(
            state = UpdateSheetState.WhatsNew,
            items = whatsNewItems,
            versionLabel = BuildConfig.VERSION_NAME,
            onDismiss = { showWhatsNew = false },
        )
    }

    if (showLogActions) {
        val frameworkLabel = framework?.let { "${it.name} ${it.version}" }
        val collect: suspend ((File) -> Unit) -> Boolean = { onCollected ->
            try {
                onCollected(DiagnosticsExporter.export(context, frameworkLabel))
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Toast.makeText(context, R.string.diagnostics_no_logs, Toast.LENGTH_LONG).show()
                false
            }
        }
        LogActionsSheet(
            onSave = {
                collect { file ->
                    pendingSaveFile = file
                    saveLogsLauncher.launch(file.name)
                }
            },
            onSend = {
                collect { file -> DiagnosticsExporter.share(context, file) }
            },
            onDismiss = { showLogActions = false },
        )
    }

    val showDotIndicator = cachedAvailable?.latestVersion?.let { it != prefs.lastDismissedAvailableVersion } == true

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { ExpandedTitle(stringResource(R.string.tab_settings)) },
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
            item { SettingsSectionHeader(title = stringResource(R.string.settings_lock)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Timer,
                    title = stringResource(R.string.settings_relock_delay_title),
                    summary = stringResource(R.string.settings_relock_delay_summary),
                    position = SectionPosition.Top,
                    onClick = { showRelockDialog = true },
                    trailing = { ValueText(relockDelaySummary(prefs.relockDelaySeconds)) },
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.ScreenLockPortrait,
                    title = stringResource(R.string.settings_relock_screen_off_title),
                    summary = stringResource(R.string.settings_relock_screen_off_summary),
                    position = SectionPosition.Middle,
                    onClick = {
                        app.prefsRepository.save(
                            Prefs.RELOCK_ON_SCREEN_OFF,
                            !prefs.relockOnScreenOff,
                        )
                    },
                    trailing = {
                        LockSwitch(checked = prefs.relockOnScreenOff, onCheckedChange = null)
                    },
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.DeleteSweep,
                    title = stringResource(R.string.settings_relock_task_removed_title),
                    summary = stringResource(R.string.settings_relock_task_removed_summary),
                    position = SectionPosition.Bottom,
                    onClick = {
                        app.prefsRepository.save(
                            Prefs.RELOCK_ON_TASK_REMOVED,
                            !prefs.relockOnTaskRemoved,
                        )
                    },
                    trailing = {
                        LockSwitch(checked = prefs.relockOnTaskRemoved, onCheckedChange = null)
                    },
                )
            }

            item { SettingsSectionHeader(title = stringResource(R.string.settings_unlock_prompt)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.TouchApp,
                    title = stringResource(R.string.settings_unlock_confirmation_title),
                    summary = stringResource(R.string.settings_unlock_confirmation_summary),
                    position = SectionPosition.Top,
                    onClick = {
                        app.prefsRepository.save(
                            Prefs.UNLOCK_REQUIRE_CONFIRMATION,
                            !prefs.unlockRequireConfirmation,
                        )
                    },
                    trailing = {
                        LockSwitch(checked = prefs.unlockRequireConfirmation, onCheckedChange = null)
                    },
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Fingerprint,
                    title = stringResource(R.string.unlock_biometrics_title),
                    summary = biometricChoiceLabel(biometricChoice(unlockMethods)),
                    position = SectionPosition.Middle,
                    onClick = { showBiometricDialog = true },
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Dialpad,
                    title = stringResource(R.string.unlock_screen_lock_title),
                    summary =
                        stringResource(
                            if (credentialAvailable) {
                                R.string.unlock_screen_lock_summary
                            } else {
                                R.string.unlock_screen_lock_unset
                            },
                        ),
                    position = SectionPosition.Middle,
                    onClick =
                        if (unlockCredentialToggleable) {
                            {
                                app.prefsRepository.save(
                                    Prefs.UNLOCK_METHODS,
                                    withCredential(unlockMethods, unlockMethods and METHOD_CREDENTIAL == 0),
                                )
                            }
                        } else {
                            null
                        },
                    trailing = {
                        LockSwitch(
                            checked = unlockMethods and METHOD_CREDENTIAL != 0,
                            onCheckedChange = null,
                            enabled = unlockCredentialToggleable,
                        )
                    },
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Build,
                    title = stringResource(R.string.settings_opaque_unlock_prompt_title),
                    summary = stringResource(R.string.settings_opaque_unlock_prompt_summary),
                    position = SectionPosition.Bottom,
                    onClick = {
                        app.prefsRepository.save(
                            Prefs.USE_OPAQUE_UNLOCK_PROMPT,
                            !prefs.useOpaqueUnlockPrompt,
                        )
                    },
                    trailing = {
                        LockSwitch(checked = prefs.useOpaqueUnlockPrompt, onCheckedChange = null)
                    },
                )
            }

            item { SettingsSectionHeader(title = stringResource(R.string.settings_protection)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Screenshot,
                    title = stringResource(R.string.settings_block_screenshots_title),
                    summary = stringResource(R.string.settings_block_screenshots_summary),
                    position = SectionPosition.Top,
                    onClick = {
                        app.prefsRepository.save(
                            Prefs.BLOCK_SCREENSHOTS,
                            !prefs.blockScreenshots,
                        )
                    },
                    trailing = {
                        LockSwitch(checked = prefs.blockScreenshots, onCheckedChange = null)
                    },
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.NotificationsOff,
                    title = stringResource(R.string.settings_hide_notification_content_title),
                    summary = stringResource(R.string.settings_hide_notification_content_summary),
                    position = SectionPosition.Middle,
                    onClick = {
                        app.prefsRepository.save(
                            Prefs.HIDE_NOTIFICATION_CONTENT,
                            !prefs.hideNotificationContent,
                        )
                    },
                    trailing = {
                        LockSwitch(checked = prefs.hideNotificationContent, onCheckedChange = null)
                    },
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.AppBlocking,
                    title = stringResource(R.string.settings_prevent_uninstall_title),
                    summary = stringResource(R.string.settings_prevent_uninstall_summary),
                    position = SectionPosition.Middle,
                    onClick = {
                        app.prefsRepository.save(
                            Prefs.PREVENT_MODULE_UNINSTALL,
                            !prefs.preventModuleUninstall,
                        )
                    },
                    trailing = {
                        LockSwitch(checked = prefs.preventModuleUninstall, onCheckedChange = null)
                    },
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.VisibilityOff,
                    title = stringResource(R.string.settings_hide_launcher),
                    summary = stringResource(R.string.settings_hide_launcher_summary),
                    position = SectionPosition.Bottom,
                    onClick = {
                        if (launcherIconHidden) {
                            LauncherIconHelper.setLauncherIconVisible(context, true)
                            launcherIconHidden = false
                        } else {
                            showHideLauncherConfirm = true
                        }
                    },
                    trailing = {
                        LockSwitch(checked = launcherIconHidden, onCheckedChange = null)
                    },
                )
            }

            item { SettingsSectionHeader(title = stringResource(R.string.settings_self_lock_section)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.ScreenLockPortrait,
                    title = stringResource(R.string.settings_self_lock_title),
                    summary = stringResource(R.string.settings_self_lock_summary),
                    position = if (prefs.selfLock) SectionPosition.Top else SectionPosition.Single,
                    onClick = {
                        app.prefsRepository.save(Prefs.SELF_LOCK, !prefs.selfLock)
                    },
                    trailing = {
                        LockSwitch(checked = prefs.selfLock, onCheckedChange = null)
                    },
                )
            }
            if (prefs.selfLock) {
                item {
                    PreferenceRow(
                        icon = Icons.Outlined.Fingerprint,
                        title = stringResource(R.string.settings_self_lock_biometrics_title),
                        summary = biometricChoiceLabel(biometricChoice(selfLockMethods)),
                        position = SectionPosition.Middle,
                        onClick = { showSelfLockDialog = true },
                    )
                }
                item {
                    PreferenceRow(
                        icon = Icons.Outlined.Dialpad,
                        title = stringResource(R.string.settings_self_lock_credential_title),
                        summary =
                            stringResource(
                                if (credentialAvailable) {
                                    R.string.settings_self_lock_credential_summary
                                } else {
                                    R.string.unlock_screen_lock_unset
                                },
                            ),
                        position = SectionPosition.Bottom,
                        onClick =
                            if (selfLockCredentialToggleable) {
                                {
                                    app.prefsRepository.save(
                                        Prefs.SELF_LOCK_METHODS,
                                        withCredential(selfLockMethods, selfLockMethods and METHOD_CREDENTIAL == 0),
                                    )
                                }
                            } else {
                                null
                            },
                        trailing = {
                            LockSwitch(
                                checked = selfLockMethods and METHOD_CREDENTIAL != 0,
                                onCheckedChange = null,
                                enabled = selfLockCredentialToggleable,
                            )
                        },
                    )
                }
            }

            item { SettingsSectionHeader(title = stringResource(R.string.settings_app_management)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Download,
                    title = stringResource(R.string.settings_app_management_title),
                    summary = stringResource(R.string.settings_app_management_summary),
                    position = SectionPosition.Single,
                    onClick = onNavigateToAppManagement,
                )
            }

            item { SettingsSectionHeader(title = stringResource(R.string.settings_appearance)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Palette,
                    title = stringResource(R.string.settings_theme),
                    summary = stringResource(R.string.settings_theme_summary),
                    position = SectionPosition.Top,
                    onClick = { showThemeDialog = true },
                    trailing = { ValueText(themeModeLabel(prefs.themeMode)) },
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.FormatPaint,
                    title = stringResource(R.string.settings_dynamic_color),
                    summary = stringResource(R.string.settings_dynamic_color_summary),
                    position = SectionPosition.Middle,
                    onClick = {
                        app.prefsRepository.save(Prefs.USE_DYNAMIC_COLOR, !prefs.useDynamicColor)
                    },
                    trailing = {
                        LockSwitch(checked = prefs.useDynamicColor, onCheckedChange = null)
                    },
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.RoundedCorner,
                    title = stringResource(R.string.settings_floating_nav_bar),
                    summary = stringResource(R.string.settings_floating_nav_bar_summary),
                    position = SectionPosition.Bottom,
                    onClick = {
                        app.prefsRepository.save(Prefs.FLOATING_NAV_BAR, !prefs.floatingNavBar)
                    },
                    trailing = {
                        LockSwitch(checked = prefs.floatingNavBar, onCheckedChange = null)
                    },
                )
            }

            item { SettingsSectionHeader(title = stringResource(R.string.settings_updates)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Download,
                    title = stringResource(R.string.updates_check_title),
                    summary = stringResource(R.string.updates_check_summary),
                    position = SectionPosition.Top,
                    onClick = {
                        onShowUpdateSheet()
                        coroutineScope.launch { app.updateRepository.checkNow() }
                    },
                    trailing = {
                        if (showDotIndicator) {
                            Box(
                                modifier = Modifier.size(Tokens.SpacingSm).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    },
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Update,
                    title = stringResource(R.string.updates_auto_check_title),
                    summary = stringResource(R.string.updates_auto_check_summary),
                    position = SectionPosition.Bottom,
                    onClick = {
                        app.prefsRepository.save(Prefs.AUTO_CHECK_UPDATE, !prefs.autoCheckUpdate)
                    },
                    trailing = {
                        LockSwitch(checked = prefs.autoCheckUpdate, onCheckedChange = null)
                    },
                )
            }

            item { SettingsSectionHeader(title = stringResource(R.string.settings_diagnostics)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Share,
                    title = stringResource(R.string.about_send_logs_title),
                    summary =
                        stringResource(
                            if (rootGranted == false) {
                                R.string.about_send_logs_no_root
                            } else {
                                R.string.about_send_logs_summary
                            },
                        ),
                    position = SectionPosition.Single,
                    enabled = rootGranted != false,
                    onClick = { showLogActions = true },
                )
            }

            item { SettingsSectionHeader(title = stringResource(R.string.settings_about)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.NewReleases,
                    title = stringResource(R.string.about_whats_new_title),
                    summary = stringResource(R.string.about_whats_new_summary, BuildConfig.VERSION_NAME),
                    position = SectionPosition.Top,
                    onClick = { showWhatsNew = true },
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.settings_about_entry),
                    summary = stringResource(R.string.settings_about_entry_summary),
                    position = SectionPosition.Bottom,
                    onClick = onNavigateToAbout,
                )
            }
        }
    }
}

@Composable
private fun ValueText(value: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String =
    when (mode) {
        ThemeMode.FOLLOW_SYSTEM -> stringResource(R.string.settings_theme_system)
        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
    }
