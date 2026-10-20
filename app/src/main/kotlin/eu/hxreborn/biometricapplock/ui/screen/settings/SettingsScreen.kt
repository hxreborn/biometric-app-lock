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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AppBlocking
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InstallMobile
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.hxreborn.biometricapplock.App
import eu.hxreborn.biometricapplock.BuildConfig
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.prefs.AppPrefs
import eu.hxreborn.biometricapplock.prefs.BoolPref
import eu.hxreborn.biometricapplock.prefs.Prefs
import eu.hxreborn.biometricapplock.prefs.ThemeMode
import eu.hxreborn.biometricapplock.ui.component.LockSwitch
import eu.hxreborn.biometricapplock.ui.component.LogActionsSheet
import eu.hxreborn.biometricapplock.ui.component.SectionPosition
import eu.hxreborn.biometricapplock.ui.screen.BiometricChoiceDialog
import eu.hxreborn.biometricapplock.ui.screen.RelockDelayDialog
import eu.hxreborn.biometricapplock.ui.screen.biometricChoiceLabel
import eu.hxreborn.biometricapplock.ui.screen.relockDelaySummary
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.ui.util.LauncherIconHelper
import eu.hxreborn.biometricapplock.ui.viewmodel.FrameworkInfo
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
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val prefs by app.prefsRepository.state.collectAsStateWithLifecycle(initialValue = AppPrefs.Defaults)
    val cachedAvailable by app.updateRepository.cachedAvailable.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var showRelockDialog by rememberSaveable { mutableStateOf(false) }
    var showLogActions by rememberSaveable { mutableStateOf(false) }
    var pendingSaveFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var launcherIconHidden by remember { mutableStateOf(!LauncherIconHelper.isLauncherIconVisible(context)) }
    var showHideLauncherConfirm by rememberSaveable { mutableStateOf(false) }

    val bm = remember { context.getSystemService(BiometricManager::class.java) }
    val unlockMethods = normalizeMethods(prefs.unlockMethods)
    val credentialAvailable = remember { bm.canAuthenticate(methodAuthenticators(METHOD_CREDENTIAL)) == BiometricManager.BIOMETRIC_SUCCESS }
    val unlockCredentialToggleable = credentialAvailable && unlockMethods and METHOD_BIOMETRIC != 0
    val selfLockMethods = normalizeMethods(prefs.selfLockMethods)
    val selfLockCredentialToggleable = credentialAvailable && selfLockMethods and METHOD_BIOMETRIC != 0
    var showBiometricDialog by rememberSaveable { mutableStateOf(false) }
    var showSelfLockDialog by rememberSaveable { mutableStateOf(false) }

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
            val filePath = pendingSaveFilePath
            pendingSaveFilePath = null
            if (uri == null || filePath == null) return@rememberLauncherForActivityResult
            val file = File(filePath)
            coroutineScope.launch {
                val saved = DiagnosticsExporter.saveTo(context, file, uri)
                haptics.performHapticFeedback(if (saved) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
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
                mode.applyAsAppNightMode(context)
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
                        haptics.toggle(true)
                        LauncherIconHelper.setLauncherIconVisible(context, false)
                        launcherIconHidden = true
                        showHideLauncherConfirm = false
                    },
                ) {
                    Text(stringResource(R.string.settings_hide_launcher_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showHideLauncherConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
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
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                Toast.makeText(context, R.string.diagnostics_no_logs, Toast.LENGTH_LONG).show()
                false
            }
        }
        LogActionsSheet(
            onSave = {
                collect { file ->
                    pendingSaveFilePath = file.absolutePath
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
    val save: (BoolPref, Boolean) -> Unit = { pref, value -> app.prefsRepository.save(pref, value) }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.tab_settings)) },
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
                SwitchPreferenceRow(
                    icon = Icons.Outlined.Update,
                    title = stringResource(R.string.updates_auto_check_title),
                    summary = stringResource(R.string.updates_auto_check_summary),
                    checked = prefs.autoCheckUpdate,
                    onCheckedChange = { save(Prefs.AUTO_CHECK_UPDATE, it) },
                    position = SectionPosition.Bottom,
                )
            }

            item { SettingsSectionHeader(title = stringResource(R.string.settings_lock)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Timer,
                    title = stringResource(R.string.settings_relock_delay_title),
                    summary = relockDelaySummary(prefs.relockDelaySeconds),
                    position = SectionPosition.Top,
                    onClick = { showRelockDialog = true },
                )
            }
            item {
                SwitchPreferenceRow(
                    icon = Icons.Outlined.ScreenLockPortrait,
                    title = stringResource(R.string.settings_relock_screen_off_title),
                    summary = stringResource(R.string.settings_relock_screen_off_summary),
                    checked = prefs.relockOnScreenOff,
                    onCheckedChange = { save(Prefs.RELOCK_ON_SCREEN_OFF, it) },
                    position = SectionPosition.Middle,
                )
            }
            item {
                SwitchPreferenceRow(
                    icon = Icons.Outlined.DeleteSweep,
                    title = stringResource(R.string.settings_relock_task_removed_title),
                    summary = stringResource(R.string.settings_relock_task_removed_summary),
                    checked = prefs.relockOnTaskRemoved,
                    onCheckedChange = { save(Prefs.RELOCK_ON_TASK_REMOVED, it) },
                    position = SectionPosition.Bottom,
                )
            }

            item { SettingsSectionHeader(title = stringResource(R.string.settings_unlock_prompt)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Fingerprint,
                    title = stringResource(R.string.unlock_biometrics_title),
                    summary = biometricChoiceLabel(biometricChoice(unlockMethods)),
                    position = SectionPosition.Top,
                    onClick = { showBiometricDialog = true },
                )
            }
            item {
                SwitchPreferenceRow(
                    icon = Icons.Outlined.Dialpad,
                    title = stringResource(R.string.unlock_screen_lock_title),
                    summary =
                        stringResource(
                            if (credentialAvailable) R.string.unlock_screen_lock_summary else R.string.unlock_screen_lock_unset,
                        ),
                    checked = unlockMethods and METHOD_CREDENTIAL != 0,
                    onCheckedChange = { app.prefsRepository.save(Prefs.UNLOCK_METHODS, withCredential(unlockMethods, it)) },
                    position = SectionPosition.Middle,
                    switchEnabled = unlockCredentialToggleable,
                )
            }
            item {
                SwitchPreferenceRow(
                    icon = Icons.Outlined.TouchApp,
                    title = stringResource(R.string.settings_unlock_confirmation_title),
                    summary = stringResource(R.string.settings_unlock_confirmation_summary),
                    checked = prefs.unlockRequireConfirmation,
                    onCheckedChange = { save(Prefs.UNLOCK_REQUIRE_CONFIRMATION, it) },
                    position = SectionPosition.Middle,
                )
            }
            item {
                SwitchPreferenceRow(
                    icon = Icons.Outlined.Build,
                    title = stringResource(R.string.settings_opaque_unlock_prompt_title),
                    summary = stringResource(R.string.settings_opaque_unlock_prompt_summary),
                    checked = prefs.useOpaqueUnlockPrompt,
                    onCheckedChange = { save(Prefs.USE_OPAQUE_UNLOCK_PROMPT, it) },
                    position = SectionPosition.Bottom,
                )
            }

            item { SettingsSectionHeader(title = stringResource(R.string.settings_protection)) }
            item {
                SwitchPreferenceRow(
                    icon = Icons.Outlined.Screenshot,
                    title = stringResource(R.string.settings_block_screenshots_title),
                    summary = stringResource(R.string.settings_block_screenshots_summary),
                    checked = prefs.blockScreenshots,
                    onCheckedChange = { save(Prefs.BLOCK_SCREENSHOTS, it) },
                    position = SectionPosition.Top,
                )
            }
            item {
                SwitchPreferenceRow(
                    icon = Icons.Outlined.NotificationsOff,
                    title = stringResource(R.string.settings_hide_notification_content_title),
                    summary = stringResource(R.string.settings_hide_notification_content_summary),
                    checked = prefs.hideNotificationContent,
                    onCheckedChange = { save(Prefs.HIDE_NOTIFICATION_CONTENT, it) },
                    position = SectionPosition.Middle,
                )
            }
            item {
                SwitchPreferenceRow(
                    icon = Icons.Outlined.AppBlocking,
                    title = stringResource(R.string.settings_prevent_uninstall_title),
                    summary = stringResource(R.string.settings_prevent_uninstall_summary),
                    checked = prefs.preventModuleUninstall,
                    onCheckedChange = { save(Prefs.PREVENT_MODULE_UNINSTALL, it) },
                    position = SectionPosition.Middle,
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
                            haptics.toggle(false)
                            LauncherIconHelper.setLauncherIconVisible(context, true)
                            launcherIconHidden = false
                        } else {
                            showHideLauncherConfirm = true
                        }
                    },
                    trailing = { LockSwitch(checked = launcherIconHidden, onCheckedChange = null) },
                )
            }

            item { SettingsSectionHeader(title = stringResource(R.string.settings_self_lock_section)) }
            item {
                SwitchPreferenceRow(
                    icon = Icons.Outlined.ScreenLockPortrait,
                    title = stringResource(R.string.settings_self_lock_title),
                    summary = stringResource(R.string.settings_self_lock_summary),
                    checked = prefs.selfLock,
                    onCheckedChange = { save(Prefs.SELF_LOCK, it) },
                    position = if (prefs.selfLock) SectionPosition.Top else SectionPosition.Single,
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
                    SwitchPreferenceRow(
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
                        checked = selfLockMethods and METHOD_CREDENTIAL != 0,
                        onCheckedChange = { app.prefsRepository.save(Prefs.SELF_LOCK_METHODS, withCredential(selfLockMethods, it)) },
                        position = SectionPosition.Bottom,
                        switchEnabled = selfLockCredentialToggleable,
                    )
                }
            }

            item { Spacer(Modifier.height(Tokens.SectionHeaderTopPadding)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.InstallMobile,
                    title = stringResource(R.string.settings_app_management_title),
                    summary = stringResource(R.string.settings_app_management_summary),
                    position = SectionPosition.Single,
                    onClick = onNavigateToAppManagement,
                    trailing = { Chevron() },
                )
            }

            item { SettingsSectionHeader(title = stringResource(R.string.settings_appearance)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Palette,
                    title = stringResource(R.string.settings_theme),
                    summary = themeModeLabel(prefs.themeMode),
                    position = SectionPosition.Top,
                    onClick = { showThemeDialog = true },
                )
            }
            item {
                SwitchPreferenceRow(
                    icon = Icons.Outlined.FormatPaint,
                    title = stringResource(R.string.settings_dynamic_color),
                    summary = stringResource(R.string.settings_dynamic_color_summary),
                    checked = prefs.useDynamicColor,
                    onCheckedChange = { save(Prefs.USE_DYNAMIC_COLOR, it) },
                    position = SectionPosition.Middle,
                )
            }
            item {
                SwitchPreferenceRow(
                    icon = Icons.Outlined.RoundedCorner,
                    title = stringResource(R.string.settings_floating_nav_bar),
                    summary = stringResource(R.string.settings_floating_nav_bar_summary),
                    checked = prefs.floatingNavBar,
                    onCheckedChange = { save(Prefs.FLOATING_NAV_BAR, it) },
                    position = SectionPosition.Bottom,
                )
            }

            item { SettingsSectionHeader(title = stringResource(R.string.settings_diagnostics)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Share,
                    title = stringResource(R.string.about_send_logs_title),
                    summary =
                        stringResource(
                            if (rootGranted == false) R.string.about_send_logs_no_root else R.string.about_send_logs_summary,
                        ),
                    position = SectionPosition.Single,
                    enabled = rootGranted != false,
                    onClick = { showLogActions = true },
                )
            }

            item { Spacer(Modifier.height(Tokens.SectionHeaderTopPadding)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.settings_about_entry),
                    summary = stringResource(R.string.settings_about_entry_summary, BuildConfig.VERSION_NAME),
                    position = SectionPosition.Single,
                    onClick = onNavigateToAbout,
                    trailing = { Chevron() },
                )
            }
        }
    }
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String =
    when (mode) {
        ThemeMode.FOLLOW_SYSTEM -> stringResource(R.string.settings_theme_system)
        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
    }
