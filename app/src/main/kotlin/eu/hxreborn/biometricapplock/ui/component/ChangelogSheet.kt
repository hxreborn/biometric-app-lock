package eu.hxreborn.biometricapplock.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.hxreborn.biometricapplock.App
import eu.hxreborn.biometricapplock.BuildConfig
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.prefs.Prefs
import eu.hxreborn.biometricapplock.updates.ChangeType
import eu.hxreborn.biometricapplock.updates.UpdateSheetState
import eu.hxreborn.biometricapplock.updates.toSheetState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChangelogSheet(
    onDismiss: () -> Unit,
    showCheckingState: Boolean = true,
) {
    val app = App.from(LocalContext.current)
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val cachedEntries by app.updateRepository.cachedChangelog.collectAsStateWithLifecycle()
    val cachedAvailable by app.updateRepository.cachedAvailable.collectAsStateWithLifecycle()
    val updateState by app.updateRepository.currentState.collectAsStateWithLifecycle()
    var minDelayPassed by remember { mutableStateOf(!showCheckingState) }

    LaunchedEffect(Unit) {
        app.updateRepository.fetchChangelog()
        if (showCheckingState) {
            delay(1000L)
            minDelayPassed = true
        }
    }

    val latestVersionForMatch = cachedAvailable?.latestVersion ?: BuildConfig.VERSION_NAME
    val hasMatchingChangelogEntry = cachedEntries?.any { it.version != null && it.version == latestVersionForMatch } ?: false

    val sheetState =
        if (!minDelayPassed) {
            UpdateSheetState.Checking
        } else {
            updateState.toSheetState(
                cached = cachedAvailable,
                hasMatchingChangelogEntry = hasMatchingChangelogEntry,
            )
        }

    val versionLabel =
        when (sheetState) {
            is UpdateSheetState.Available -> sheetState.latestVersion
            is UpdateSheetState.UpToDate -> sheetState.currentVersion
            is UpdateSheetState.Failed -> sheetState.cachedFallback?.latestVersion ?: BuildConfig.VERSION_NAME
            else -> BuildConfig.VERSION_NAME
        }

    val items =
        cachedEntries?.filter { it.version == latestVersionForMatch }?.map { entry ->
            val type = ChangeType.from(entry.type, entry.breaking)
            FeatureSheetItem(
                label = stringResource(changeTypeLabelRes(type)),
                scope = entry.scope?.takeIf { it.isNotBlank() },
                changeType = type,
                title = entry.title,
                body = entry.description,
                onClick = entry.url?.let { url -> { uriHandler.openUri(url) } },
            )
        } ?: emptyList()

    WhatsNewSheet(
        state = sheetState,
        items = items,
        versionLabel = versionLabel,
        onDismiss = onDismiss,
        onDownload = { url -> uriHandler.openUri(url) },
        onRetry = {
            coroutineScope.launch { app.updateRepository.checkNow() }
        },
        onLater = { version ->
            app.prefsRepository.save(Prefs.LAST_DISMISSED_AVAILABLE_VERSION, version)
        },
    )
}

internal fun changeTypeIcon(type: ChangeType): ImageVector =
    when (type) {
        ChangeType.Feat -> Icons.Filled.AutoAwesome
        ChangeType.Fix -> Icons.Outlined.BugReport
        ChangeType.Perf -> Icons.Outlined.Bolt
        ChangeType.Security -> Icons.Outlined.Shield
        ChangeType.Refactor -> Icons.Outlined.Tune
        ChangeType.Revert -> Icons.AutoMirrored.Outlined.Undo
        ChangeType.Ci -> Icons.Outlined.Cloud
        ChangeType.Test -> Icons.Outlined.Science
        ChangeType.Misc -> Icons.Outlined.MoreHoriz
        ChangeType.Breaking -> Icons.Outlined.PriorityHigh
    }

internal fun changeTypeLabelRes(type: ChangeType): Int =
    when (type) {
        ChangeType.Feat -> R.string.change_type_feat
        ChangeType.Fix -> R.string.change_type_fix
        ChangeType.Perf -> R.string.change_type_perf
        ChangeType.Security -> R.string.change_type_security
        ChangeType.Refactor -> R.string.change_type_refactor
        ChangeType.Revert -> R.string.change_type_revert
        ChangeType.Ci -> R.string.change_type_ci
        ChangeType.Test -> R.string.change_type_test
        ChangeType.Misc -> R.string.change_type_misc
        ChangeType.Breaking -> R.string.change_type_breaking
    }
