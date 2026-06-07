@file:Suppress("AssignedValueIsNeverRead")

package eu.hxreborn.biometricapplock.ui.screen

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.UserHandle
import android.os.UserManager
import android.util.LruCache
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.hxreborn.biometricapplock.App
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.ui.component.ExpandedTitle
import eu.hxreborn.biometricapplock.ui.component.LockSwitch
import eu.hxreborn.biometricapplock.ui.component.SectionCard
import eu.hxreborn.biometricapplock.ui.component.SectionPosition
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.ui.util.openAppInfo
import eu.hxreborn.biometricapplock.ui.viewmodel.ScopeViewModel
import eu.hxreborn.biometricapplock.util.getUserHandle
import eu.hxreborn.biometricapplock.util.getUserId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class AppItem(
    val label: String,
    val packageName: String,
    val userId: Int,
    val isSystem: Boolean,
) {
    val key: String get() = "$packageName:$userId"
}

private data class AppLoadState(
    val apps: List<AppItem>,
    val isLoading: Boolean,
    val refreshKey: Int,
)

@SuppressLint("QueryPermissionsNeeded")
private suspend fun loadApps(
    context: Context,
    ownPackage: String,
): List<AppItem> =
    withContext(Dispatchers.IO) {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val profiles = userManager.userProfiles

        val entries = mutableListOf<Pair<LauncherActivityInfo, Int>>()
        for (user in profiles) {
            val userId = getUserId(user)
            launcherApps.getActivityList(null, user).forEach { info ->
                if (info.applicationInfo.packageName != ownPackage) {
                    entries.add(info to userId)
                }
            }
        }

        coroutineScope {
            entries
                .map { (info, userId) ->
                    async {
                        val appInfo = info.applicationInfo
                        AppItem(
                            label = info.label.toString(),
                            packageName = appInfo.packageName,
                            userId = userId,
                            isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        )
                    }
                }.awaitAll()
        }
    }

private val iconCache = LruCache<String, ImageBitmap>(200)

@Composable
private fun rememberAppIcon(
    packageName: String,
    userId: Int,
): ImageBitmap? {
    val context = LocalContext.current
    val key = "$packageName:$userId"
    return produceState<ImageBitmap?>(initialValue = iconCache.get(key), key1 = key) {
        if (value != null) return@produceState
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                    val userHandle = getUserHandle(userId)
                    val info = launcherApps.getActivityList(packageName, userHandle).firstOrNull()
                    info?.getIcon(0)?.toBitmap()?.asImageBitmap()
                }.getOrNull()?.also { iconCache.put(key, it) }
            }
    }.value
}

@Composable
private fun rememberInstalledApps(refreshKey: Int): AppLoadState {
    val context = LocalContext.current
    val ownPackage = context.packageName

    return produceState(
        initialValue = AppLoadState(emptyList(), isLoading = true, refreshKey = refreshKey),
        key1 = refreshKey,
    ) {
        value = AppLoadState(value.apps, isLoading = true, refreshKey = refreshKey)
        val apps = loadApps(context, ownPackage)
        value = AppLoadState(apps, isLoading = false, refreshKey = refreshKey)
    }.value
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSearchBar(
    textFieldState: TextFieldState,
    showSystemApps: Boolean,
    onSystemToggle: (Boolean) -> Unit,
    selectedCount: Int,
    canClear: Boolean,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

    TextField(
        state = textFieldState,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.apps_search_placeholder)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (textFieldState.text.isNotEmpty()) {
                    IconButton(onClick = { textFieldState.clearText() }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.apps_search_clear_cd))
                    }
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.apps_search_menu_cd))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.apps_menu_show_system)) },
                            leadingIcon = { Icon(Icons.Outlined.Visibility, contentDescription = null) },
                            trailingIcon = {
                                Checkbox(
                                    checked = showSystemApps,
                                    onCheckedChange = null,
                                )
                            },
                            onClick = { onSystemToggle(!showSystemApps) },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.apps_menu_clear_selection, selectedCount),
                                    color =
                                        if (canClear) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            Color.Unspecified
                                        },
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.ClearAll,
                                    contentDescription = null,
                                    tint =
                                        if (canClear) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                )
                            },
                            enabled = canClear,
                            onClick = {
                                showMenu = false
                                onClear()
                            },
                        )
                    }
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

