package eu.hxreborn.biometricapplock.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.ui.theme.Tokens

private val relockDelayPresets =
    listOf(
        0 to R.string.app_detail_relock_delay_immediate,
        30 to R.string.app_detail_relock_delay_30s,
        60 to R.string.app_detail_relock_delay_1m,
        180 to R.string.app_detail_relock_delay_3m,
        300 to R.string.app_detail_relock_delay_5m,
        600 to R.string.app_detail_relock_delay_10m,
        1800 to R.string.app_detail_relock_delay_30m,
        -1 to R.string.app_detail_relock_delay_never,
    )

@Composable
fun relockDelaySummary(seconds: Int): String =
    relockDelayPresets.firstOrNull { it.first == seconds }?.let { stringResource(it.second) }
        ?: if (seconds % 60 == 0) {
            stringResource(R.string.app_detail_relock_delay_after_minutes, seconds / 60)
        } else {
            stringResource(R.string.app_detail_relock_delay_after_seconds, seconds)
        }

@Composable
fun RelockDelayDialog(
    currentSeconds: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val isPreset = relockDelayPresets.any { it.first == currentSeconds }
    var customPicked by remember { mutableStateOf(!isPreset) }
    var customMinutes by remember {
        mutableStateOf(if (isPreset) "" else (currentSeconds / 60).coerceAtLeast(1).toString())
    }
    val customSeconds = customMinutes.toIntOrNull()?.takeIf { it > 0 }?.times(60)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_detail_relock_delay_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                relockDelayPresets.forEach { (seconds, labelRes) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(seconds) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = !customPicked && currentSeconds == seconds,
                            onClick = { onSelect(seconds) },
                        )
                        Spacer(Modifier.width(Tokens.SpacingSm))
                        Text(stringResource(labelRes))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { customPicked = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = customPicked, onClick = { customPicked = true })
                    Spacer(Modifier.width(Tokens.SpacingSm))
                    Text(stringResource(R.string.app_detail_relock_delay_custom))
                }
                if (customPicked) {
                    OutlinedTextField(
                        value = customMinutes,
                        onValueChange = { customMinutes = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.app_detail_relock_delay_custom_minutes)) },
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                        keyboardActions = KeyboardActions(onDone = { customSeconds?.let(onSelect) }),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = Tokens.SpacingSm),
                    )
                }
            }
        },
        confirmButton = {
            if (customPicked) {
                TextButton(
                    onClick = { customSeconds?.let(onSelect) },
                    enabled = customSeconds != null,
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
