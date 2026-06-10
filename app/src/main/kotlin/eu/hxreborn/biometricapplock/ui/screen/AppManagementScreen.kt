@file:OptIn(ExperimentalMaterial3Api::class)

package eu.hxreborn.biometricapplock.ui.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.hxreborn.biometricapplock.App
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.prefs.AppPrefs
import eu.hxreborn.biometricapplock.prefs.Prefs
import eu.hxreborn.biometricapplock.ui.component.BackButton
import eu.hxreborn.biometricapplock.ui.component.ExpandedTitle
import eu.hxreborn.biometricapplock.ui.component.LockSwitch
import eu.hxreborn.biometricapplock.ui.component.SectionPosition
import eu.hxreborn.biometricapplock.ui.screen.settings.PreferenceRow
import eu.hxreborn.biometricapplock.ui.screen.settings.SettingsSectionHeader
import eu.hxreborn.biometricapplock.ui.theme.Tokens

@Composable
fun AppManagementScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = App.from(context)
    val prefs by app.prefsRepository.state.collectAsStateWithLifecycle(initialValue = AppPrefs.Defaults)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                navigationIcon = { BackButton(onClick = onBack) },
                title = { ExpandedTitle(stringResource(R.string.app_management_title)) },
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
                Text(
                    text = stringResource(R.string.app_management_description),
                    modifier =
                        Modifier.padding(
                            horizontal = Tokens.SectionHorizontalMargin + Tokens.PreferenceRowHorizontalPadding,
                            vertical = Tokens.SpacingSm,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { SettingsSectionHeader(title = stringResource(R.string.app_management_title)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Download,
                    title = stringResource(R.string.app_management_install_title),
                    summary = stringResource(R.string.app_management_install_summary),
                    position = SectionPosition.Top,
                    onClick = {
                        app.prefsRepository.save(
                            Prefs.REQUIRE_BIOMETRIC_INSTALL,
                            !prefs.requireBiometricInstall,
                        )
                    },
                    trailing = {
                        LockSwitch(checked = prefs.requireBiometricInstall, onCheckedChange = null)
                    },
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Delete,
                    title = stringResource(R.string.app_management_uninstall_title),
                    summary = stringResource(R.string.app_management_uninstall_summary),
                    position = SectionPosition.Bottom,
                    onClick = {
                        app.prefsRepository.save(
                            Prefs.REQUIRE_BIOMETRIC_UNINSTALL,
                            !prefs.requireBiometricUninstall,
                        )
                    },
                    trailing = {
                        LockSwitch(checked = prefs.requireBiometricUninstall, onCheckedChange = null)
                    },
                )
            }
        }
    }
}
