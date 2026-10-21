package de.westnordost.streetcomplete.quests.crossing_markings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

@Composable
internal expect fun crossingMarkingsPainter(markings: CrossingMarkings): Painter

internal val CrossingMarkings.drawableName: String get() {
    require(this != CrossingMarkings.YES) { "no drawable for $this" }
    return "crossing_markings_${name.lowercase()}"
}
