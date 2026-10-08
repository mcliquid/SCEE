package de.westnordost.streetcomplete.quests.crossing_markings

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.allDrawableResources
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CrossingMarkingsDrawableTest {

    @Test fun `every crossing markings option has an android drawable resource`() {
        for (marking in CrossingMarkings.entries) {
            val imageRes = marking.imageRes ?: continue
            val drawableName = Res.allDrawableResources.entries.first { it.value == imageRes }.key
            val id = R.drawable::class.java.getField(drawableName).getInt(null)
            assertNotEquals(0, id, "missing android drawable for $drawableName")
        }
    }

    @Test fun `crossing markings layer lists only reference packaged android drawables`() {
        val drawableDir = File("src/androidMain/res/drawable")
        assertTrue(drawableDir.isDirectory, "expected $drawableDir to exist")
        val packagedNames = drawableDir.listFiles()!!
            .filter { it.extension == "xml" && it.name.startsWith("crossing_markings") }
            .map { it.nameWithoutExtension }
            .toSet()
        assertEquals(20, packagedNames.size, "unexpected crossing_markings drawable count: $packagedNames")

        val drawableRef = Regex("""@drawable/(crossing_markings[a-z0-9_]*)""")
        for (name in packagedNames) {
            val xml = drawableDir.resolve("$name.xml").readText()
            for (ref in drawableRef.findAll(xml).map { it.groupValues[1] }.toSet()) {
                assertTrue(ref in packagedNames, "$name references missing drawable $ref")
            }
        }
    }
}
