package de.westnordost.streetcomplete.quests.bicycle_bypass

import de.westnordost.streetcomplete.ApplicationConstants.EE_QUEST_OFFSET
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.BICYCLIST
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.CAR
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.default_disabled_msg_ee
import de.westnordost.streetcomplete.resources.quest_bicycle_bypass
import de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry
import de.westnordost.streetcomplete.testutils.mockPrefs3
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.way
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddBicycleBypassTest {
    private lateinit var quest: AddBicycleBypass

    @BeforeTest fun setUp() {
        Prefs.preferences = mockPrefs3()
        quest = AddBicycleBypass()
    }

    private fun onAccessibleRoad(
        trafficCalming: String,
        nodeExtra: Map<String, String> = emptyMap(),
        wayExtra: Map<String, String> = emptyMap(),
        nodeId: Long = 1,
    ) = TestMapDataWithGeometry(
        listOf(
            node(nodeId, tags = mapOf("traffic_calming" to trafficCalming) + nodeExtra),
            way(2, listOf(nodeId, 3), mapOf("highway" to "residential") + wayExtra),
            node(3),
        ),
    )

    private fun selected(mapData: TestMapDataWithGeometry): Boolean =
        quest.getApplicableElements(mapData).any()

    @Test fun `applicable to choker on bicycle-accessible road`() {
        assertTrue(selected(onAccessibleRoad("choker")))
    }

    @Test fun `applicable to chicane on bicycle-accessible road`() {
        assertTrue(selected(onAccessibleRoad("chicane")))
    }

    @Test fun `applicable to choked table on bicycle-accessible road`() {
        assertTrue(selected(onAccessibleRoad("choked_table")))
    }

    @Test fun `applicable to compound traffic calming containing choker`() {
        assertTrue(selected(onAccessibleRoad("choker;island")))
    }

    @Test fun `applicable when suitable traffic calming value is not first in list`() {
        assertTrue(selected(onAccessibleRoad("island;choker")))
        assertTrue(selected(onAccessibleRoad("rumble_strip;chicane")))
        assertTrue(selected(onAccessibleRoad("bump;choker")))
    }

    @Test fun `not applicable when only unsuitable types appear in compound value`() {
        assertFalse(selected(onAccessibleRoad("island;bump")))
        assertFalse(selected(onAccessibleRoad("hump;rumble_strip")))
    }

    @Test fun `not applicable for unsuitable traffic calming type`() {
        assertFalse(selected(onAccessibleRoad("bump")))
        assertFalse(selected(onAccessibleRoad("hump")))
        assertFalse(selected(onAccessibleRoad("cushion")))
        assertFalse(selected(onAccessibleRoad("island")))
    }

    @Test fun `not applicable when bicycle_bypass already set`() {
        assertFalse(selected(onAccessibleRoad("choker", nodeExtra = mapOf("bicycle_bypass" to "yes"))))
        assertFalse(selected(onAccessibleRoad("choker", nodeExtra = mapOf("bicycle_bypass" to "no"))))
    }

    @Test fun `not applicable when directional bicycle_bypass is set`() {
        assertFalse(selected(onAccessibleRoad("choker", nodeExtra = mapOf("bicycle_bypass:forward" to "yes"))))
        assertFalse(selected(onAccessibleRoad("choker", nodeExtra = mapOf("bicycle_bypass:backward" to "no"))))
    }

    @Test fun `not applicable with node bicycle or access restrictions`() {
        assertFalse(selected(onAccessibleRoad("choker", nodeExtra = mapOf("bicycle" to "no"))))
        assertFalse(selected(onAccessibleRoad("choker", nodeExtra = mapOf("bicycle" to "private"))))
        assertFalse(selected(onAccessibleRoad("choker", nodeExtra = mapOf("access" to "private"))))
        assertFalse(selected(onAccessibleRoad("choker", nodeExtra = mapOf("access" to "no"))))
    }

    @Test fun `not applicable on bicycle forbidden road`() {
        assertFalse(selected(onAccessibleRoad("choker", wayExtra = mapOf("bicycle" to "no"))))
        assertFalse(selected(onAccessibleRoad("choker", wayExtra = mapOf("bicycle" to "private"))))
        assertFalse(selected(onAccessibleRoad("choker", wayExtra = mapOf("access" to "private"))))
        assertFalse(selected(onAccessibleRoad("choker", wayExtra = mapOf("access" to "no"))))
    }

    @Test fun `not applicable when only connected ways are paths not roads`() {
        val mapData = TestMapDataWithGeometry(
            listOf(
                node(1, tags = mapOf("traffic_calming" to "choker")),
                way(2, listOf(1, 3), mapOf("highway" to "cycleway", "bicycle" to "designated")),
                way(3, listOf(1, 4), mapOf("highway" to "footway", "bicycle" to "yes")),
                node(3),
                node(4),
            ),
        )
        assertFalse(selected(mapData))
    }

    @Test fun `applicable at intersection with bicycle-accessible road and path`() {
        val mapData = TestMapDataWithGeometry(
            listOf(
                node(1, tags = mapOf("traffic_calming" to "choker")),
                way(2, listOf(1, 3), mapOf("highway" to "residential")),
                way(3, listOf(1, 4), mapOf("highway" to "footway", "bicycle" to "yes")),
                node(3),
                node(4),
            ),
        )
        assertTrue(selected(mapData))
    }

    @Test fun `not applicable when every connected road forbids bicycles`() {
        val mapData = TestMapDataWithGeometry(
            listOf(
                node(1, tags = mapOf("traffic_calming" to "choker")),
                way(2, listOf(1, 3), mapOf("highway" to "residential", "bicycle" to "no")),
                way(3, listOf(1, 4), mapOf("highway" to "tertiary", "bicycle" to "no")),
                node(3),
                node(4),
            ),
        )
        assertFalse(selected(mapData))
    }

    @Test fun `applicable when any connected bicycle-accessible road exists`() {
        val mapData = TestMapDataWithGeometry(
            listOf(
                node(1, tags = mapOf("traffic_calming" to "chicane")),
                way(2, listOf(1, 3), mapOf("highway" to "residential", "bicycle" to "no")),
                way(3, listOf(1, 4), mapOf("highway" to "unclassified")),
                node(3),
                node(4),
            ),
        )
        assertTrue(selected(mapData))
    }

    @Test fun `not applicable on highway construction way`() {
        val mapData = TestMapDataWithGeometry(
            listOf(
                node(1, tags = mapOf("traffic_calming" to "choker")),
                way(2, listOf(1, 3), mapOf("highway" to "construction")),
                node(3),
            ),
        )
        assertFalse(selected(mapData))
    }

    @Test fun `not applicable for isolated traffic calming node without road`() {
        val mapData = TestMapDataWithGeometry(listOf(node(1, tags = mapOf("traffic_calming" to "choker"))))
        assertFalse(selected(mapData))
        assertEquals(null, quest.isApplicableTo(mapData.nodes.first()))
    }

    @Test fun `yes answer sets bicycle_bypass only`() {
        val tags = mapOf("traffic_calming" to "choker", "traffic_calming:maxspeed" to "30")
        assertEquals(
            setOf(StringMapEntryAdd("bicycle_bypass", "yes")),
            quest.answerAppliedTo(true, tags),
        )
    }

    @Test fun `no answer sets bicycle_bypass only`() {
        val tags = mapOf(
            "traffic_calming" to "chicane",
            "bicycle" to "yes",
            "access" to "yes",
            "oneway" to "yes",
        )
        assertEquals(
            setOf(StringMapEntryAdd("bicycle_bypass", "no")),
            quest.answerAppliedTo(false, tags),
        )
    }

    @Test fun `yes no quest form without ok button`() {
        val questSource = readSource("quests/bicycle_bypass/AddBicycleBypass.kt")
        assertTrue(questSource.contains("YesNoQuestForm(on)"))
        val yesNoFormSource = readSource("ui/common/quest/YesNoQuestForm.kt")
        assertFalse(yesNoFormSource.contains("onClickOk"))
    }

    @Test fun `disabled by default with expert message`() {
        assertEquals(Res.string.default_disabled_msg_ee, quest.defaultDisabledMessage)
    }

    @Test fun `achievements`() {
        assertEquals(listOf(BICYCLIST, CAR), quest.achievements)
    }

    @Test fun `uses bicycle bypass quest icon resource`() {
        assertEquals(Res.drawable.quest_bicycle_bypass, quest.icon)
    }

    @Test fun `quest id is unique in registry`() {
        val registrySource = readSource("quests/QuestTypesRegistry.kt")
        val questId = "EE_QUEST_OFFSET + 215"
        assertEquals(1, registrySource.split(questId).size - 1)
        assertEquals(1, registrySource.split("AddBicycleBypass()").size - 1)
        assertEquals(EE_QUEST_OFFSET + 215, EXPECTED_QUEST_ORDINAL)
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

    companion object {
        const val EXPECTED_QUEST_ORDINAL = EE_QUEST_OFFSET + 215
    }
}