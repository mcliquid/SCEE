package de.westnordost.streetcomplete.quests.smoothness

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.osm.surface.parseSurface
import de.westnordost.streetcomplete.osm.surface.title
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.smoothness_no_photo
import de.westnordost.streetcomplete.resources.smoothness_similar_surface
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Smoothness answer item: example photo for the given surface (or a neutral placeholder if
 *  there is none), title, description and the vehicle icon. Shared by quests and overlay. */
@Composable
fun SmoothnessAnswerItem(item: Smoothness, surface: String?, modifier: Modifier = Modifier) {
    SmoothnessChoiceItem(smoothnessChoice(item, surface), modifier)
}

@Composable
fun SmoothnessChoiceItem(choice: SmoothnessChoice, modifier: Modifier = Modifier) {
    val photo = choice.photo
    Box(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (photo != null) {
                Image(
                    painter = painterResource(photo.image),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .sizeIn(maxWidth = 160.dp, maxHeight = 112.dp)
                        .clip(MaterialTheme.shapes.medium)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 160.dp, height = 107.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colors.onSurface.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.smoothness_no_photo),
                        style = MaterialTheme.typography.caption,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(start = 40.dp, end = 8.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(choice.value.title),
                    style = MaterialTheme.typography.subtitle2,
                )
                choice.description?.let {
                    Text(
                        text = stringResource(it),
                        style = MaterialTheme.typography.caption.copy(hyphens = Hyphens.Auto),
                    )
                }
                val similarSurface = photo?.similarSurface?.let { parseSurface(it) }
                if (similarSurface != null) {
                    Text(
                        text = stringResource(Res.string.smoothness_similar_surface, stringResource(similarSurface.title)),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }
        Image(
            painter = painterResource(choice.value.icon),
            contentDescription = choice.value.emoji,
            modifier = Modifier.padding(8.dp)
        )
    }
}
