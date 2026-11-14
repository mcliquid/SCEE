package de.westnordost.streetcomplete.quests.contract

import de.westnordost.streetcomplete.quests.seating.AddSeating
import de.westnordost.streetcomplete.quests.seating.Seating
import de.westnordost.streetcomplete.quests.barrier_opening.AddBarrierOpening
import de.westnordost.streetcomplete.quests.barrier_opening.BarrierWidth
import de.westnordost.streetcomplete.quests.width.WidthAnswer
import de.westnordost.streetcomplete.osm.length.Length
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry
import de.westnordost.streetcomplete.ui.util.measure.ArSupportChecker
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

internal fun questContractFixtures(): List<QuestContract<*>> = buildList {
    val seating = AddSeating()
    for (value in listOf("garden", "terrace", "sidewalk;street", "separate")) {
        add(QuestContract(seating, Seating.INDOOR_AND_OUTDOOR,
            mapOf("amenity" to "cafe", "outdoor_seating" to value,
                "outdoor_seating:conditional" to "no @ (22:00-06:00)"),
            { seating.isApplicableTo(node(tags = it)) == true },
            setOf("outdoor_seating", "outdoor_seating:conditional")))
    }
    val barrier = AddBarrierOpening(object : ArSupportChecker { override fun invoke() = false })
    for (ar in listOf(false, true)) {
        add(QuestContract(barrier, BarrierWidth(WidthAnswer(Length.Meters(1.2), ar)),
            mapOf("barrier" to "bollard", "maxwidth:physical" to "1",
                "source:maxwidth_physical" to "estimate", "source:height" to "estimate"),
            { tags ->
                val n = node(1, tags = tags)
                barrier.getApplicableElements(TestMapDataWithGeometry(listOf(
                    n, node(2), way(11, listOf(1, 2), mapOf("highway" to "footway")),
                ))).any { it.id == n.id }
            }, setOf("source:height")))
    }
}

class QuestContractTest {
    @BeforeTest fun setup() { Prefs.preferences = inMemoryPrefs() }

    @Test fun `seating preserves specific outdoor seating and conditional information`() {
        questContractFixtures().filter { it.quest is AddSeating }.forEach { it.verify() }
    }

    @Test fun `measured barrier width closes legacy estimate quest on a footway`() {
        for (fixture in questContractFixtures().filter { it.quest is AddBarrierOpening }) {
            val after = fixture.verify()
            assertEquals(null, after["source:maxwidth_physical"])
            val answer = fixture.answer as BarrierWidth
            assertEquals(if (answer.widthAnswer.isARMeasurement) "ARCore" else null, after["source:maxwidth:physical"])
        }
    }
}
