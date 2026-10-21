package de.westnordost.streetcomplete

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import de.westnordost.streetcomplete.quests.crossing_markings.*
import de.westnordost.streetcomplete.resources.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import kotlin.test.*

class DesktopGraphicsTest {
    @Test fun `every selectable crossing resolves its existing drawable and generated vector`() = runBlocking<Unit> {
        for (marking in CrossingMarkings.entries.filter { it != CrossingMarkings.YES }) {
            assertEquals(marking.imageRes, Res.allDrawableResources[marking.drawableName])
            val vector = loadCrossingVector(marking, Density(1f))
            assertEquals(600f, vector.viewportWidth)
            assertEquals(600f, vector.viewportHeight)
            assertTrue(vector.root.size > 0)
            assertEquals(vector.viewportWidth, loadCrossingVector(marking, Density(1f)).viewportWidth)
        }
        assertTrue(getString(Res.string.action_copy).isNotBlank()) // localized: the test must not depend on the system language
        assertTrue(getString(Res.string.quest_crossing_marking_value_no).isNotBlank())
    }

    @Test fun `actual desktop painters render every crossing repeatedly without Android classes`() = runBlocking<Unit> {
        assertFailsWith<ClassNotFoundException> { Class.forName("android.graphics.drawable.Drawable") }
        for (marking in CrossingMarkings.entries.filter { it != CrossingMarkings.YES }) {
            repeat(2) {
                val recomposer = Recomposer(coroutineContext)
                val composition = Composition(EmptyApplier(), recomposer)
                try {
                    var painter: Painter? = null
                    composition.setContent { CompositionLocalProvider(LocalDensity provides Density(1f)) { painter = crossingMarkingsPainter(marking) } }
                    val pixels = render(assertNotNull(painter), 128).toPixelMap()
                    assertTrue((0 until 128).any { x -> (0 until 128).any { y -> pixels[x, y].alpha > 0f } })
                } finally { composition.dispose(); recomposer.cancel() }
            }
        }
    }

    @Test fun `bitmap painter preserves transparency alpha tint and repeatability`() {
        val bitmap = ImageBitmap(2, 2)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(2f, 2f)) {
            drawRect(Color.Blue, size = Size(1f, 2f))
        }
        repeat(3) {
            val pixels = render(BitmapPainter(bitmap), 2, 0.5f, ColorFilter.tint(Color.Red)).toPixelMap()
            assertEquals(0f, pixels[1, 0].alpha)
            assertTrue(pixels[0, 0].alpha in 0.49f..0.51f)
            assertEquals(1f, pixels[0, 0].red)
        }
    }

    @Test fun `generic yes is an error rather than an invented image`() = runBlocking<Unit> {
        assertFailsWith<IllegalArgumentException> { loadCrossingVector(CrossingMarkings.YES, Density(1f)) }
    }

    private fun render(painter: Painter, size: Int, alpha: Float = 1f, filter: ColorFilter? = null): ImageBitmap {
        val bitmap = ImageBitmap(size, size)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(size.toFloat(), size.toFloat())) {
            with(painter) { draw(this@draw.size, alpha, filter) }
        }
        return bitmap
    }

    private class EmptyApplier : AbstractApplier<Unit>(Unit) {
        override fun insertBottomUp(index: Int, instance: Unit) {}
        override fun insertTopDown(index: Int, instance: Unit) {}
        override fun remove(index: Int, count: Int) {}
        override fun move(from: Int, to: Int, count: Int) {}
        override fun onClear() {}
    }
}
