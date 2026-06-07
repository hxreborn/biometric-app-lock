@file:OptIn(ExperimentalMaterial3ComponentOverrideApi::class)

package eu.hxreborn.biometricapplock.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3ComponentOverrideApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalNavigationBarOverride
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarOverride
import androidx.compose.material3.NavigationBarOverrideScope
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import kotlinx.coroutines.launch

internal val LocalSelectedIndex: ProvidableCompositionLocal<Int> =
    compositionLocalOf { 0 }

internal val LocalItemCount: ProvidableCompositionLocal<Int> =
    compositionLocalOf { 0 }

private object StretchingPillOverride : NavigationBarOverride {
    @Composable
    override fun NavigationBarOverrideScope.NavigationBar() {
        val selectedIndex = LocalSelectedIndex.current
        val itemCount = LocalItemCount.current.coerceAtLeast(1)
        val motionScheme = MaterialTheme.motionScheme
        val pillColor = MaterialTheme.colorScheme.secondaryContainer

        Surface(
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = tonalElevation,
            modifier = modifier,
        ) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(windowInsets)
                        .height(Tokens.ClassicNavBarHeight),
            ) {
                val totalWidth = maxWidth
                val totalSpacing = Tokens.ClassicNavBarItemSpacing * (itemCount - 1)
                val itemWidth = (totalWidth - totalSpacing) / itemCount

                fun pillStartFor(index: Int): Dp =
                    (itemWidth + Tokens.ClassicNavBarItemSpacing) * index +
                        (itemWidth - Tokens.ClassicNavBarPillWidth) / 2

                fun pillEndFor(index: Int): Dp = pillStartFor(index) + Tokens.ClassicNavBarPillWidth

                val initialIndex = remember { selectedIndex }
                val startEdge =
                    remember { Animatable(pillStartFor(initialIndex), Dp.VectorConverter) }
                val endEdge =
                    remember { Animatable(pillEndFor(initialIndex), Dp.VectorConverter) }

                LaunchedEffect(selectedIndex, itemWidth) {
                    val newStart = pillStartFor(selectedIndex)
                    val newEnd = pillEndFor(selectedIndex)
                    val movingRight = newStart > startEdge.targetValue
                    val fast = motionScheme.fastSpatialSpec<Dp>()
                    val slow = motionScheme.slowSpatialSpec<Dp>()
                    launch { startEdge.animateTo(newStart, if (movingRight) slow else fast) }
                    launch { endEdge.animateTo(newEnd, if (movingRight) fast else slow) }
                }

                Box(
                    modifier =
                        Modifier
                            .offset {
                                IntOffset(
                                    startEdge.value.roundToPx(),
                                    Tokens.ClassicNavBarPillTopOffset.roundToPx(),
                                )
                            }.width((endEdge.value - startEdge.value).coerceAtLeast(0.dp))
                            .height(Tokens.ClassicNavBarPillHeight)
                            .background(
                                color = pillColor,
                                shape = RoundedCornerShape(50),
                            ),
                )

                Row(
                    modifier = Modifier.fillMaxSize().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(Tokens.ClassicNavBarItemSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }
    }
}

@Composable
fun ClassicBottomNav(
    backStack: NavBackStack<NavKey>,
    currentKey: NavKey?,
    modifier: Modifier = Modifier,
    showUpdateBadge: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    val selectedIndex = bottomNavItems.indexOfFirst { it.key == currentKey }.coerceAtLeast(0)
    val transparentIndicatorColors =
        NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)

    CompositionLocalProvider(
        LocalSelectedIndex provides selectedIndex,
        LocalItemCount provides bottomNavItems.size,
        LocalNavigationBarOverride provides StretchingPillOverride,
    ) {
        NavigationBar(modifier = modifier) {
            bottomNavItems.forEach { item ->
                val selected = currentKey == item.key
                NavigationBarItem(
                    selected = selected,
                    onClick =
                        dropUnlessResumed {
                            if (!selected) {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                backStack.clear()
                                backStack.add(item.key)
                            }
                        },
                    icon = {
                        Crossfade(
                            targetState = selected,
                            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            label = "iconCrossfade",
                        ) { isSelected ->
                            val icon = if (isSelected) item.selectedIcon else item.unselectedIcon
                            if (item.key is Screen.Settings && showUpdateBadge) {
                                BadgedBox(badge = { Badge() }) {
                                    Icon(icon, contentDescription = stringResource(item.titleRes))
                                }
                            } else {
                                Icon(icon, contentDescription = stringResource(item.titleRes))
                            }
                        }
                    },
                    label = { Text(stringResource(item.titleRes)) },
                    alwaysShowLabel = false,
                    colors = transparentIndicatorColors,
                )
            }
        }
    }
}
