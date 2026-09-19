package de.westnordost.streetcomplete.quests.kerb_type

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry
import de.westnordost.streetcomplete.testutils.mockPrefs3
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AddKerbTypeTest {

    private val questType = AddKerbType()

    @BeforeTest fun setUp() {
        Prefs.preferences = mockPrefs3()
    }

    @Test fun `applicable to barrier kerb ways without kerb key`() {
        val mapData = TestMapDataWithGeometry(
            listOf(
                way(tags = mapOf("barrier" to "kerb"))
            )
        )
        assertEquals(1, questType.getApplicableElements(mapData).toList().size)
    }

    @Test fun `not applicable to barrier kerb ways with kerb key already`() {
        val mapData = TestMapDataWithGeometry(listOf(
            way(tags = mapOf(
                "barrier" to "kerb",
                "kerb" to "raised"
            ))
        ))
        assertEquals(0, questType.getApplicableElements(mapData).toList().size)
    }

    @Test fun `apply no kerb answer removes barrier and adds no-barrier tag`() {
        val changes = questType.answerAppliedTo(KerbType.NO_KERB, mapOf("barrier" to "kerb"))

        assertEquals(
            setOf(
                StringMapEntryAdd("kerb", "no"),
                StringMapEntryDelete("barrier", "kerb"),
                StringMapEntryAdd("no:barrier", "kerb")
            ),
            changes
        )
    }

    @Test fun `disabled by default in SCEE`() {
        assertNotNull(questType.defaultDisabledMessage)
    }
}