@Composable
private fun AppIcon(
    icon: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Tokens.AppIconCornerRadius),
        color = icon?.let { Color.Transparent } ?: MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun AppRow(
    app: AppItem,
    isChecked: Boolean,
    canToggle: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    position: SectionPosition = SectionPosition.Single,
    onNavigateToDetail: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val icon = rememberAppIcon(app.packageName, app.userId)

    SectionCard(
        position = position,
        onClick = if (canToggle && onNavigateToDetail != null) onNavigateToDetail else null,
        onLongClick = { openAppInfo(context, app.packageName) },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Tokens.PreferenceRowHorizontalPadding, vertical = Tokens.PreferenceRowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(icon = icon, modifier = Modifier.size(Tokens.AppIconSize))
            Spacer(modifier = Modifier.width(Tokens.PreferenceHorizontalSpacing))
            Column(modifier = Modifier.weight(1f)) {
                AppLabel(app.label)
                AppPackage(
                    if (app.userId ==
                        0
                    ) {
                        app.packageName
                    } else {
                        "${app.packageName} (${stringResource(R.string.apps_user_id, app.userId)})"
                    },
                )
            }
            if (canToggle && onNavigateToDetail != null) {
                Spacer(modifier = Modifier.width(Tokens.PreferenceRowTrailingSpacing))
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Tokens.CHEVRON_ALPHA),
                    modifier = Modifier.size(Tokens.ChevronIconSize),
                )
                Spacer(modifier = Modifier.width(Tokens.PreferenceRowTrailingSpacing))
                VerticalDivider(
                    modifier = Modifier.height(Tokens.RowDividerHeight),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Spacer(modifier = Modifier.width(Tokens.RowDividerSwitchSpacing))
            } else {
                Spacer(modifier = Modifier.width(Tokens.PreferenceRowTrailingSpacing))
            }
            LockSwitch(
                checked = isChecked,
                onCheckedChange = { if (canToggle) onCheckedChange(it) },
                enabled = canToggle,
            )
        }
    }
}

