@file:OptIn(ExperimentalMaterial3Api::class)

package eu.hxreborn.biometricapplock.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.util.BiometricChoice
import eu.hxreborn.biometricapplock.util.BiometricClass
import eu.hxreborn.biometricapplock.util.choiceAuthenticators
import eu.hxreborn.biometricapplock.util.inferredFaceClass
import eu.hxreborn.biometricapplock.util.sensorSettingName

@Composable
fun biometricChoiceLabel(choice: BiometricChoice?): String =
    stringResource(
        when (choice) {
            null -> R.string.unlock_biometrics_default
            BiometricChoice.ANY -> R.string.unlock_biometrics_any
            BiometricChoice.STRONGEST -> R.string.unlock_biometrics_strongest
            BiometricChoice.NONE -> R.string.unlock_biometrics_none
        },
    )

@Composable
fun BiometricChoiceDialog(
    current: BiometricChoice?,
    allowDefault: Boolean,
    onSelect: (BiometricChoice?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val choices =
        remember(allowDefault) {
            buildList {
                if (allowDefault) add(null)
                addAll(listOf(BiometricChoice.ANY, BiometricChoice.STRONGEST, BiometricChoice.NONE))
            }
        }
    val faceIsWeak = remember { inferredFaceClass(context) == BiometricClass.WEAK }
    val tiersMatch =
        remember {
            sensorSettingName(context, choiceAuthenticators(BiometricChoice.ANY)) ==
                sensorSettingName(context, choiceAuthenticators(BiometricChoice.STRONGEST))
        }
    val summaries =
        mapOf(
            null to stringResource(R.string.unlock_biometrics_default_summary),
            BiometricChoice.ANY to
                stringResource(
                    if (faceIsWeak) {
                        R.string.unlock_biometrics_any_summary_face
                    } else {
                        R.string.unlock_biometrics_any_summary
                    },
                ),
            BiometricChoice.STRONGEST to
                stringResource(
                    when {
                        faceIsWeak -> R.string.unlock_biometrics_strongest_summary_face
                        tiersMatch -> R.string.unlock_biometrics_strongest_summary_same
                        else -> R.string.unlock_biometrics_strongest_summary
                    },
                ),
            BiometricChoice.NONE to stringResource(R.string.unlock_biometrics_none_summary),
        )

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(vertical = Tokens.DialogContentPadding)) {
                Text(
                    text = stringResource(R.string.unlock_biometrics_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = Tokens.DialogContentPadding),
                )
                Spacer(Modifier.height(Tokens.DialogTitleSpacing))
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    choices.forEach { choice ->
                        ChoiceRow(
                            label = biometricChoiceLabel(choice),
                            summary = summaries[choice],
                            selected = current == choice,
                            onSelect = { onSelect(choice) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    summary: String?,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                .padding(
                    horizontal = Tokens.DialogContentPadding,
                    vertical = Tokens.DialogListItemVerticalPadding,
                ),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            modifier =
                Modifier
                    .padding(top = Tokens.RadioTitleTopOffset)
                    .size(Tokens.RadioSize),
        )
        Spacer(Modifier.width(Tokens.DialogTitleSpacing))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            summary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
