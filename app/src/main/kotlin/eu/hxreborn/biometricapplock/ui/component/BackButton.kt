package eu.hxreborn.biometricapplock.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.hxreborn.biometricapplock.R
import eu.hxreborn.biometricapplock.ui.theme.Tokens

// shared circular navigation icon for the LargeTopAppBar back affordance on detail screens
@Composable
fun BackButton(onClick: () -> Unit) {
    Surface(
        modifier =
            Modifier
                .padding(start = Tokens.SpacingSm)
                .size(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.about_back_cd),
            )
        }
    }
}
