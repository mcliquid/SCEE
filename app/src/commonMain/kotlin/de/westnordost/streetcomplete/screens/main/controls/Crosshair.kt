package de.westnordost.streetcomplete.screens.main.controls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.theme.AppTheme
import org.jetbrains.compose.resources.painterResource

/** A crosshair at the position at which a new POI should be created: the center of the map */
@Composable
fun Crosshair(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        Icon(
            painter = painterResource(Res.drawable.crosshair),
            contentDescription = null,
            modifier = Modifier.align(Alignment.Center),
            tint = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
        )
    }
}

@Preview
@Composable
private fun PreviewCrosshair() {
    AppTheme {
        Surface {
            Crosshair()
        }
    }
}
