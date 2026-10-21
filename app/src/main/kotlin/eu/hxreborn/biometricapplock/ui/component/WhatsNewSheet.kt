@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package eu.hxreborn.biometricapplock.ui.component

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.updates.ChangelogEntry
import eu.hxreborn.biometricapplock.updates.FailureCause
import eu.hxreborn.biometricapplock.updates.UpdateSheetState
import eu.hxreborn.biometricapplock.updates.titleRes
import kotlinx.coroutines.launch

@Composable
fun WhatsNewSheet(
    state: UpdateSheetState,
    entries: List<ChangelogEntry>,
    versionLabel: String,
    onDismiss: () -> Unit,
    onFullChangelog: () -> Unit = {},
    onDownload: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onLater: (String) -> Unit = {},
) {
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    val haptics = LocalHapticFeedback.current
    val title = stringResource(state.titleRes)

    val scope = rememberCoroutineScope()
    val contentAlpha = remember { Animatable(0f) }
    val contentAlphaSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val density = LocalDensity.current
    val screenHeight =
        with(density) {
            LocalWindowInfo.current.containerSize.height
                .toDp()
        }
    val contentHeight = remember { mutableStateOf(0.dp) }

    LaunchedEffect(Unit) {
        contentAlpha.animateTo(1f, contentAlphaSpec)
    }

    var sawChecking by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        when (state) {
            UpdateSheetState.Checking -> {
                sawChecking = true
            }

            is UpdateSheetState.UpToDate, is UpdateSheetState.Available -> {
                if (sawChecking) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                sawChecking = false
            }

            is UpdateSheetState.Failed, is UpdateSheetState.RateLimited -> {
                if (sawChecking) haptics.performHapticFeedback(HapticFeedbackType.Reject)
                sawChecking = false
            }

            UpdateSheetState.WhatsNew -> {}
        }
    }

    val dismiss: () -> Unit = {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    val openFullChangelog: () -> Unit = {
        scope.launch {
            sheetState.hide()
            onFullChangelog()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = contentHeight.value < screenHeight / 2,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Tokens.SheetContentPadding)
                    .graphicsLayer { alpha = contentAlpha.value },
        ) {
            val itemCount =
                when (state) {
                    UpdateSheetState.Checking,
                    UpdateSheetState.WhatsNew,
                    is UpdateSheetState.UpToDate,
                    is UpdateSheetState.RateLimited,
                    -> null

                    is UpdateSheetState.Available -> entries.size.takeIf { state.notesAvailable && it > 0 }

                    is UpdateSheetState.Failed -> entries.size.takeIf { state.cachedFallback != null && it > 0 }
                }

            Column(
                modifier =
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).onSizeChanged { size ->
                        contentHeight.value = with(density) { size.height.toDp() }
                    },
                verticalArrangement = Arrangement.spacedBy(Tokens.SheetSectionSpacing),
            ) {
                if (state == UpdateSheetState.WhatsNew) {
                    WhatsNewHeader(versionLabel = versionLabel, releasedOn = releaseDate(entries))
                } else {
                    SheetHeader(versionLabel = versionLabel, title = title, itemCount = itemCount)
                }
                SheetBody(state = state, entries = entries, sheetTitle = title)
                Spacer(Modifier.height(Tokens.SheetSectionSpacing))
            }

            Column(
                modifier = Modifier.padding(bottom = Tokens.SheetContentPadding),
                verticalArrangement = Arrangement.spacedBy(Tokens.SheetSectionSpacing),
            ) {
                SheetActions(
                    state = state,
                    onDismiss = dismiss,
                    onFullChangelog = openFullChangelog,
                    onDownload = onDownload,
                    onLater = onLater,
                    onRetry = onRetry,
                )
            }
        }
    }
}

@Composable
private fun SheetHeader(
    versionLabel: String,
    title: String,
    itemCount: Int? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.SheetItemSpacing),
        modifier = Modifier.fillMaxWidth().semantics { heading() },
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                text = "v$versionLabel",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier.padding(
                        horizontal = Tokens.SpacingSm,
                        vertical = Tokens.SpacingXs,
                    ),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (itemCount != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text = pluralStringResource(R.plurals.updates_sheet_change_count, itemCount, itemCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = Tokens.SpacingSm, vertical = Tokens.SpacingXs),
                )
            }
        }
    }
}

