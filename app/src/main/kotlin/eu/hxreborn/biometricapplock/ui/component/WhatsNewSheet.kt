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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.updates.ChangeType
import eu.hxreborn.biometricapplock.updates.FailureCause
import eu.hxreborn.biometricapplock.updates.UpdateSheetState
import eu.hxreborn.biometricapplock.updates.titleRes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class FeatureSheetItem(
    val label: String? = null,
    val scope: String? = null,
    val changeType: ChangeType? = null,
    val title: String,
    val body: String? = null,
    val onClick: (() -> Unit)? = null,
)

@Composable
fun WhatsNewSheet(
    state: UpdateSheetState,
    items: List<FeatureSheetItem>,
    versionLabel: String,
    onDismiss: () -> Unit,
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
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        launch { contentAlpha.animateTo(1f, contentAlphaSpec) }
    }

    val dismiss: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        scope.launch {
            sheetState.hide()
            onDismiss()
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
                    is UpdateSheetState.UpToDate,
                    is UpdateSheetState.RateLimited,
                    -> null

                    is UpdateSheetState.Available -> items.size.takeIf { state.notesAvailable && it > 0 }

                    is UpdateSheetState.Failed -> items.size.takeIf { state.cachedFallback != null && it > 0 }

                    UpdateSheetState.WhatsNew -> items.size.takeIf { it > 0 }
                }

            Column(
                modifier =
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .onSizeChanged { size ->
                            contentHeight.value = with(density) { size.height.toDp() }
                        },
                verticalArrangement = Arrangement.spacedBy(Tokens.SheetSectionSpacing),
            ) {
                SheetHeader(versionLabel = versionLabel, title = title, itemCount = itemCount)
                SheetBody(state = state, items = items, sheetTitle = title)
                Spacer(Modifier.height(Tokens.SheetSectionSpacing))
            }

            Column(
                modifier = Modifier.padding(bottom = Tokens.SheetContentPadding),
                verticalArrangement = Arrangement.spacedBy(Tokens.SheetSectionSpacing),
            ) {
                SheetActions(
                    state = state,
                    onDismiss = dismiss,
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
private fun SheetBody(
    state: UpdateSheetState,
    items: List<FeatureSheetItem>,
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
                            (fadeIn(tween(600)) + scaleIn(tween(600), initialScale = 0.6f))
                                .togetherWith(fadeOut(tween(400)) + scaleOut(tween(400), targetScale = 0.6f))
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
                                            imageVector = Icons.Outlined.Download,
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
                if (state is UpdateSheetState.Failed && state.cachedFallback != null && items.isNotEmpty()) {
                    CachedCaption()
                    ItemList(items)
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
                ItemList(items)
            }

            UpdateSheetState.WhatsNew -> {
                ItemList(items)
            }
        }
    }
}

private val CHANGE_TYPE_ORDER =
    listOf(
        ChangeType.Breaking,
        ChangeType.Feat,
        ChangeType.Fix,
        ChangeType.Perf,
        ChangeType.Security,
        ChangeType.Refactor,
        ChangeType.Revert,
        ChangeType.Ci,
        ChangeType.Test,
        ChangeType.Misc,
    )

@Composable
private fun ItemList(items: List<FeatureSheetItem>) {
    if (items.isEmpty()) return
    val grouped = items.groupBy { it.changeType ?: ChangeType.Misc }
    val sections =
        CHANGE_TYPE_ORDER.mapNotNull { type ->
            val group = grouped[type] ?: return@mapNotNull null
            val label = group.first().label ?: return@mapNotNull null
            Triple(type, label, group)
        }
    val shuffledShapes = remember { sectionBadgeShapes.shuffled() }
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.SheetItemSpacing)) {
        sections.forEachIndexed { index, (type, label, group) ->
            TypeSection(
                type = type,
                label = label,
                items = group,
                index = index,
                badgeShape = shuffledShapes[index].toShape(),
            )
        }
    }
}

