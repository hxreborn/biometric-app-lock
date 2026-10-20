@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package eu.hxreborn.biometricapplock.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.hxreborn.biometricapplock.App
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.ui.component.BackButton
import eu.hxreborn.biometricapplock.ui.component.changeTypeLabelRes
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.updates.ChangeType
import eu.hxreborn.biometricapplock.updates.ChangelogEntry
import kotlinx.coroutines.launch

@Composable
fun ChangelogScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val app = App.from(LocalContext.current)
    val cached by app.updateRepository.cachedChangelog.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { app.updateRepository.fetchChangelog() }
    val entries = cached ?: app.updateRepository.bundledChangelog
    val releases =
        remember(entries) {
            entries.filter { it.version != null }.groupBy { it.version.orEmpty() }.toList()
        }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MediumFlexibleTopAppBar(
                navigationIcon = { BackButton(onClick = onBack) },
                title = { Text(stringResource(R.string.about_changelog_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            Column(
                modifier = Modifier.navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(Tokens.SpacingSm),
            ) {
                AnimatedVisibility(
                    visible = listState.canScrollBackward,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    SmallFloatingActionButton(onClick = { coroutineScope.launch { listState.animateScrollToItem(0) } }) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.changelog_jump_top))
                    }
                }
                AnimatedVisibility(
                    visible = listState.canScrollForward,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    SmallFloatingActionButton(
                        onClick = { coroutineScope.launch { listState.animateScrollToItem((releases.size - 1).coerceAtLeast(0)) } },
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.changelog_jump_bottom))
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = Tokens.SectionHorizontalMargin,
                    end = Tokens.SectionHorizontalMargin,
                    top = innerPadding.calculateTopPadding() + Tokens.SpacingSm,
                    bottom = contentPadding.calculateBottomPadding() + Tokens.SpacingLg,
                ),
            verticalArrangement = Arrangement.spacedBy(Tokens.SheetItemSpacing),
        ) {
            items(releases, key = { it.first }) { (version, changes) ->
                ReleaseCard(version = version, changes = changes)
            }
        }
    }
}

@Composable
private fun ReleaseCard(
    version: String,
    changes: List<ChangelogEntry>,
) {
    val uriHandler = LocalUriHandler.current
    val byType = remember(changes) { changes.groupBy { ChangeType.from(it.type, it.breaking) } }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(Tokens.SpacingLg),
            verticalArrangement = Arrangement.spacedBy(Tokens.SpacingSm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "v$version", style = MaterialTheme.typography.titleMedium)
                changes.firstNotNullOfOrNull { it.date }?.let { date ->
                    Text(text = date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            byType.forEach { (type, group) ->
                Text(
                    text = stringResource(changeTypeLabelRes(type)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Tokens.SpacingXs),
                )
                group.forEach { change ->
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(change.url?.let { url -> Modifier.clickable { uriHandler.openUri(url) } } ?: Modifier),
                    ) {
                        Text(
                            text = change.title.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        change.description?.takeIf { it.isNotBlank() }?.let { body ->
                            Text(
                                text = body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
