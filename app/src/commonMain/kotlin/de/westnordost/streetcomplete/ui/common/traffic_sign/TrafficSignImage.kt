package de.westnordost.streetcomplete.ui.common.traffic_sign

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSign
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignGroup
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignPart
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignValue
import de.westnordost.streetcomplete.osm.traffic_sign.imagePath
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.ui.ktx.readBytesOrNull
import de.westnordost.streetcomplete.util.logs.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageVector

/** All signs of a traffic_sign value: each main sign with its additional signs below it */
@Composable
fun TrafficSignValueImage(
    value: TrafficSignValue,
    modifier: Modifier = Modifier,
    signHeight: Dp = 64.dp,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (value.isUnparseable) {
            TrafficSignFallback(value.originalValue.orEmpty(), signHeight)
        } else {
            for (group in value.groups) {
                TrafficSignGroupImage(group, value.country, signHeight = signHeight)
            }
        }
    }
}

/** A main sign with its additional signs below it */
@Composable
fun TrafficSignGroupImage(
    group: TrafficSignGroup,
    country: String?,
    modifier: Modifier = Modifier,
    signHeight: Dp = 64.dp,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        group.main?.let { TrafficSignPartImage(it, country, signHeight = signHeight) }
        for (modifierSign in group.modifiers) {
            // additional signs are less high than wide, half the height works well for German signs
            TrafficSignPartImage(modifierSign, country, signHeight = signHeight / 2)
        }
    }
}

/** One sign. Unknown signs and signs without image are shown as text */
@Composable
fun TrafficSignPartImage(
    part: TrafficSignPart,
    country: String?,
    modifier: Modifier = Modifier,
    signHeight: Dp = 64.dp,
) {
    val sign = part.sign
    // the images of signs with variable value show an example value (e.g. "10" for "274[47]"), so
    // they would be misleading
    if (sign == null || country == null || sign.hasVariableValue) {
        TrafficSignFallback(part.description, signHeight, modifier)
        return
    }
    val density = LocalDensity.current
    val vector by produceState<ImageVector?>(null, sign, country, density) {
        value = loadTrafficSignImage(sign, country, density)
    }
    val imageVector = vector
    if (imageVector != null) {
        Image(
            painter = rememberVectorPainter(imageVector),
            contentDescription = part.description,
            modifier = modifier.height(signHeight),
            contentScale = ContentScale.Fit,
        )
    } else {
        TrafficSignFallback(part.description, signHeight, modifier)
    }
}

@Composable
private fun TrafficSignFallback(text: String, signHeight: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(signHeight)
            .widthIn(min = signHeight, max = signHeight * 3)
            .border(2.dp, Color.Gray, RoundedCornerShape(4.dp))
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.caption, textAlign = TextAlign.Center, maxLines = 3)
    }
}

/** load the vector image of the given sign, null if there is none or it can't be decoded */
private suspend fun loadTrafficSignImage(sign: TrafficSign, country: String, density: Density): ImageVector? {
    val bytes = Res.readBytesOrNull(sign.imagePath(country)) ?: return null
    return withContext(Dispatchers.Default) {
        try {
            bytes.decodeToImageVector(density)
        } catch (e: Exception) {
            Log.w("TrafficSignImage", "Unable to decode image of traffic sign ${sign.osmValuePart}", e)
            null
        }
    }
}