private val sectionBadgeShapes =
    listOf(
        MaterialShapes.Cookie4Sided,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.PuffyDiamond,
        MaterialShapes.Flower,
        MaterialShapes.Ghostish,
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Gem,
        MaterialShapes.Pill,
        MaterialShapes.Puffy,
        MaterialShapes.ClamShell,
    )

@Composable
private fun TypeSection(
    type: ChangeType,
    label: String,
    items: List<FeatureSheetItem>,
    index: Int = 0,
    badgeShape: Shape = MaterialShapes.Cookie9Sided.toShape(),
) {
    val cs = MaterialTheme.colorScheme
    val cardAlpha = remember { Animatable(0f) }
    val cardOffsetY = remember { Animatable(16f) }
    val iconScale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(index * 60L)
        launch { cardAlpha.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow)) }
        launch { cardOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
        delay(80L)
        iconScale.animateTo(
            1f,
            spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        )
    }

    val labelColor =
        when (type) {
            ChangeType.Breaking, ChangeType.Security -> cs.error
            ChangeType.Fix -> cs.secondary
            else -> cs.primary
        }
    val iconContainerColor =
        when (type) {
            ChangeType.Breaking, ChangeType.Security -> cs.errorContainer
            ChangeType.Feat -> cs.primaryContainer
            ChangeType.Fix -> cs.secondaryContainer
            ChangeType.Perf -> cs.tertiaryContainer
            else -> cs.surfaceContainerLowest
        }
    val iconContentColor =
        when (type) {
            ChangeType.Breaking, ChangeType.Security -> cs.onErrorContainer
            ChangeType.Feat -> cs.onPrimaryContainer
            ChangeType.Fix -> cs.onSecondaryContainer
            ChangeType.Perf -> cs.onTertiaryContainer
            else -> cs.onSurface
        }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = cardAlpha.value
                    translationY = cardOffsetY.value
                },
        shape = MaterialTheme.shapes.large,
        color = cs.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(Tokens.SpacingSm)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Tokens.SpacingXs),
            ) {
                SoftBlobBadge(
                    size = 36.dp,
                    shape = badgeShape,
                    containerColor = iconContainerColor,
                    modifier =
                        Modifier.graphicsLayer {
                            scaleX = iconScale.value
                            scaleY = iconScale.value
                        },
                ) {
                    Icon(
                        imageVector = changeTypeIcon(type),
                        contentDescription = null,
                        tint = iconContentColor,
                        modifier = Modifier.size(Tokens.SmallIconSize),
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = labelColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(Tokens.SpacingXs))
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpacingSm)) {
                items.forEachIndexed { i, item ->
                    if (i > 0) EntryDivider()
                    ChangelogEntryRow(item)
                }
            }
        }
    }
}

@Composable
private fun ChangelogEntryRow(item: FeatureSheetItem) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val titleText =
        buildAnnotatedString {
            append(item.title)
            if (!item.scope.isNullOrBlank()) {
                withStyle(SpanStyle(color = onSurfaceVariant)) {
                    append(" (${item.scope})")
                }
            }
        }
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.SpacingXs),
        verticalArrangement = Arrangement.spacedBy(Tokens.SpacingXs),
    ) {
        Text(
            text = titleText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier =
                if (item.onClick != null) {
                    Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            item.onClick.invoke()
                        },
                    )
                } else {
                    Modifier
                },
        )
        if (!item.body.isNullOrBlank()) {
            Text(
                text = item.body,
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EntryDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
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

        is UpdateSheetState.UpToDate,
        UpdateSheetState.WhatsNew,
        -> {
            PrimaryAction(
                label = stringResource(R.string.whats_new_got_it),
                onClick = onDismiss,
            )
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
            Modifier
                .fillMaxWidth()
                .height(Tokens.PrimaryActionHeight)
                .graphicsLayer {
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
