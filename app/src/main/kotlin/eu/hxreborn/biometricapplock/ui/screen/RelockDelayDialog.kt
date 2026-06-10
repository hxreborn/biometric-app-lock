package eu.hxreborn.biometricapplock.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.ui.theme.Tokens

@Composable
fun relockDelaySummary(seconds: Int): String =
    when (seconds) {
        -1 -> stringResource(R.string.app_detail_relock_delay_never)
        0 -> stringResource(R.string.app_detail_relock_delay_immediate)
        30 -> stringResource(R.string.app_detail_relock_delay_30s)
        60 -> stringResource(R.string.app_detail_relock_delay_1m)
        300 -> stringResource(R.string.app_detail_relock_delay_5m)
        1800 -> stringResource(R.string.app_detail_relock_delay_30m)
        else -> "$seconds s"
    }

@Composable
fun RelockDelayDialog(
    currentSeconds: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val options =
        remember {
            listOf(
                0 to R.string.app_detail_relock_delay_immediate,
                30 to R.string.app_detail_relock_delay_30s,
                60 to R.string.app_detail_relock_delay_1m,
                300 to R.string.app_detail_relock_delay_5m,
                1800 to R.string.app_detail_relock_delay_30m,
                -1 to R.string.app_detail_relock_delay_never,
            )
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_detail_relock_delay_title)) },
        text = {
            Column {
                options.forEach { (seconds, labelRes) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(seconds) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = currentSeconds == seconds,
                            onClick = { onSelect(seconds) },
                        )
                        Spacer(Modifier.width(Tokens.SpacingSm))
                        Text(stringResource(labelRes))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
