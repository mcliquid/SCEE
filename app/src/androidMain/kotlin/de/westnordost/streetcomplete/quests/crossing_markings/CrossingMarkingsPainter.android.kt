package de.westnordost.streetcomplete.quests.crossing_markings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.util.image.toPainter

@Composable
internal actual fun crossingMarkingsPainter(markings: CrossingMarkings): Painter {
    val ctx = LocalContext.current
    val size = ctx.resources.displayMetrics.widthPixels / 4
    return ctx.getDrawable(markings.toAndroidDrawableRes())!!.toPainter(size)
}

/** Layer-list drawables are not exposed via [org.jetbrains.compose.resources.painterResource]. */
internal fun CrossingMarkings.toAndroidDrawableRes(): Int = when (this) {
    CrossingMarkings.NO -> R.drawable.crossing_markings_no
    CrossingMarkings.ZEBRA -> R.drawable.crossing_markings_zebra
    CrossingMarkings.LINES -> R.drawable.crossing_markings_lines
    CrossingMarkings.LADDER -> R.drawable.crossing_markings_ladder
    CrossingMarkings.DASHES -> R.drawable.crossing_markings_dashes
    CrossingMarkings.DOTS -> R.drawable.crossing_markings_dots
    CrossingMarkings.SURFACE -> R.drawable.crossing_markings_surface
    CrossingMarkings.LADDER_SKEWED -> R.drawable.crossing_markings_ladder_skewed
    CrossingMarkings.ZEBRA_PAIRED -> R.drawable.crossing_markings_zebra_paired
    CrossingMarkings.ZEBRA_BICOLOUR -> R.drawable.crossing_markings_zebra_bicolour
    CrossingMarkings.ZEBRA_DOUBLE -> R.drawable.crossing_markings_zebra_double
    CrossingMarkings.LADDER_PAIRED -> R.drawable.crossing_markings_ladder_paired
    CrossingMarkings.PICTOGRAMS -> R.drawable.crossing_markings_pictograms
    CrossingMarkings.YES -> error("no drawable for $this")
}
