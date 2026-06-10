@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package eu.hxreborn.biometricapplock.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import kotlinx.coroutines.launch

// Save runs SAF and Send opens the share sheet, both returning false to keep the sheet open on failure
@Composable
fun LogActionsSheet(
    onSave: suspend () -> Boolean,
    onSend: suspend () -> Boolean,
    onDismiss: () -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    val scope = rememberCoroutineScope()
    var collecting by remember { mutableStateOf(false) }

    val run: (suspend () -> Boolean) -> Unit = { action ->
        collecting = true
        scope.launch {
            if (action()) {
                sheetState.hide()
                onDismiss()
            } else {
                collecting = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!collecting) onDismiss() },
        sheetState = sheetState,
        sheetGesturesEnabled = !collecting,
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth().padding(
                    horizontal = Tokens.SheetContentPadding,
                    vertical = Tokens.SheetSectionSpacing,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = collecting,
                contentAlignment = Alignment.Center,
                transitionSpec = {
                    val animation = spring<Float>(stiffness = Spring.StiffnessLow)
                    (fadeIn(animation) + scaleIn(animation, initialScale = 0.85f))
                        .togetherWith(
                            fadeOut(animation) +
                                scaleOut(
                                    animation,
                                    targetScale = 0.85f,
                                ),
                        ).using(SizeTransform(clip = false) { _, _ -> spring(stiffness = Spring.StiffnessMediumLow) })
                },
                label = "logActions",
            ) { busy ->
                if (busy) {
                    LoadingIndicator(modifier = Modifier.size(Tokens.LogActionButtonSize))
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Tokens.LogActionSpacing, Alignment.CenterHorizontally),
                    ) {
                        LogAction(
                            icon = Icons.Outlined.Save,
                            label = stringResource(R.string.logs_sheet_save),
                            onClick = { run(onSave) },
                        )
                        LogAction(
                            icon = Icons.Outlined.Share,
                            label = stringResource(R.string.logs_sheet_send),
                            onClick = { run(onSend) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Tokens.SpacingSm),
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(Tokens.LogActionButtonSize),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(Tokens.SettingsIconSize),
            )
        }
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}
