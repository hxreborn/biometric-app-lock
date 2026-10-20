package eu.hxreborn.biometricapplock.ui.screen

import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.provider.Settings
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.hxreborn.biometricapplock.App
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.prefs.AppPrefs
import eu.hxreborn.biometricapplock.ui.component.BiometricHardwareSection
import eu.hxreborn.biometricapplock.ui.component.HomeStatus
import eu.hxreborn.biometricapplock.ui.component.LockedAppsSection
import eu.hxreborn.biometricapplock.ui.component.StatusCard
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.ui.viewmodel.ModuleStatus
import eu.hxreborn.biometricapplock.ui.viewmodel.ScopeViewModel
import eu.hxreborn.biometricapplock.util.normalizeMethods
import eu.hxreborn.biometricapplock.util.usableAuthenticators

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DashboardScreen(
    viewModel: ScopeViewModel,
    onNavigateToApps: () -> Unit,
    onNavigateToAppDetail: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val app = App.from(context)
    val moduleStatus by viewModel.moduleStatus.collectAsStateWithLifecycle()
    val scope by viewModel.scope.collectAsStateWithLifecycle()
    val framework by viewModel.framework.collectAsStateWithLifecycle()
    val prefs by app.prefsRepository.state.collectAsStateWithLifecycle(initialValue = AppPrefs.Defaults)
    val installedPackageKeys = rememberInstalledPackageKeys()
    val lockedApps =
        remember(scope, installedPackageKeys) {
            scope.filterTo(linkedSetOf()) { it in installedPackageKeys }
        }
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshFrameworkScope()
        onPauseOrDispose {}
    }
    var unlockMethodAvailable by remember { mutableStateOf(true) }
    LifecycleResumeEffect(prefs.unlockMethods) {
        val bm = context.getSystemService(BiometricManager::class.java)
        unlockMethodAvailable = usableAuthenticators(bm, normalizeMethods(prefs.unlockMethods)) != null
        onPauseOrDispose {}
    }
    val status =
        when {
            moduleStatus == ModuleStatus.NotEnabled -> HomeStatus.NotEnabled
            moduleStatus == ModuleStatus.RebootRequired -> HomeStatus.RebootRequired
            lockedApps.isEmpty() -> HomeStatus.NoApps
            !unlockMethodAvailable -> HomeStatus.NoUnlockMethod
            else -> HomeStatus.Locking
        }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_bar_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding() + Tokens.SpacingSm,
                    bottom = contentPadding.calculateBottomPadding() + Tokens.SpacingLg,
                ),
        ) {
            item {
                StatusCard(
                    status = status,
                    frameworkLabel =
                        framework?.let {
                            stringResource(R.string.status_framework, it.name, it.version.substringBefore(" "), it.api)
                        },
                    onChooseApps = onNavigateToApps,
                    onOpenSecuritySettings = {
                        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                    },
                )
            }
            if (lockedApps.isNotEmpty()) {
                item {
                    LockedAppsSection(
                        scope = lockedApps,
                        onNavigateToApps = onNavigateToApps,
                        onNavigateToAppDetail = onNavigateToAppDetail,
                    )
                }
            }
            item { BiometricHardwareSection() }
        }
    }
}
