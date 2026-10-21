package de.westnordost.streetcomplete.quests.crossing_markings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import de.westnordost.streetcomplete.util.readBundledAsset
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.decodeToImageVector

internal suspend fun loadCrossingVector(markings: CrossingMarkings, density: Density): ImageVector =
    readBundledAsset("crossing/${markings.drawableName}.xml").decodeToImageVector(density)

@Composable
internal actual fun crossingMarkingsPainter(markings: CrossingMarkings): Painter {
    val density = LocalDensity.current
    val vector = remember(markings, density) { runBlocking { loadCrossingVector(markings, density) } }
    return rememberVectorPainter(vector)
}