@Composable
private fun AppLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun AppPackage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ShimmerListItem(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "shimmer_translate",
    )

    val baseColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val shimmerBrush =
        Brush.linearGradient(
            colors = listOf(baseColor, highlightColor, baseColor),
            start = Offset(translateAnim - 500f, 0f),
            end = Offset(translateAnim, 0f),
        )

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(Tokens.PreferencePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(Tokens.AppIconSize)
                    .background(shimmerBrush, RoundedCornerShape(Tokens.AppIconCornerRadius)),
        )
        Spacer(modifier = Modifier.width(Tokens.PreferenceHorizontalSpacing))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(Tokens.SkeletonTitleHeight)
                        .background(shimmerBrush, RoundedCornerShape(Tokens.SmallCornerRadius)),
            )
            Spacer(modifier = Modifier.height(Tokens.SpacingSm))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.8f)
                        .height(Tokens.SkeletonSubtitleHeight)
                        .background(shimmerBrush, RoundedCornerShape(Tokens.SmallCornerRadius)),
            )
        }
        Spacer(modifier = Modifier.width(Tokens.PreferenceHorizontalSpacing))
        Box(
            modifier =
                Modifier
                    .width(Tokens.SwitchTrackWidth)
                    .height(Tokens.SwitchTrackHeight)
                    .background(shimmerBrush, RoundedCornerShape(50)),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppListScreen(
    viewModel: ScopeViewModel,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onNavigateToAppDetail: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val app = App.from(context)

    val scope by viewModel.scope.collectAsStateWithLifecycle()
    val framework by viewModel.framework.collectAsStateWithLifecycle()
    val serviceAvailable = framework != null

    val searchTextFieldState = rememberTextFieldState()
    var debouncedQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val appState = rememberInstalledApps(refreshKey)

    LaunchedEffect(refreshKey) {
        val installed =
            withContext(Dispatchers.IO) {
                val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                val profiles = userManager.userProfiles
                val keys = mutableSetOf<String>()
                for (user in profiles) {
                    val userId = getUserId(user)
                    launcherApps.getActivityList(null, user).forEach { info ->
                        keys.add("${info.applicationInfo.packageName}:$userId")
                    }
                }
                keys
            }
        app.appOverridesRepository.prune(installed)
    }
    val isInitialLoading = appState.isLoading && appState.apps.isEmpty()

    LaunchedEffect(refreshKey, appState.refreshKey, appState.isLoading) {
        if (isRefreshing && appState.refreshKey == refreshKey && !appState.isLoading) {
            isRefreshing = false
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val clearedMessage = stringResource(R.string.apps_snackbar_selection_cleared)
    val undoLabel = stringResource(R.string.apps_snackbar_undo)

    LaunchedEffect(searchTextFieldState) {
        snapshotFlow { searchTextFieldState.text.toString() }.collectLatest { query ->
            delay(Tokens.SEARCH_DEBOUNCE_MS)
            debouncedQuery = query
        }
    }

    val filteredApps =
        remember(appState.apps, debouncedQuery, showSystemApps) {
            appState.apps.filter { app ->
                val matchesSearch =
                    debouncedQuery.isEmpty() ||
                        app.label.contains(
                            debouncedQuery,
                            ignoreCase = true,
                        ) || app.packageName.contains(debouncedQuery, ignoreCase = true)
                val matchesSystem = showSystemApps || !app.isSystem
                matchesSearch && matchesSystem
            }
        }

    val selectableScope =
        remember(appState.apps, scope) {
            val appKeys = appState.apps.mapTo(mutableSetOf()) { it.key }
            scope.filterTo(linkedSetOf()) { it in appKeys }
        }

    // snapshot of scope so toggling a row doesn't reorder the visible list
    // keyed on refreshKey so pull-to-refresh restarts the snapshot
    val scopeAtScreenOpen = remember(refreshKey) { scope }

    val sortedApps =
        remember(filteredApps, scopeAtScreenOpen) {
            filteredApps.sortedWith(
                compareByDescending<AppItem> { it.key in scopeAtScreenOpen }.thenBy { it.label.lowercase() },
            )
        }

    val lazyListState = rememberLazyListState()
    val scrollbarColor = MaterialTheme.colorScheme.onSurfaceVariant
    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (lazyListState.isScrollInProgress) 0.5f else 0f,
        animationSpec = tween(if (lazyListState.isScrollInProgress) 150 else 500),
        label = "scrollbar",
    )
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LargeTopAppBar(
                        title = { ExpandedTitle(stringResource(R.string.tab_apps)) },
                        scrollBehavior = scrollBehavior,
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                scrolledContainerColor = MaterialTheme.colorScheme.surface,
                            ),
                    )
                    AppSearchBar(
                        textFieldState = searchTextFieldState,
                        showSystemApps = showSystemApps,
                        onSystemToggle = { showSystemApps = it },
                        selectedCount = selectableScope.size,
                        canClear = serviceAvailable && selectableScope.isNotEmpty(),
                        onClear = {
                            val previous = selectableScope
                            viewModel.clearScope(selectableScope)
                            coroutineScope.launch {
                                if (snackbarHostState.showSnackbar(
                                        message = clearedMessage,
                                        actionLabel = undoLabel,
                                        duration = SnackbarDuration.Short,
                                    ) == SnackbarResult.ActionPerformed
                                ) {
                                    viewModel.restoreScope(previous)
                                }
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Tokens.ScreenHorizontalPadding)
                                .padding(bottom = Tokens.SpacingSm),
                    )
                }
            }
        },
    ) { innerPadding ->
        val pullState = rememberPullToRefreshState()
        val haptics = LocalHapticFeedback.current
        val currentIsRefreshing by rememberUpdatedState(isRefreshing)
        val refreshReady = pullState.distanceFraction >= 1f
        val refreshIndicatorActive = refreshReady || isRefreshing
        val refreshIndicatorContainerColor =
            if (refreshIndicatorActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                PullToRefreshDefaults.loadingIndicatorContainerColor
            }
        val refreshIndicatorColor =
            if (refreshIndicatorActive) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                PullToRefreshDefaults.loadingIndicatorColor
            }

        LaunchedEffect(pullState) {
            var previousFraction = pullState.distanceFraction
            snapshotFlow { pullState.distanceFraction }.collect { fraction ->
                if (previousFraction < 1f && fraction >= 1f && !currentIsRefreshing) {
                    haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                }
                previousFraction = fraction
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    if (!isRefreshing && !appState.isLoading) {
                        isRefreshing = true
                        refreshKey++
                    }
                },
                state = pullState,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullState,
                        isRefreshing = isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                        containerColor = refreshIndicatorContainerColor,
                        color = refreshIndicatorColor,
                    )
                },
            ) {
                LazyColumn(
                    state = lazyListState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                            .scrollbar(
                                lazyListState,
                                scrollbarColor,
                                scrollbarAlpha,
                                contentPadding.calculateBottomPadding() + Tokens.SpacingLg,
                            ),
                    contentPadding =
                        PaddingValues(
                            top = Tokens.SpacingSm,
                            bottom = contentPadding.calculateBottomPadding() + Tokens.SpacingLg,
                        ),
                ) {
                    when {
                        isInitialLoading -> {
                            items(Tokens.SHIMMER_PLACEHOLDER_COUNT) { index ->
                                val position =
                                    when (index) {
                                        0 -> SectionPosition.Top
                                        Tokens.SHIMMER_PLACEHOLDER_COUNT - 1 -> SectionPosition.Bottom
                                        else -> SectionPosition.Middle
                                    }
                                SectionCard(position = position) {
                                    ShimmerListItem()
                                }
                            }
                        }

                        sortedApps.isEmpty() -> {
                            item(key = "empty") {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(Tokens.SpacingLg),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.apps_empty_no_matches),
                                        modifier = Modifier.padding(vertical = Tokens.EmptyStatePadding),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        else -> {
                            itemsIndexed(
                                items = sortedApps,
                                key = { _, app -> app.key },
                            ) { index, app ->
                                val position =
                                    when {
                                        sortedApps.size == 1 -> SectionPosition.Single
                                        index == 0 -> SectionPosition.Top
                                        index == sortedApps.lastIndex -> SectionPosition.Bottom
                                        else -> SectionPosition.Middle
                                    }
                                AppRow(
                                    app = app,
                                    isChecked = app.key in scope,
                                    canToggle = serviceAvailable,
                                    onCheckedChange = { checked ->
                                        viewModel.toggleScope(app.key, checked)
                                    },
                                    position = position,
                                    onNavigateToDetail = { onNavigateToAppDetail(app.key) },
                                )
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = contentPadding.calculateBottomPadding()),
            )
        }
    }
}

private fun Modifier.scrollbar(
    state: LazyListState,
    color: Color,
    alpha: Float,
    bottomPadding: Dp = 0.dp,
) = drawWithContent {
    drawContent()
    if (alpha == 0f) return@drawWithContent
    val info = state.layoutInfo
    if (info.totalItemsCount == 0 || info.visibleItemsInfo.size >= info.totalItemsCount) return@drawWithContent

    val trackHeight = size.height - bottomPadding.toPx()
    val thumbRatio = info.visibleItemsInfo.size.toFloat() / info.totalItemsCount
    val thumbHeight = (thumbRatio * trackHeight).coerceAtLeast(24.dp.toPx())
    val avgItemHeight =
        info.visibleItemsInfo
            .map { it.size }
            .average()
            .toFloat()
            .coerceAtLeast(1f)
    val scrolled = state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / avgItemHeight
    val maxScroll = (info.totalItemsCount - info.visibleItemsInfo.size).coerceAtLeast(1).toFloat()
    val thumbTop = (scrolled / maxScroll).coerceIn(0f, 1f) * (trackHeight - thumbHeight)
    val width = 4.dp.toPx()

    drawRoundRect(
        color = color.copy(alpha = alpha),
        topLeft = Offset(size.width - width - 2.dp.toPx(), thumbTop),
        size = Size(width, thumbHeight),
        cornerRadius = CornerRadius(width / 2),
    )
}
