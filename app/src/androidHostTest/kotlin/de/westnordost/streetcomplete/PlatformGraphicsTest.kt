package de.westnordost.streetcomplete

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import de.westnordost.streetcomplete.quests.crossing_markings.*
import de.westnordost.streetcomplete.quests.building_colour.BuildingColour
import de.westnordost.streetcomplete.quests.roof_colour.RoofColour
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.allDrawableResources
import de.westnordost.streetcomplete.ui.util.nearbyQuestTint
import de.westnordost.streetcomplete.ui.util.parseHexColor
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlatformGraphicsTest {
    @Test fun `all existing palette values equal Android parsing`() {
        for (value in BuildingColour.entries.map { it.hexValue ?: it.osmValue } + RoofColour.entries.map { it.hexValue ?: it.osmValue }) {
            assertEquals(android.graphics.Color.parseColor(value), parseHexColor(value).toArgb())
        }
    }

    @Test fun `tint preserves Android channel arithmetic across alpha and RGB`() {
        val colors = listOf(0, -1, 0x80123456.toInt(), 0x00ff0000) + (0..255).map { it * 0x01010101 }
        for (color in colors) assertEquals(ColorUtils.blendARGB(color, -1, 0.8f), nearbyQuestTint(color).toArgb())
    }

    @Test fun `crossing resources and flattened paths render equivalently on the Android backend`() {
        val context = RuntimeEnvironment.getApplication()
        for (markings in CrossingMarkings.entries.filter { it != CrossingMarkings.YES }) {
            assertEquals(markings.imageRes, Res.allDrawableResources[markings.drawableName])
            val generated = File(System.getProperty("crossing.vector.fixtures"), markings.drawableName + ".xml").readText()
            // The Android host test cannot inflate uncompiled XML, so the flattened result is checked in as a res fixture
            // and must stay identical to what the generator currently produces.
            val fixture = File("src/androidHostTest/res/drawable/flat_${markings.drawableName}.xml").readText()
            assertEquals(generated, fixture, "stale fixture for ${markings.name}; copy the generated file")
            val id = context.resources.getIdentifier("flat_${markings.drawableName}", "drawable", context.packageName)
            val flat = context.getDrawable(id)!!
            for (size in listOf(128, 256)) {
                val original = context.getDrawable(markings.toAndroidDrawableRes())!!
                assertSameImage(pixels(original, size), pixels(flat, size), "${markings.name} at $size")
            }
        }
    }

    @Test fun `generic yes never maps to a fabricated crossing drawable`() {
        assertFails { CrossingMarkings.YES.drawableName }
        assertFails { CrossingMarkings.YES.toAndroidDrawableRes() }
    }

    /** Separately antialiased layers vs. one flattened vector differ by rounding on edges only: bound both size and magnitude. */
    private fun assertSameImage(expected: IntArray, actual: IntArray, message: String) {
        var different = 0
        var maxChannelDiff = 0
        for (i in expected.indices) {
            if (expected[i] == actual[i]) continue
            different++
            for (shift in 0..24 step 8) {
                maxChannelDiff = maxOf(maxChannelDiff, kotlin.math.abs(((expected[i] ushr shift) and 255) - ((actual[i] ushr shift) and 255)))
            }
        }
        assertTrue(maxChannelDiff <= 8, "$message: channel difference $maxChannelDiff")
        assertTrue(different <= expected.size / 20, "$message: $different of ${expected.size} pixels differ")
    }

    private fun pixels(drawable: Drawable, size: Int): IntArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        return IntArray(size * size).also { bitmap.getPixels(it, 0, size, 0, 0, size, size); bitmap.recycle() }
    }
}
