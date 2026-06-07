package eu.hxreborn.biometricapplock.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import eu.hxreborn.biometricapplock.App
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.ui.screen.AboutScreen
import eu.hxreborn.biometricapplock.ui.screen.AllowedActivitiesScreen
import eu.hxreborn.biometricapplock.ui.screen.AppDetailScreen
import eu.hxreborn.biometricapplock.ui.screen.AppListScreen
import eu.hxreborn.biometricapplock.ui.screen.DashboardScreen
import eu.hxreborn.biometricapplock.ui.screen.settings.SettingsScreen
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.ui.viewmodel.ScopeViewModel
import kotlinx.serialization.Serializable
import soup.compose.material.motion.animation.materialSharedAxisXIn
import soup.compose.material.motion.animation.materialSharedAxisXOut

sealed interface Screen : NavKey {
    @Serializable
    data object Dashboard : Screen

    @Serializable
    data object Apps : Screen

    @Serializable
    data object Settings : Screen

    @Serializable
    data object About : Screen

    @Serializable
    data class AppDetail(
        val packageKey: String,
    ) : Screen

    @Serializable
    data class AllowedActivities(
        val packageKey: String,
    ) : Screen
}

data class BottomNavItem(
    val key: Screen,
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private fun tabIndexOf(contentKey: Any?): Int = bottomNavItems.indexOfFirst { it.key.toString() == contentKey }

private fun isForwardTabTransition(
    initial: Scene<NavKey>,
    target: Scene<NavKey>,
): Boolean {
    val initialIdx = tabIndexOf(initial.entries.lastOrNull()?.contentKey)
    val targetIdx = tabIndexOf(target.entries.lastOrNull()?.contentKey)
    return if (initialIdx >= 0 && targetIdx >= 0) targetIdx >= initialIdx else true
}

val bottomNavItems =
    listOf(
        BottomNavItem(
            key = Screen.Dashboard,
            titleRes = R.string.tab_dashboard,
            selectedIcon = Icons.Rounded.Home,
            unselectedIcon = Icons.Outlined.Home,
        ),
        BottomNavItem(
            key = Screen.Apps,
            titleRes = R.string.tab_apps,
            selectedIcon = Icons.Rounded.Apps,
            unselectedIcon = Icons.Outlined.Apps,
        ),
        BottomNavItem(
            key = Screen.Settings,
            titleRes = R.string.tab_settings,
            selectedIcon = Icons.Rounded.Settings,
            unselectedIcon = Icons.Outlined.Settings,
        ),
    )

@Composable
fun MainNavDisplay(
    backStack: NavBackStack<NavKey>,
    viewModel: ScopeViewModel,
    contentPadding: PaddingValues,
    onShowUpdateSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = App.from(LocalContext.current)
    val slideInDistance = with(LocalDensity.current) { Tokens.NavSlideDistance.roundToPx() }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            app.updateRepository.maybeAutoCheck()
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        modifier = modifier,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        transitionSpec = {
            val forward = isForwardTabTransition(initialState, targetState)
            materialSharedAxisXIn(forward = forward, slideDistance = slideInDistance) togetherWith
                materialSharedAxisXOut(
                    forward = forward,
                    slideDistance = slideInDistance,
                )
        },
        popTransitionSpec = {
            materialSharedAxisXIn(forward = false, slideDistance = slideInDistance) togetherWith
                materialSharedAxisXOut(
                    forward = false,
                    slideDistance = slideInDistance,
                )
        },
        predictivePopTransitionSpec = {
            materialSharedAxisXIn(forward = false, slideDistance = slideInDistance) togetherWith
                materialSharedAxisXOut(
                    forward = false,
                    slideDistance = slideInDistance,
                )
        },
        entryProvider =
            entryProvider {
                entry<Screen.Dashboard> {
                    DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToApps =
                            dropUnlessResumed {
                                backStack.clear()
                                backStack.add(Screen.Apps)
                            },
                        onNavigateToAppDetail = { pkg -> backStack.add(Screen.AppDetail(pkg)) },
                        contentPadding = contentPadding,
                    )
                }
                entry<Screen.Apps> {
                    AppListScreen(
                        viewModel = viewModel,
                        contentPadding = contentPadding,
                        onNavigateToAppDetail = { pkg -> backStack.add(Screen.AppDetail(pkg)) },
                    )
                }
                entry<Screen.AppDetail> { appDetail ->
                    AppDetailScreen(
                        packageKey = appDetail.packageKey,
                        onBack = dropUnlessResumed { backStack.removeLastOrNull() },
                        contentPadding = contentPadding,
                        onNavigateToAllowedActivities = { key ->
                            backStack.add(Screen.AllowedActivities(key))
                        },
                    )
                }
                entry<Screen.AllowedActivities> { key ->
                    AllowedActivitiesScreen(
                        packageKey = key.packageKey,
                        onBack = dropUnlessResumed { backStack.removeLastOrNull() },
                        contentPadding = contentPadding,
                    )
                }
                entry<Screen.Settings> {
                    val framework by viewModel.framework.collectAsStateWithLifecycle()
                    val rootGranted by viewModel.rootGranted.collectAsStateWithLifecycle()
                    SettingsScreen(
                        framework = framework,
                        rootGranted = rootGranted,
                        onNavigateToAbout = dropUnlessResumed { backStack.add(Screen.About) },
                        onShowUpdateSheet = onShowUpdateSheet,
                        contentPadding = contentPadding,
                    )
                }
                entry<Screen.About> {
                    val framework by viewModel.framework.collectAsStateWithLifecycle()
                    AboutScreen(
                        framework = framework,
                        onBack = dropUnlessResumed { backStack.removeLastOrNull() },
                        contentPadding = contentPadding,
                    )
                }
            },
    )
}

@OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
)
@Composable
fun BottomNav(
    backStack: NavBackStack<NavKey>,
    currentKey: NavKey?,
    modifier: Modifier = Modifier,
    showUpdateBadge: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    val buttonBounds = remember { mutableStateMapOf<Int, Rect>() }
    val currentIndex = bottomNavItems.indexOfFirst { it.key == currentKey }.coerceAtLeast(0)
    val targetRect = buttonBounds[currentIndex]
    val anchorRect = buttonBounds[0]
    val pillTargetX = (targetRect?.left ?: 0f) - (anchorRect?.left ?: 0f)
    val pillTargetWidth = targetRect?.width ?: 0f

    val pillAnimatedX by animateFloatAsState(
        targetValue = pillTargetX,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "navPillX",
    )
    val pillAnimatedWidth by animateFloatAsState(
        targetValue = pillTargetWidth,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "navPillWidth",
    )
    val pillColor = MaterialTheme.colorScheme.primary

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = Tokens.FloatingBarBottomPadding),
        contentAlignment = Alignment.BottomCenter,
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            colors =
                FloatingToolbarDefaults.vibrantFloatingToolbarColors(
                    toolbarContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    toolbarContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
        ) {
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                bottomNavItems.forEachIndexed { index, item ->
                    val selected = currentKey == item.key
                    val onSelectTab =
                        dropUnlessResumed {
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            backStack.clear()
                            backStack.add(item.key)
                        }

                    ToggleButton(
                        checked = selected,
                        onCheckedChange = { if (!selected) onSelectTab() },
                        modifier =
                            Modifier
                                .height(Tokens.FloatingBarItemHeight)
                                .onGloballyPositioned { coords ->
                                    buttonBounds[index] = coords.boundsInParent()
                                }.then(
                                    if (index == 0) {
                                        Modifier.drawWithContent {
                                            if (pillAnimatedWidth > 0f) {
                                                drawRoundRect(
                                                    color = pillColor,
                                                    topLeft = Offset(pillAnimatedX, 0f),
                                                    size = Size(pillAnimatedWidth, size.height),
                                                    cornerRadius = CornerRadius(size.height / 2f),
                                                )
                                            }
                                            drawContent()
                                        }
                                    } else {
                                        Modifier
                                    },
                                ),
                        colors =
                            ToggleButtonDefaults.toggleButtonColors(
                                containerColor = Color.Transparent,
                                checkedContainerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        shapes =
                            ToggleButtonDefaults.shapes(
                                shape = CircleShape,
                                pressedShape = CircleShape,
                                checkedShape = CircleShape,
                            ),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isSettingsTab = item.key is Screen.Settings
                            if (isSettingsTab && showUpdateBadge) {
                                BadgedBox(badge = { Badge() }) {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.titleRes),
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = stringResource(item.titleRes),
                                )
                            }
                            AnimatedVisibility(
                                visible = selected,
                                enter = expandHorizontally(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
                                exit = shrinkHorizontally(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
                            ) {
                                Text(
                                    text = stringResource(item.titleRes),
                                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                                    style =
                                        MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
