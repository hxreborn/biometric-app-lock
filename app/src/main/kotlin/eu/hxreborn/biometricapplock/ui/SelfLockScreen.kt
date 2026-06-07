package eu.hxreborn.biometricapplock.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.ui.theme.Tokens
import eu.hxreborn.biometricapplock.ui.util.rememberAppIcon
import eu.hxreborn.biometricapplock.ui.viewmodel.SelfLockState

@Composable
internal fun SelfLockScreen(
    state: SelfLockState,
    onUnlock: () -> Unit,
    onUseCredential: () -> Unit,
) {
    val context = LocalContext.current
    val appIcon = rememberAppIcon(context.packageName)

    val errorRes =
        when {
            state is SelfLockState.LockedOut -> R.string.self_lock_error_lockout
            state is SelfLockState.Locked && state.error -> R.string.self_lock_error_failed
            else -> null
        }
    // Hold the last message so it stays readable while the error row animates out
    var lastErrorRes by remember { mutableIntStateOf(R.string.self_lock_error_failed) }
    LaunchedEffect(errorRes) {
        if (errorRes != null) lastErrorRes = errorRes
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top lighter than bottom so the content sits above the system biometric sheet
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Tokens.ScreenHorizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Tokens.SpacingLg),
        ) {
            if (appIcon != null) {
                Image(
                    bitmap = appIcon,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                )
            } else {
                Spacer(Modifier.size(72.dp))
            }
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Tokens.SpacingSm),
            ) {
                AnimatedVisibility(
                    visible = errorRes != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Text(
                        text = stringResource(lastErrorRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
                AnimatedVisibility(
                    visible = state is SelfLockState.Locked || state is SelfLockState.LockedOut,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    UnlockButton(state = state, onUnlock = onUnlock, onUseCredential = onUseCredential)
                }
            }
        }
        Spacer(Modifier.weight(2.4f))
    }
}

@Composable
private fun UnlockButton(
    state: SelfLockState,
    onUnlock: () -> Unit,
    onUseCredential: () -> Unit,
) {
    val lockedOut = state is SelfLockState.LockedOut
    Button(
        onClick = { if (lockedOut) onUseCredential() else onUnlock() },
        modifier = Modifier.heightIn(min = Tokens.PrimaryActionHeight).fillMaxWidth(0.6f),
    ) {
        Icon(
            imageVector = if (lockedOut) Icons.Outlined.Lock else Icons.Outlined.Fingerprint,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(
            text =
                stringResource(
                    if (lockedOut) R.string.self_lock_use_credential else R.string.self_lock_action,
                ),
        )
    }
}
