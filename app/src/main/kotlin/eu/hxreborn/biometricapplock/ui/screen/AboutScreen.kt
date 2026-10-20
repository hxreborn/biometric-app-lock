@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package eu.hxreborn.biometricapplock.ui.screen

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.hxreborn.biometricapplock.BuildConfig
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.ui.component.AdaptiveButtonRow
import eu.hxreborn.biometricapplock.ui.component.BackButton
import eu.hxreborn.biometricapplock.ui.component.MorphingLogo
import eu.hxreborn.biometricapplock.ui.component.SectionPosition
import eu.hxreborn.biometricapplock.ui.screen.settings.PreferenceRow
import eu.hxreborn.biometricapplock.ui.screen.settings.SettingsSectionHeader
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.ui.viewmodel.FrameworkInfo

private const val REPO_URL = "https://github.com/hxreborn/biometric-app-lock"
private const val KOFI_URL = "https://ko-fi.com/hxreborn"
private const val LICENSE_NAME = "GPL-3.0"

@Composable
fun AboutScreen(
    framework: FrameworkInfo?,
    onBack: () -> Unit,
    onShowWhatsNew: () -> Unit,
    onNavigateToChangelog: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    onTriggerHotReload: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MediumFlexibleTopAppBar(
                navigationIcon = { BackButton(onClick = onBack) },
                title = { Text(stringResource(R.string.about_title)) },
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
            item { IdentityCard(onOpenUrl = uriHandler::openUri) }

            item { SettingsSectionHeader(title = stringResource(R.string.about_section_links)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.NewReleases,
                    title = stringResource(R.string.about_whats_new_title),
                    summary = stringResource(R.string.about_whats_new_summary, BuildConfig.VERSION_NAME),
                    position = SectionPosition.Top,
                    onClick = onShowWhatsNew,
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.History,
                    title = stringResource(R.string.about_changelog_title),
                    summary = stringResource(R.string.about_changelog_summary),
                    position = SectionPosition.Middle,
                    onClick = onNavigateToChangelog,
                )
            }
            item {
                PreferenceRow(
                    icon = painterResource(R.drawable.ic_github_24),
                    title = stringResource(R.string.about_source_title),
                    summary = stringResource(R.string.about_source_summary),
                    position = SectionPosition.Middle,
                    onClick = { uriHandler.openUri(REPO_URL) },
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.BugReport,
                    title = stringResource(R.string.about_report_issue_title),
                    summary = stringResource(R.string.about_report_issue_summary),
                    position = SectionPosition.Middle,
                    onClick = { uriHandler.openUri("$REPO_URL/issues/new/choose") },
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Description,
                    title = stringResource(R.string.about_licenses_title),
                    summary = stringResource(R.string.about_licenses_summary),
                    position = SectionPosition.Bottom,
                    onClick = onNavigateToLicenses,
                )
            }

            item { SettingsSectionHeader(title = stringResource(R.string.about_section_device)) }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Android,
                    title = stringResource(R.string.about_android_title),
                    summary = stringResource(R.string.about_android_summary, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
                    position = SectionPosition.Top,
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Memory,
                    title = stringResource(R.string.about_framework_title),
                    summary = framework?.let { "${it.name} ${it.version}" } ?: stringResource(R.string.settings_lsposed_framework_unbound),
                    position = SectionPosition.Middle,
                )
            }
            item {
                PreferenceRow(
                    icon = Icons.Outlined.Extension,
                    title = stringResource(R.string.about_libxposed_api_title),
                    summary = BuildConfig.LIBXPOSED_API_VERSION.toString(),
                    position = SectionPosition.Bottom,
                )
            }

            if (BuildConfig.DEBUG) {
                item { SettingsSectionHeader(title = stringResource(R.string.about_section_debug)) }
                item {
                    PreferenceRow(
                        icon = Icons.Outlined.Refresh,
                        title = stringResource(R.string.about_debug_hot_reload_title),
                        summary = stringResource(R.string.about_debug_hot_reload_summary),
                        onClick = onTriggerHotReload,
                    )
                }
            }
        }
    }
}

@Composable
private fun IdentityCard(onOpenUrl: (String) -> Unit) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.SectionHorizontalMargin, vertical = Tokens.SpacingSm),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(Tokens.SpacingLg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Tokens.SheetItemSpacing),
        ) {
            MorphingLogo(Modifier.size(Tokens.AboutBadgeSize))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Tokens.SpacingXs),
            ) {
                Text(
                    text = stringResource(R.string.about_app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.about_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Tokens.SpacingSm, Alignment.CenterHorizontally),
            ) {
                MetaChip(
                    label = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    icon = Icons.Outlined.Sell,
                    onClick = { onOpenUrl("$REPO_URL/releases/tag/v${BuildConfig.VERSION_NAME}") },
                )
                MetaChip(
                    label = LICENSE_NAME,
                    icon = Icons.Outlined.Balance,
                    onClick = { onOpenUrl("$REPO_URL/blob/main/LICENSE") },
                )
            }
            val star = stringResource(R.string.about_star_on_github)
            val coffee = stringResource(R.string.about_donate)
            AdaptiveButtonRow(
                labels = listOf(star, coffee),
                labelStyle = MaterialTheme.typography.labelLarge,
                nonLabelWidth =
                    ButtonDefaults.SmallContentPadding.calculateLeftPadding(LayoutDirection.Ltr) +
                        ButtonDefaults.SmallContentPadding.calculateRightPadding(LayoutDirection.Ltr) +
                        ButtonDefaults.IconSize +
                        ButtonDefaults.IconSpacing,
            ) { stacked, itemModifier ->
                Button(
                    onClick = { onOpenUrl(REPO_URL) },
                    contentPadding = ButtonDefaults.SmallContentPadding,
                    shapes =
                        if (stacked) {
                            ButtonDefaults.shapes()
                        } else {
                            ButtonDefaults.shapes(
                                ButtonGroupDefaults.connectedLeadingButtonShape,
                                ButtonGroupDefaults.connectedLeadingButtonPressShape,
                            )
                        },
                    modifier = itemModifier.heightIn(min = Tokens.ConnectedButtonHeight),
                ) {
                    ButtonLabel(Icons.Outlined.StarOutline, star)
                }
                FilledTonalButton(
                    onClick = { onOpenUrl(KOFI_URL) },
                    contentPadding = ButtonDefaults.SmallContentPadding,
                    shapes =
                        if (stacked) {
                            ButtonDefaults.shapes()
                        } else {
                            ButtonDefaults.shapes(
                                ButtonGroupDefaults.connectedTrailingButtonShape,
                                ButtonGroupDefaults.connectedTrailingButtonPressShape,
                            )
                        },
                    modifier = itemModifier.heightIn(min = Tokens.ConnectedButtonHeight),
                ) {
                    ButtonLabel(Icons.Outlined.Coffee, coffee)
                }
            }
        }
    }
}

@Composable
private fun MetaChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
    )
}

@Composable
private fun ButtonLabel(
    icon: ImageVector,
    text: String,
) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    Text(text, maxLines = 1, softWrap = false)
}
