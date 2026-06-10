package eu.hxreborn.biometricapplock.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import eu.hxreborn.biometricapplock.ui.component.SectionCard
import eu.hxreborn.biometricapplock.ui.component.SectionPosition
import eu.hxreborn.biometricapplock.ui.theme.Tokens

@Composable
fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier =
            modifier.padding(
                start = Tokens.SectionHeaderStartPadding,
                top = Tokens.SectionHeaderTopPadding,
                bottom = Tokens.SectionHeaderBottomPadding,
            ),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun PreferenceRow(
    icon: ImageVector,
    title: String,
    summary: String?,
    modifier: Modifier = Modifier,
    position: SectionPosition = SectionPosition.Single,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) = PreferenceRow(rememberVectorPainter(icon), title, summary, modifier, position, enabled, onClick, trailing)

@Composable
fun PreferenceRow(
    icon: Painter,
    title: String,
    summary: String?,
    modifier: Modifier = Modifier,
    position: SectionPosition = SectionPosition.Single,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    SectionCard(modifier = modifier, position = position) {
        PreferenceRowContent(
            icon = icon,
            title = title,
            summary = summary,
            enabled = enabled,
            onClick = onClick,
            trailing = trailing,
        )
    }
}

@Composable
fun PreferenceRowContent(
    icon: Painter,
    title: String,
    summary: String?,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val rowModifier =
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Tokens.PreferenceRowMinHeight)
            .alpha(if (enabled) 1f else 0.38f)
            .then(if (enabled && onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (!enabled) Modifier.semantics(mergeDescendants = true) { disabled() } else Modifier)
            .padding(
                horizontal = Tokens.PreferenceRowHorizontalPadding,
                vertical = Tokens.PreferenceRowVerticalPadding,
            )

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Tokens.SettingsIconSize),
        )
        Spacer(Modifier.width(Tokens.PreferenceRowIconTextSpacing))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!summary.isNullOrEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Tokens.PreferenceRowTrailingSpacing))
            Box(contentAlignment = Alignment.Center) { trailing() }
        }
    }
}
