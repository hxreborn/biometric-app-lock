package eu.hxreborn.biometricapplock.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.ui.theme.Tokens

enum class HomeStatus { NotEnabled, RebootRequired, NoApps, NoUnlockMethod, Locking }

private enum class StatusTone { Healthy, Setup, Broken }

private val HomeStatus.tone: StatusTone
    get() =
        when (this) {
            HomeStatus.Locking -> StatusTone.Healthy
            HomeStatus.NoApps -> StatusTone.Setup
            HomeStatus.NotEnabled, HomeStatus.RebootRequired, HomeStatus.NoUnlockMethod -> StatusTone.Broken
        }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusCard(
    status: HomeStatus,
    frameworkLabel: String?,
    onChooseApps: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        if (status.tone == StatusTone.Broken) colors.errorContainer else colors.surfaceContainerHigh,
        label = "statusContainer",
    )
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.SectionHorizontalMargin),
        shape = RoundedCornerShape(Tokens.GroupedShapeCornerLarge),
        color = containerColor,
    ) {
        AnimatedContent(
            targetState = status,
            transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
            label = "statusContent",
        ) { shown ->
            val broken = shown.tone == StatusTone.Broken
            Column(
                modifier = Modifier.padding(Tokens.PreferencePadding),
                verticalArrangement = Arrangement.spacedBy(Tokens.SpacingLg),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Tokens.PreferenceHorizontalSpacing),
                ) {
                    StatusBadge(shown)
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    ) {
                        Text(
                            text = statusTitle(shown),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (broken) colors.onErrorContainer else colors.onSurface,
                        )
                        statusSummary(shown, frameworkLabel)?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (broken) colors.onErrorContainer else colors.onSurfaceVariant,
                            )
                        }
                    }
                }
                when (shown) {
                    HomeStatus.NoApps -> {
                        StatusActionButton(stringResource(R.string.status_action_choose_apps), broken = false, onChooseApps)
                    }

                    HomeStatus.NoUnlockMethod -> {
                        StatusActionButton(stringResource(R.string.status_action_security_settings), broken = true, onOpenSecuritySettings)
                    }

                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: HomeStatus) {
    val colors = MaterialTheme.colorScheme
    when (status.tone) {
        StatusTone.Healthy -> {
            MorphingBadge(size = Tokens.StatusBadgeSize, color = colors.primary) {
                BadgeIcon(Icons.Rounded.Check, colors.onPrimary)
            }
        }

        StatusTone.Setup -> {
            StaticBadge(colors.secondaryContainer) { BadgeIcon(Icons.Rounded.Add, colors.onSecondaryContainer) }
        }

        StatusTone.Broken -> {
            StaticBadge(colors.error) { BadgeIcon(Icons.Rounded.PriorityHigh, colors.onError) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StaticBadge(
    color: Color,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.size(Tokens.StatusBadgeSize).background(color = color, shape = MaterialShapes.Sunny.toShape()),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun BadgeIcon(
    imageVector: ImageVector,
    tint: Color,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(Tokens.StatusIconSize),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusActionButton(
    text: String,
    broken: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        colors =
            if (broken) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                )
            } else {
                ButtonDefaults.buttonColors()
            },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text)
    }
}

@Composable
private fun statusTitle(status: HomeStatus): String =
    stringResource(
        when (status) {
            HomeStatus.Locking -> R.string.status_running
            HomeStatus.NoApps -> R.string.status_locked_empty
            HomeStatus.NoUnlockMethod -> R.string.status_no_unlock_method
            HomeStatus.RebootRequired -> R.string.status_reboot_required
            HomeStatus.NotEnabled -> R.string.status_inactive
        },
    )

@Composable
private fun statusSummary(
    status: HomeStatus,
    frameworkLabel: String?,
): String? =
    when (status) {
        HomeStatus.Locking -> frameworkLabel
        HomeStatus.NoApps -> stringResource(R.string.status_locked_empty_action)
        HomeStatus.NoUnlockMethod -> stringResource(R.string.status_no_unlock_method_action)
        HomeStatus.RebootRequired -> stringResource(R.string.status_reboot_required_reason)
        HomeStatus.NotEnabled -> stringResource(R.string.status_inactive_action)
    }
