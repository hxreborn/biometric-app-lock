@file:Suppress("AssignedValueIsNeverRead")
@file:OptIn(ExperimentalMaterial3Api::class)

package eu.hxreborn.biometricapplock.ui.screen

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.text.format.DateUtils
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.hxreborn.biometricapplock.App
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.prefs.AppOverrides
import eu.hxreborn.biometricapplock.ui.component.ExpandedTitle
import eu.hxreborn.biometricapplock.ui.component.SectionCard
import eu.hxreborn.biometricapplock.ui.component.SectionPosition
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.util.getUserHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun shortActivityName(className: String): String = className.substringAfterLast('.')

private fun sectionPosition(
    index: Int,
    count: Int,
): SectionPosition =
    when {
        count == 1 -> SectionPosition.Single
        index == 0 -> SectionPosition.Top
        index == count - 1 -> SectionPosition.Bottom
        else -> SectionPosition.Middle
    }

@Composable
fun rememberLauncherActivities(packageKey: String): Set<String> {
    val context = LocalContext.current
    val packageName = remember(packageKey) { packageKey.substringBeforeLast(':') }
    val userId = remember(packageKey) { packageKey.substringAfterLast(':').toIntOrNull() ?: 0 }
    return produceState(emptySet<String>(), packageKey) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                    val userHandle = getUserHandle(userId)
                    launcherApps.getActivityList(packageName, userHandle).mapTo(mutableSetOf()) { it.componentName.className }
                }.getOrDefault(emptySet())
            }
    }.value
}

@Composable
private fun rememberDeclaredActivities(packageKey: String): List<String> {
    val context = LocalContext.current
    val packageName = remember(packageKey) { packageKey.substringBeforeLast(':') }
    val userId = remember(packageKey) { packageKey.substringAfterLast(':').toIntOrNull() ?: 0 }
    return produceState(emptyList<String>(), packageKey) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    if (userId == 0) {
                        context.packageManager
                            .getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
                            .activities
                            ?.map { it.name }
                            ?.sorted()
                            .orEmpty()
                    } else {
                        // For other users, we might only be able to see launcher activities if we are not root or system
                        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                        val userHandle = getUserHandle(userId)
                        launcherApps.getActivityList(packageName, userHandle).map { it.componentName.className }.sorted()
                    }
                }.getOrDefault(emptyList())
            }
    }.value
}

@Composable
fun ActivityToggleRow(
    className: String,
    checked: Boolean,
    isLauncher: Boolean,
    recentAt: Long?,
    position: SectionPosition,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(modifier = modifier, position = position, onClick = { onCheckedChange(!checked) }) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = Tokens.PreferenceRowHorizontalPadding,
                        vertical = Tokens.PreferenceRowVerticalPadding,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = shortActivityName(className),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isLauncher) {
                        Spacer(Modifier.width(Tokens.SpacingSm))
                        LauncherBadge()
                    }
                }
                Text(
                    text = className,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (recentAt != null) {
                    Text(
                        text =
                            stringResource(
                                R.string.app_detail_allowed_recent,
                                DateUtils
                                    .getRelativeTimeSpanString(
                                        recentAt,
                                        System.currentTimeMillis(),
                                        DateUtils.MINUTE_IN_MILLIS,
                                    ).toString(),
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(Tokens.PreferenceRowTrailingSpacing))
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun LauncherBadge() {
    Surface(
        shape = RoundedCornerShape(Tokens.SmallCornerRadius),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = stringResource(R.string.app_detail_allowed_main_badge),
            modifier = Modifier.padding(horizontal = Tokens.SpacingSm, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
fun LauncherAllowConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_detail_allowed_main_confirm_title)) },
        text = { Text(stringResource(R.string.app_detail_allowed_main_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
fun AllowedActivitiesScreen(
    packageKey: String,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = App.from(context)

    val overrides by app.appOverridesRepository
        .observe(packageKey)
        .collectAsStateWithLifecycle(initialValue = AppOverrides(null, null))
    val recents by app.appOverridesRepository
        .observeRecentActivities(packageKey)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val allowed = overrides.allowedActivities
    val launcherActivities = rememberLauncherActivities(packageKey)
    val declared = rememberDeclaredActivities(packageKey)

    val recentTimes = remember(recents) { recents.associate { it.className to it.lastSeen } }
    val ordered =
        remember(recents, declared, allowed) {
            (recents.map { it.className } + declared + allowed).distinct()
        }

    val searchState = rememberTextFieldState()
    val query = searchState.text.toString()
    val filtered =
        remember(ordered, query) {
            if (query.isBlank()) ordered else ordered.filter { it.contains(query, ignoreCase = true) }
        }

    var pendingLauncher by remember { mutableStateOf<String?>(null) }

    fun toggle(
        name: String,
        allow: Boolean,
    ) {
        if (allow && name in launcherActivities) {
            pendingLauncher = name
            return
        }
        app.appOverridesRepository.setAllowedActivities(
            packageKey,
            if (allow) allowed + name else allowed - name,
        )
    }

    pendingLauncher?.let { name ->
        LauncherAllowConfirmDialog(
            onConfirm = {
                app.appOverridesRepository.setAllowedActivities(packageKey, allowed + name)
                pendingLauncher = null
            },
            onDismiss = { pendingLauncher = null },
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
                title = { ExpandedTitle(stringResource(R.string.app_detail_allowed_section)) },
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
            item(key = "search") {
                TextField(
                    state = searchState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Tokens.ScreenHorizontalPadding, vertical = Tokens.SpacingSm),
                    placeholder = { Text(stringResource(R.string.app_detail_allowed_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { searchState.clearText() }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.apps_search_clear_cd))
                            }
                        }
                    },
                    shape = SearchBarDefaults.inputFieldShape,
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                )
            }

            item(key = "desc") {
                Text(
                    text = stringResource(R.string.app_detail_allowed_screen_desc),
                    modifier =
                        Modifier.padding(
                            horizontal = Tokens.SectionHorizontalMargin + Tokens.PreferenceRowHorizontalPadding,
                            vertical = Tokens.SpacingSm,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (filtered.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(Tokens.SpacingLg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text =
                                stringResource(
                                    if (query.isNotBlank()) {
                                        R.string.app_detail_allowed_no_matches
                                    } else {
                                        R.string.app_detail_allowed_browse_empty
                                    },
                                ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                itemsIndexed(filtered, key = { _, name -> name }) { index, name ->
                    ActivityToggleRow(
                        className = name,
                        checked = name in allowed,
                        isLauncher = name in launcherActivities,
                        recentAt = recentTimes[name],
                        position = sectionPosition(index, filtered.size),
                        onCheckedChange = { toggle(name, it) },
                    )
                }
            }
        }
    }
}
