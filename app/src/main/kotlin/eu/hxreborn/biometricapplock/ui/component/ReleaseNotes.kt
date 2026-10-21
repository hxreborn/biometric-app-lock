package eu.hxreborn.biometricapplock.ui.component

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Webhook
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.updates.ChangeType
import eu.hxreborn.biometricapplock.updates.ChangelogEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val ChipRadius = 8.dp
private val AreaIconSize = 16.sp
private val BadgeHeight = 22.dp
private val BadgeIconSize = 14.sp
private val NoteMinHeight = 40.dp

private val ChangeTypeOrder =
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

private val BadgeVerbs =
    mapOf(
        ChangeType.Feat to setOf("add", "adds", "added"),
        ChangeType.Fix to setOf("fix", "fixes", "fixed"),
        ChangeType.Revert to setOf("revert", "reverts", "reverted"),
        ChangeType.Refactor to setOf("refactor", "refactors", "refactored"),
    )

private val NoteBullet = Regex("""^[-*•]\s+""")

private fun noteTitle(entry: ChangelogEntry): String {
    val title = entry.title.trim().replace(NoteBullet, "")
    val verb = title.substringBefore(' ')
    val rest = title.substringAfter(' ', "")
    val verbs = BadgeVerbs[ChangeType.from(entry.type, entry.breaking)].orEmpty()
    return (if (verb.lowercase() in verbs && rest.isNotBlank()) rest else title).replaceFirstChar(Char::uppercase)
}

private enum class NoteArea(
    val scopes: Set<String?>,
    @param:StringRes val title: Int,
    val icon: ImageVector,
) {
    AUTH(setOf("auth"), R.string.changelog_area_auth, Icons.Outlined.Fingerprint),
    HOOKS(setOf("hook"), R.string.changelog_area_hooks, Icons.Outlined.Webhook),
    SCOPE(setOf("scope"), R.string.changelog_area_scope, Icons.Outlined.Lock),
    INTERFACE(setOf("ui"), R.string.changelog_area_ui, Icons.Outlined.Palette),
    DEPS(setOf("deps"), R.string.changelog_area_deps, Icons.Outlined.Inventory2),
    OTHER(emptySet(), R.string.changelog_area_other, Icons.Outlined.Code),
    ;

    companion object {
        fun of(scope: String?): NoteArea = entries.firstOrNull { scope in it.scopes } ?: OTHER
    }
}

fun releaseDate(entries: List<ChangelogEntry>): String? =
    entries
        .mapNotNull { it.date }
        .maxOrNull()
        ?.let { runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrNull() }

@Composable
fun ReleaseNotes(entries: List<ChangelogEntry>) {
    val byArea = entries.groupBy { NoteArea.of(it.scope) }
    Column {
        NoteArea.entries.forEach { area ->
            val notes =
                byArea[area]?.sortedBy { ChangeTypeOrder.indexOf(ChangeType.from(it.type, it.breaking)) } ?: return@forEach
            AreaHeader(area)
            notes.forEach { NoteRow(it) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AreaHeader(area: NoteArea) {
    Row(
        Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 2.dp).semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            area.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(with(LocalDensity.current) { AreaIconSize.toDp() }),
        )
        Text(
            stringResource(area.title).uppercase(),
            style = MaterialTheme.typography.labelMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoteRow(entry: ChangelogEntry) {
    val uriHandler = LocalUriHandler.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = NoteMinHeight)
            .clip(RoundedCornerShape(ChipRadius))
            .then(
                entry.url?.let {
                    Modifier.clickable(onClickLabel = stringResource(R.string.changelog_open_link), role = Role.Button) {
                        uriHandler.openUri(it)
                    }
                } ?: Modifier,
            ).padding(horizontal = 4.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KindBadge(ChangeType.from(entry.type, entry.breaking))
        Column(Modifier.weight(1f)) {
            Text(noteTitle(entry), style = MaterialTheme.typography.bodyMedium)
            entry.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun KindBadge(type: ChangeType) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) =
        when (type) {
            ChangeType.Feat -> scheme.primaryContainer to scheme.onPrimaryContainer
            ChangeType.Fix -> scheme.tertiaryContainer to scheme.onTertiaryContainer
            ChangeType.Breaking -> scheme.errorContainer to scheme.onErrorContainer
            else -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        }
    Surface(shape = RoundedCornerShape(ChipRadius), color = container, contentColor = content) {
        Row(
            Modifier.heightIn(min = BadgeHeight).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                changeTypeIcon(type),
                contentDescription = null,
                modifier = Modifier.size(with(LocalDensity.current) { BadgeIconSize.toDp() }),
            )
            Text(
                stringResource(changeTypeLabelRes(type)),
                style = MaterialTheme.typography.labelSmallEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
