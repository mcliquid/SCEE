package de.westnordost.streetcomplete.quests.barrier_locked

import de.westnordost.streetcomplete.data.elementfilter.dateDaysAgo
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryModify
import de.westnordost.streetcomplete.osm.nowAsCheckDateString
import de.westnordost.streetcomplete.osm.toCheckDateString
import de.westnordost.streetcomplete.quests.answerApplied
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddBarrierLockedTest {

    private val questType = AddBarrierLocked()

    private val expiredLockedYesCheckDate = dateDaysAgo(365 * 6f).toCheckDateString()
    private val expiredLockedAnyCheckDate = dateDaysAgo(365 * 11f).toCheckDateString()
    private val recentCheckDate = nowAsCheckDateString()

    // ---- Nodes: ordinary matching gate ----

    @Test
    fun `quest shown for ordinary matching gate node`() {
        val barrierNode = node(1, tags = mapOf("barrier" to "gate"))

        val mapData = TestMapDataWithGeometry(listOf(
            barrierNode,
            node(2),
            way(11, listOf(1, 2), mapOf("highway" to "service")),
        ))

        val applicable = questType.getApplicableElements(mapData).toList()
        assertEquals(listOf(barrierNode), applicable)
        assertNull(questType.isApplicableTo(barrierNode))
    }

    @Test
    fun `quest shown for barrier node with two unrestricted connected ways`() {
        val barrierNode = node(1, tags = mapOf("barrier" to "gate"))

        val wayA = way(11, listOf(1, 2), mapOf("highway" to "service"))
        val wayB = way(12, listOf(1, 3), mapOf("highway" to "service"))

        val mapData = TestMapDataWithGeometry(listOf(
            barrierNode,
            node(2),
            node(3),
            wayA,
            wayB,
        ))

        val applicable = questType.getApplicableElements(mapData).toList()
        assertEquals(listOf(barrierNode), applicable)
        assertNull(questType.isApplicableTo(barrierNode))
    }

    // ---- Nodes: access-boundary suppression ----

    @Test
    fun `no quest for barrier node at unrestricted-restricted highway boundary`() {
        val barrierNode = node(1, tags = mapOf("barrier" to "gate"))

        val privateWay = way(
            11,
            listOf(1, 2),
            mapOf("highway" to "service", "access" to "private"),
        )
        val publicWay = way(
            12,
            listOf(1, 3),
            mapOf("highway" to "service"),
        )

        val mapData = TestMapDataWithGeometry(listOf(
            barrierNode,
            node(2),
            node(3),
            privateWay,
            publicWay,
        ))

        assertEquals(emptyList(), questType.getApplicableElements(mapData).toList())
    }

    @Test
    fun `isApplicableTo for access-boundary gate node is null not true`() {
        val barrierNode = node(1, tags = mapOf("barrier" to "gate"))
        assertNull(questType.isApplicableTo(barrierNode))
        assertFalse(questType.isApplicableTo(barrierNode) == true)
    }

    @Test
    fun `contextual reevaluation excludes access-boundary gate node`() {
        val barrierNode = node(1, tags = mapOf("barrier" to "gate"))

        // Single-element path: null → load surroundings → getApplicableElements
        assertNull(questType.isApplicableTo(barrierNode))

        val mapData = TestMapDataWithGeometry(listOf(
            barrierNode,
            node(2),
            node(3),
            way(11, listOf(1, 2), mapOf("highway" to "service", "access" to "private")),
            way(12, listOf(1, 3), mapOf("highway" to "service")),
        ))

        val appliesViaSurroundings = questType.getApplicableElements(mapData)
            .any { it.id == barrierNode.id && it.type == barrierNode.type }
        assertFalse(appliesViaSurroundings)
    }

    @Test
    fun `no quest for barrier node with access=no and unrestricted connected ways`() {
        val barrierNode = node(1, tags = mapOf("barrier" to "gate"))

        val mapData = TestMapDataWithGeometry(listOf(
            barrierNode,
            node(2),
            node(3),
            way(11, listOf(1, 2), mapOf("highway" to "service", "access" to "no")),
            way(12, listOf(1, 3), mapOf("highway" to "service")),
        ))

        assertEquals(emptyList(), questType.getApplicableElements(mapData).toList())
        assertNull(questType.isApplicableTo(barrierNode))
    }

    // ---- Ways ----

    @Test
    fun `matching barrier way is returned in bulk applicability`() {
        val barrierWay = way(1, listOf(1, 2), mapOf("barrier" to "gate"))

        val mapData = TestMapDataWithGeometry(listOf(
            node(1),
            node(2),
            barrierWay,
        ))

        assertEquals(listOf(barrierWay), questType.getApplicableElements(mapData).toList())
    }

    @Test
    fun `matching barrier way has definitive true isApplicableTo`() {
        val barrierWay = way(1, listOf(1, 2), mapOf("barrier" to "wicket_gate"))
        assertEquals(true, questType.isApplicableTo(barrierWay))
    }

    @Test
    fun `matching barrier ways for all filter values are applicable`() {
        val values = listOf(
            "bump_gate", "chain", "door", "gate",
            "swing_gate", "sliding_gate", "sliding_beam", "wicket_gate",
        )
        for ((index, value) in values.withIndex()) {
            val barrierWay = way(index.toLong(), listOf(1, 2), mapOf("barrier" to value))
            assertEquals(true, questType.isApplicableTo(barrierWay), "expected applicable for barrier=$value")
            val mapData = TestMapDataWithGeometry(listOf(node(1), node(2), barrierWay))
            assertEquals(
                listOf(barrierWay),
                questType.getApplicableElements(mapData).toList(),
                "expected bulk applicable for barrier=$value",
            )
        }
    }

    @Test
    fun `unsupported barrier way is rejected`() {
        val fence = way(1, listOf(1, 2), mapOf("barrier" to "fence"))
        val wall = way(2, listOf(3, 4), mapOf("barrier" to "wall"))
        val hedge = way(3, listOf(5, 6), mapOf("barrier" to "hedge"))

        assertEquals(false, questType.isApplicableTo(fence))
        assertEquals(false, questType.isApplicableTo(wall))
        assertEquals(false, questType.isApplicableTo(hedge))

        val mapData = TestMapDataWithGeometry(listOf(
            node(1), node(2), node(3), node(4), node(5), node(6),
            fence, wall, hedge,
        ))
        assertEquals(emptyList(), questType.getApplicableElements(mapData).toList())
    }

    @Test
    fun `bulk applicability returns matching nodes and ways together`() {
        val gateNode = node(1, tags = mapOf("barrier" to "gate"))
        val gateWay = way(10, listOf(4, 5), mapOf("barrier" to "swing_gate"))

        val mapData = TestMapDataWithGeometry(listOf(
            gateNode,
            node(2),
            node(3),
            node(4),
            node(5),
            way(11, listOf(1, 2), mapOf("highway" to "service")),
            way(12, listOf(1, 3), mapOf("highway" to "service")),
            gateWay,
        ))

        val applicable = questType.getApplicableElements(mapData).toList()
        assertEquals(2, applicable.size)
        assertTrue(applicable.contains(gateNode))
        assertTrue(applicable.contains(gateWay))
    }

    @Test
    fun `access-boundary suppression does not drop matching ways`() {
        // A matching barrier way must remain even when a nearby gate node is suppressed.
        val boundaryGate = node(1, tags = mapOf("barrier" to "gate"))
        val barrierWay = way(20, listOf(4, 5), mapOf("barrier" to "gate"))

        val mapData = TestMapDataWithGeometry(listOf(
            boundaryGate,
            node(2),
            node(3),
            node(4),
            node(5),
            way(11, listOf(1, 2), mapOf("highway" to "service", "access" to "private")),
            way(12, listOf(1, 3), mapOf("highway" to "service")),
            barrierWay,
        ))

        assertEquals(listOf(barrierWay), questType.getApplicableElements(mapData).toList())
    }

    // ---- Freshness / locked=* semantics ----

    @Test
    fun `not applicable when locked=yes is recent`() {
        val element = node(tags = mapOf(
            "barrier" to "gate",
            "locked" to "yes",
            "check_date:locked" to recentCheckDate,
        ))
        assertEquals(false, questType.isApplicableTo(element))
    }

    @Test
    fun `applicable when locked=yes is older than 5 years`() {
        val element = node(tags = mapOf(
            "barrier" to "gate",
            "locked" to "yes",
            "check_date:locked" to expiredLockedYesCheckDate,
        ))
        assertNull(questType.isApplicableTo(element))
    }

    @Test
    fun `not applicable when locked=no is recent`() {
        val element = node(tags = mapOf(
            "barrier" to "gate",
            "locked" to "no",
            "check_date:locked" to recentCheckDate,
        ))
        assertEquals(false, questType.isApplicableTo(element))
    }

    @Test
    fun `applicable when locked=no is older than 10 years`() {
        val element = node(tags = mapOf(
            "barrier" to "gate",
            "locked" to "no",
            "check_date:locked" to expiredLockedAnyCheckDate,
        ))
        assertNull(questType.isApplicableTo(element))
    }

    @Test
    fun `not applicable to non-matching element`() {
        assertEquals(false, questType.isApplicableTo(node(tags = mapOf("amenity" to "bench"))))
        assertEquals(false, questType.isApplicableTo(way(tags = mapOf("highway" to "residential"))))
    }

    // ---- Answer / tag semantics ----

    @Test
    fun `Locked answer sets locked=yes`() {
        assertEquals(
            setOf(StringMapEntryAdd("locked", "yes")),
            questType.answerApplied(Locked),
        )
    }

    @Test
    fun `NotLocked answer sets locked=no`() {
        assertEquals(
            setOf(StringMapEntryAdd("locked", "no")),
            questType.answerApplied(NotLocked),
        )
    }

    @Test
    fun `Locked answer updates existing locked and removes conditional`() {
        assertEquals(
            setOf(
                StringMapEntryModify("locked", "no", "yes"),
                StringMapEntryDelete("locked:conditional", "yes @ (Mo-Fr 08:00-18:00)"),
            ),
            questType.answerAppliedTo(
                Locked,
                mapOf(
                    "locked" to "no",
                    "locked:conditional" to "yes @ (Mo-Fr 08:00-18:00)",
                ),
            ),
        )
    }

    @Test
    fun `NotLocked answer updates existing locked and removes conditional`() {
        assertEquals(
            setOf(
                StringMapEntryModify("locked", "yes", "no"),
                StringMapEntryDelete("locked:conditional", "no @ (Mo-Fr 08:00-18:00)"),
            ),
            questType.answerAppliedTo(
                NotLocked,
                mapOf(
                    "locked" to "yes",
                    "locked:conditional" to "no @ (Mo-Fr 08:00-18:00)",
                ),
            ),
        )
    }

    @Test
    fun `Locked answer on same value updates check date`() {
        assertEquals(
            setOf(
                StringMapEntryModify("locked", "yes", "yes"),
                StringMapEntryAdd("check_date:locked", nowAsCheckDateString()),
            ),
            questType.answerAppliedTo(Locked, mapOf("locked" to "yes")),
        )
    }
}
