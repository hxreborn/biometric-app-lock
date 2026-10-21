package eu.hxreborn.biometricapplock.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import eu.hxreborn.biometricapplock.ui.theme.Tokens

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AdaptiveButtonRow(
    labels: List<String>,
    labelStyle: TextStyle,
    nonLabelWidth: Dp,
    modifier: Modifier = Modifier,
    fillRow: Boolean = true,
    content: @Composable (stacked: Boolean, itemModifier: Modifier) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = if (fillRow) ButtonGroupDefaults.ConnectedSpaceBetween else Tokens.SpacingSm
        val widths =
            labels.map { label ->
                with(density) {
                    measurer
                        .measure(label, labelStyle, softWrap = false)
                        .size.width
                        .toDp()
                } + nonLabelWidth
            }
        val fits =
            if (fillRow) {
                val slot = (maxWidth - gap * (labels.size - 1)) / labels.size
                widths.all { it <= slot }
            } else {
                widths.fold(gap * (labels.size - 1)) { total, width -> total + width } <= maxWidth
            }
        if (fits) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.End)) {
                content(false, if (fillRow) Modifier.weight(1f) else Modifier)
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Tokens.SpacingSm)) {
                content(true, Modifier.fillMaxWidth())
            }
        }
    }
}