@Composable
private fun WhatsNewHeader(
    versionLabel: String,
    releasedOn: String?,
) {
    Column(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() }) {
        Text(
            text = stringResource(R.string.whats_new_title, versionLabel),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        if (releasedOn != null) {
            Text(
                text = stringResource(R.string.whats_new_released, releasedOn),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SheetBody(
    state: UpdateSheetState,
    entries: List<ChangelogEntry>,
    sheetTitle: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Tokens.SheetItemSpacing),
    ) {
        when (state) {
            UpdateSheetState.Checking,
            is UpdateSheetState.UpToDate,
            is UpdateSheetState.Failed,
            is UpdateSheetState.RateLimited,
            -> {
                val resolved = state !is UpdateSheetState.Checking
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.EmptyStatePadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Tokens.SheetSectionSpacing),
                ) {
                    AnimatedContent(
                        targetState = state,
                        contentKey = { it::class },
                        transitionSpec = {
                            (fadeIn(tween(600)) + scaleIn(tween(600), initialScale = 0.6f)).togetherWith(
                                fadeOut(tween(400)) +
                                    scaleOut(
                                        tween(400),
                                        targetScale = 0.6f,
                                    ),
                            )
                        },
                        label = "updateBadge",
                    ) { target ->
                        Box(
                            modifier = Modifier.size(Tokens.UpToDateBadgeSize),
                            contentAlignment = Alignment.Center,
                        ) {
                            when (target) {
                                UpdateSheetState.Checking -> {
                                    LoadingIndicator(modifier = Modifier.size(Tokens.UpToDateBadgeSize))
                                }

                                is UpdateSheetState.UpToDate -> {
                                    SoftBlobBadge(
                                        size = Tokens.UpToDateBadgeSize,
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(Tokens.UpToDateBadgeIconSize),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }

                                is UpdateSheetState.Failed -> {
                                    val icon =
                                        when (target.cause) {
                                            FailureCause.Offline, FailureCause.ServiceUnavailable -> {
                                                Icons.Outlined.CloudOff
                                            }

                                            FailureCause.Network, FailureCause.Parse -> {
                                                Icons.Outlined.ErrorOutline
                                            }
                                        }
                                    SoftBlobBadge(
                                        size = Tokens.UpToDateBadgeSize,
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(Tokens.UpToDateBadgeIconSize),
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                    }
                                }

                                is UpdateSheetState.RateLimited -> {
                                    SoftBlobBadge(
                                        size = Tokens.UpToDateBadgeSize,
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.HourglassEmpty,
                                            contentDescription = null,
                                            modifier = Modifier.size(Tokens.UpToDateBadgeIconSize),
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        )
                                    }
                                }

                                else -> {}
                            }
                        }
                    }
                    AnimatedContent(
                        targetState = state,
                        contentKey = { it::class },
                        transitionSpec = {
                            fadeIn(tween(400)).togetherWith(fadeOut(tween(300)))
                        },
                        label = "statusText",
                    ) { target ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Tokens.SpacingSm),
                        ) {
                            val text =
                                when (target) {
                                    UpdateSheetState.Checking -> {
                                        ""
                                    }

                                    is UpdateSheetState.UpToDate -> {
                                        stringResource(R.string.updates_sheet_up_to_date_callout)
                                    }

                                    is UpdateSheetState.Failed -> {
                                        when (target.cause) {
                                            FailureCause.Offline -> {
                                                stringResource(R.string.updates_dialog_failed_offline)
                                            }

                                            FailureCause.ServiceUnavailable -> {
                                                stringResource(R.string.updates_dialog_failed_service_unavailable)
                                            }

                                            FailureCause.Network, FailureCause.Parse -> {
                                                stringResource(R.string.updates_dialog_failed_network)
                                            }
                                        }
                                    }

                                    is UpdateSheetState.RateLimited -> {
                                        if (target.resetAtEpochMs != null) {
                                            stringResource(
                                                R.string.updates_dialog_rate_limited_body,
                                                DateUtils
                                                    .getRelativeTimeSpanString(
                                                        target.resetAtEpochMs,
                                                        System.currentTimeMillis(),
                                                        DateUtils.MINUTE_IN_MILLIS,
                                                    ).toString(),
                                            )
                                        } else {
                                            stringResource(R.string.updates_dialog_rate_limited_body_unknown)
                                        }
                                    }

                                    else -> {
                                        ""
                                    }
                                }
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyLarge,
                                color =
                                    if (resolved) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        Color.Transparent
                                    },
                                textAlign = TextAlign.Center,
                            )
                            if (target is UpdateSheetState.RateLimited) {
                                Text(
                                    text = stringResource(R.string.updates_rate_limit_context),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
                if (state is UpdateSheetState.Failed && state.cachedFallback != null && entries.isNotEmpty()) {
                    CachedCaption()
                    ReleaseNotes(entries)
                }
            }

            is UpdateSheetState.Available -> {
                Text(
                    text = stringResource(R.string.updates_sheet_version_delta, state.currentVersion, state.latestVersion),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                if (!state.notesAvailable) {
                    Callout(
                        icon = Icons.Outlined.Info,
                        contentDescription = sheetTitle,
                        text = stringResource(R.string.updates_sheet_notes_unavailable),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
                ReleaseNotes(entries)
            }

            UpdateSheetState.WhatsNew -> {
                ReleaseNotes(entries)
            }
        }
    }
}

@Composable
private fun CachedCaption() {
    Text(
        text = stringResource(R.string.updates_sheet_cached_caption),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Callout(
    icon: ImageVector,
    contentDescription: String,
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(Tokens.PreferencePadding),
            horizontalArrangement = Arrangement.spacedBy(Tokens.PreferenceHorizontalSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(Tokens.SettingsIconSize),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun SheetActions(
    state: UpdateSheetState,
    onDismiss: () -> Unit,
    onFullChangelog: () -> Unit,
    onDownload: (String) -> Unit,
    onLater: (String) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        UpdateSheetState.Checking -> {
            PrimaryAction(
                label = stringResource(R.string.whats_new_got_it),
                onClick = onDismiss,
                enabled = false,
            )
        }

        is UpdateSheetState.Available -> {
            PrimaryAction(
                label = stringResource(R.string.updates_sheet_download),
                onClick = {
                    onDownload(state.releaseUrl)
                    onDismiss()
                },
            )
            SecondaryAction(
                label = stringResource(R.string.updates_sheet_skip_version),
                onClick = {
                    onLater(state.latestVersion)
                    onDismiss()
                },
            )
        }

        is UpdateSheetState.Failed -> {
            PrimaryAction(
                label = stringResource(R.string.updates_dialog_retry),
                onClick = onRetry,
            )
            SecondaryAction(
                label = stringResource(R.string.updates_dialog_close),
                onClick = onDismiss,
            )
        }

        is UpdateSheetState.RateLimited -> {
            SecondaryAction(
                label = stringResource(R.string.updates_dialog_close),
                onClick = onDismiss,
            )
        }

        is UpdateSheetState.UpToDate -> {
            PrimaryAction(
                label = stringResource(R.string.whats_new_got_it),
                onClick = onDismiss,
            )
        }

        UpdateSheetState.WhatsNew -> {
            val close = stringResource(R.string.updates_dialog_close)
            val seeFull = stringResource(R.string.changelog_see_full)
            AdaptiveButtonRow(
                labels = listOf(close, seeFull),
                labelStyle = MaterialTheme.typography.labelLarge,
                nonLabelWidth =
                    ButtonDefaults.ContentPadding.calculateLeftPadding(LayoutDirection.Ltr) +
                        ButtonDefaults.ContentPadding.calculateRightPadding(LayoutDirection.Ltr) +
                        ButtonDefaults.IconSize +
                        ButtonDefaults.IconSpacing,
                fillRow = false,
            ) { _, itemModifier ->
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes(), modifier = itemModifier) {
                    Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(close)
                }
                FilledTonalButton(onClick = onFullChangelog, shapes = ButtonDefaults.shapes(), modifier = itemModifier) {
                    Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(seeFull)
                }
            }
        }
    }
}

@Composable
private fun PrimaryAction(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "buttonScale",
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier =
            Modifier.fillMaxWidth().height(Tokens.PrimaryActionHeight).graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = CircleShape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SecondaryAction(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(Tokens.PrimaryActionHeight),
        shape = CircleShape,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
