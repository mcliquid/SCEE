package de.westnordost.streetcomplete.quests.water_bottle_fill

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.way
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddWaterBottleFillTest {
    private val questType = AddWaterBottleFill()

    @Test fun `applicable to drinking water node`() {
        val mapData = TestMapDataWithGeometry(listOf(node(1, tags = mapOf("amenity" to "drinking_water"))))
        assertEquals(1, questType.getApplicableElements(mapData).count())
    }

    @Test fun `applicable to water point way`() {
        val mapData = TestMapDataWithGeometry(listOf(way(1, tags = mapOf("amenity" to "water_point"))))
        assertEquals(1, questType.getApplicableElements(mapData).count())
    }

    @Test fun `applicable to water tap node`() {
        val mapData = TestMapDataWithGeometry(listOf(node(1, tags = mapOf("man_made" to "water_tap"))))
        assertEquals(1, questType.getApplicableElements(mapData).count())
    }

    @Test fun `applicable to drinking fountain node`() {
        val mapData = TestMapDataWithGeometry(listOf(node(1, tags = mapOf("man_made" to "drinking_fountain"))))
        assertEquals(1, questType.getApplicableElements(mapData).count())
    }

    @Test fun `not applicable when bottle is already set`() {
        val mapData = TestMapDataWithGeometry(
            listOf(node(1, tags = mapOf("amenity" to "drinking_water", "bottle" to "yes"))),
        )
        assertEquals(0, questType.getApplicableElements(mapData).count())
    }

    @Test fun `not applicable with access no on any element type`() {
        val mapData = TestMapDataWithGeometry(
            listOf(
                node(1, tags = mapOf("amenity" to "drinking_water", "access" to "no")),
                way(2, tags = mapOf("amenity" to "water_point", "access" to "no")),
                node(3, tags = mapOf("man_made" to "water_tap", "access" to "no")),
                node(4, tags = mapOf("man_made" to "drinking_fountain", "access" to "no")),
            ),
        )
        assertEquals(0, questType.getApplicableElements(mapData).count())
    }

    @Test fun `not applicable with access private on any element type`() {
        val mapData = TestMapDataWithGeometry(
            listOf(
                node(1, tags = mapOf("amenity" to "drinking_water", "access" to "private")),
                way(2, tags = mapOf("amenity" to "water_point", "access" to "private")),
                node(3, tags = mapOf("man_made" to "water_tap", "access" to "private")),
                node(4, tags = mapOf("man_made" to "drinking_fountain", "access" to "private")),
            ),
        )
        assertEquals(0, questType.getApplicableElements(mapData).count())
    }

    @Test fun `not applicable with fountain bottle refill on any element type`() {
        val mapData = TestMapDataWithGeometry(
            listOf(
                node(1, tags = mapOf("amenity" to "drinking_water", "fountain" to "bottle_refill")),
                way(2, tags = mapOf("amenity" to "water_point", "fountain" to "bottle_refill")),
                node(3, tags = mapOf("man_made" to "water_tap", "fountain" to "bottle_refill")),
                node(4, tags = mapOf("man_made" to "drinking_fountain", "fountain" to "bottle_refill")),
            ),
        )
        assertEquals(0, questType.getApplicableElements(mapData).count())
    }

    @Test fun `global exclusions apply across grouped element types`() {
        val mapData = TestMapDataWithGeometry(
            listOf(node(1, tags = mapOf("man_made" to "drinking_fountain", "access" to "no"))),
        )
        assertEquals(0, questType.getApplicableElements(mapData).count())
    }

    @Test fun `yes answer sets bottle yes only`() {
        assertEquals(
            setOf(StringMapEntryAdd("bottle", "yes")),
            questType.answerAppliedTo(true, mapOf("amenity" to "drinking_water")),
        )
    }

    @Test fun `no answer sets bottle no only`() {
        assertEquals(
            setOf(StringMapEntryAdd("bottle", "no")),
            questType.answerAppliedTo(false, mapOf("man_made" to "water_tap")),
        )
    }

    @Test fun `form uses one-click yes no quest without ok button`() {
        val questSource = readSource("quests/water_bottle_fill/AddWaterBottleFill.kt")
        assertTrue(questSource.contains("YesNoQuestForm(on)"))

        val yesNoFormSource = readSource("ui/common/quest/YesNoQuestForm.kt")
        assertTrue(yesNoFormSource.contains("on(Answer(false))"))
        assertTrue(yesNoFormSource.contains("on(Answer(true))"))
        assertFalse(yesNoFormSource.contains("onClickOk"))
        assertFalse(yesNoFormSource.contains("FloatingOkButton"))
    }

    private fun readSource(relativePath: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            val candidate = File(dir, "app/src/commonMain/kotlin/de/westnordost/streetcomplete/$relativePath")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        error("Could not find source: $relativePath")
    }
}
