package eu.hxreborn.biometricapplock.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.util.BiometricChoice
import eu.hxreborn.biometricapplock.util.BiometricClass
import eu.hxreborn.biometricapplock.util.choiceAuthenticators
import eu.hxreborn.biometricapplock.util.inferredFaceClass
import eu.hxreborn.biometricapplock.util.sensorSettingName

val biometricChoices = listOf(BiometricChoice.ANY, BiometricChoice.STRONGEST, BiometricChoice.NONE)

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
fun biometricChoiceSummary(choice: BiometricChoice): String {
    val context = LocalContext.current
    val faceIsWeak = remember { inferredFaceClass(context) == BiometricClass.WEAK }
    val tiersMatch =
        remember {
            sensorSettingName(context, choiceAuthenticators(BiometricChoice.ANY)) ==
                sensorSettingName(context, choiceAuthenticators(BiometricChoice.STRONGEST))
        }
    return stringResource(
        when (choice) {
            BiometricChoice.ANY -> {
                if (faceIsWeak) R.string.unlock_biometrics_any_summary_face else R.string.unlock_biometrics_any_summary
            }

            BiometricChoice.STRONGEST -> {
                when {
                    faceIsWeak -> R.string.unlock_biometrics_strongest_summary_face
                    tiersMatch -> R.string.unlock_biometrics_strongest_summary_same
                    else -> R.string.unlock_biometrics_strongest_summary
                }
            }

            BiometricChoice.NONE -> {
                R.string.unlock_biometrics_none_summary
            }
        },
    )
}
