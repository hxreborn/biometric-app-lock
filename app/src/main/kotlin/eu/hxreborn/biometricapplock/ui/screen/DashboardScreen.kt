package eu.hxreborn.biometricapplock.ui.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.ui.component.BiometricHardwareSection
import eu.hxreborn.biometricapplock.ui.component.ExpandedTitle
import eu.hxreborn.biometricapplock.ui.component.LockedAppsSection
import eu.hxreborn.biometricapplock.ui.component.StatusCard
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.ui.viewmodel.ScopeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: ScopeViewModel,
    onNavigateToApps: () -> Unit,
    onNavigateToAppDetail: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val moduleStatus by viewModel.moduleStatus.collectAsStateWithLifecycle()
    val scope by viewModel.scope.collectAsStateWithLifecycle()
    val serviceLoadEvent by viewModel.serviceLoadEvent.collectAsStateWithLifecycle()
    val launchablePackageKeys = rememberLaunchablePackageKeys()
    val lockedApps =
        remember(scope, launchablePackageKeys) {
            scope.filterTo(linkedSetOf()) { it in launchablePackageKeys }
        }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { ExpandedTitle(stringResource(R.string.app_bar_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = Tokens.SpacingSm),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding() + Tokens.SpacingSm,
                    bottom = contentPadding.calculateBottomPadding() + Tokens.SpacingLg,
                ),
        ) {
            item {
                StatusCard(
                    status = moduleStatus,
                    lockedAppCount = lockedApps.size,
                    serviceLoadEvent = serviceLoadEvent,
                )
            }
            item {
                LockedAppsSection(
                    scope = lockedApps,
                    onNavigateToApps = onNavigateToApps,
                    onNavigateToAppDetail = onNavigateToAppDetail,
                )
            }
            item { BiometricHardwareSection() }
        }
    }
}
