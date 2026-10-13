package de.westnordost.streetcomplete.data.osm.edits.waterway_crossing

import de.westnordost.streetcomplete.data.ConflictException
import de.westnordost.streetcomplete.data.osm.edits.*
import de.westnordost.streetcomplete.data.osm.edits.split_way.*
import de.westnordost.streetcomplete.data.osm.mapdata.*
import de.westnordost.streetcomplete.quests.waterway_crossing.AddWaterwayCrossing
import de.westnordost.streetcomplete.testutils.*
import de.westnordost.streetcomplete.util.math.distanceTo
import de.westnordost.streetcomplete.util.math.measuredLength
import kotlinx.serialization.json.Json
import kotlin.test.*

class WaterwayCrossingPhase2Test {
    @Test fun `OSM path crossing is excluded because it lies 64 cm from a road vertex`() {
        val data = osmCrossingPracticeData(785221627, 1385223361)
        val road = data.getWay(785221627)!!; val water = data.getWay(1385223361)!!
        val rp = road.nodeIds.map { data.getNode(it)!!.position }
        val wp = water.nodeIds.map { data.getNode(it)!!.position }
        val point = uniqueCrossingPoints(rp, wp).single()
        val distance = rp.minOf { it.distanceTo(point) }
        assertTrue(distance in 0.60..0.69, "$distance metres")
        assertFalse(road.hasCrossingStructure()); assertFalse(water.hasCrossingStructure())
        assertTrue(road.nodeIds.intersect(water.nodeIds.toSet()).isEmpty())
        assertTrue(AddWaterwayCrossing().getCrossings(data).isEmpty())
        assertTrue(findWaterwayCrossings(data, point, 1.0).isEmpty())
    }

    private fun practice(): WaterwayCrossingTestData = WaterwayCrossingTestData().also { f ->
        f.data.toList().forEach { f.data.remove(it.type, it.id) }
        f.data.putAll(osmCrossingPracticeData(982009820, 1385223359, 1385223360))
    }

    @Test fun `OSM track produces two independent crossings and one combined bridge`() {
        val f = practice(); val type = AddWaterwayCrossing()
        val candidates = type.getCrossings(f.data)
        assertEquals(setOf(1385223359L, 1385223360L), candidates.map { it.waterway.id }.toSet())
        val selected = candidates.first()
        val crossing = findWaterwayCrossings(f.data, selected.position, 1.0).single()
        val rp = crossing.roadPositions
        // All original road vertices remain exact OSM survey data.
        val cuts = listOf(SplitAtPoint(rp[1]), SplitAtPoint(rp[rp.lastIndex - 1]))
        val action = SetWaterwayStructureAction(crossing, WaterwayStructure.BRIDGE, cuts)
        assertTrue(action.selectedPositions().measuredLength() > 100.0)
        val serialized = Json.decodeFromString<SetWaterwayStructureAction>(Json.encodeToString(action))
        val changes = serialized.createUpdates(f.repo, f.idProvider())
        assertEquals(changes, serialized.createUpdates(f.repo, f.idProvider()))
        val middle = (changes.creations + changes.modifications).filterIsInstance<Way>().single { it.tags["bridge"] == "yes" }
        assertEquals("1", middle.tags["layer"])
        assertEquals(setOf(1385223359L, 1385223360L), f.data.ways.filter { it.tags["waterway"] == "stream" }
            .filter { w -> crossingPoints(action.selectedPositions(), w.nodeIds.map { f.data.getNode(it)!!.position }).isNotEmpty() }.map { it.id }.toSet())
        assertTrue((changes.creations + changes.modifications).filterIsInstance<Way>().all { it.tags["highway"] == "track" })
        (changes.creations + changes.modifications).forEach { f.data.putElement(it) }
        assertTrue(type.getCrossings(f.data).isEmpty())
    }

    @Test fun `culvert spans two roads and validates changed second road before replay`() {
        val f = WaterwayCrossingTestData()
        val other = f.road.copy(id = 30, nodeIds = listOf(7, 8))
        f.data.putElement(node(7, p(0.001, -0.002))); f.data.putElement(node(8, p(0.001, 0.002)))
        f.data.putElement(other)
        val crossing = findWaterwayCrossings(f.data, p(), 1.0).single().copy(
            nearbyWays = listOf(CrossingContextWay(other, other.nodeIds.map { f.data.getNode(it)!!.position })))
        val action = SetWaterwayStructureAction(crossing, WaterwayStructure.CULVERT, listOf(
            SplitAtLinePosition(f.waterPositions[0], f.waterPositions[1], 0.3),
            SplitAtLinePosition(f.waterPositions[0], f.waterPositions[1], 0.9)))
        val serialized = Json.decodeFromString<SetWaterwayStructureAction>(Json.encodeToString(action))
        val updates = serialized.createUpdates(f.repo, f.idProvider())
        assertEquals("culvert", (updates.creations + updates.modifications).filterIsInstance<Way>().single { "tunnel" in it.tags }.tags["tunnel"])
        assertTrue(updates.modifications.none { it.key == f.road.key || it.key == other.key })
        for (key in listOf("bridge", "layer", "tunnel", "covered", "ford")) {
            f.data.putElement(other.copy(tags = other.tags + (key to "1")))
            assertFailsWith<ConflictException> { serialized.createUpdates(f.repo, f.idProvider()) }
        }
    }

    @Test fun `crossings on same pair can be answered one at a time without invalidating the other`() {
        val f = WaterwayCrossingTestData()
        listOf(p(-0.001, -0.001), p(0.001, -0.001), p(0.001, 0.001), p(-0.001, 0.001))
            .forEachIndexed { i, pos -> f.data.putElement(node(i + 1L, pos)) }
        f.data.putElement(node(5, p(0.0, -0.002))); f.data.putElement(node(6, p(0.0, 0.002)))
        val type = AddWaterwayCrossing(); val first = type.getCrossings(f.data).first()
        val c = findWaterwayCrossings(f.data, first.position, 1.0).single()
        val changes = CreateWaterwayFordAction(c).createUpdates(f.repo, f.idProvider())
        (changes.creations + changes.modifications).forEach { f.data.putElement(it) }
        assertEquals(1, type.getCrossings(f.data).size)
        val second = type.getCrossings(f.data).single()
        assertTrue(first.position.distanceTo(second.position) > 100.0)
        assertEquals(1, findWaterwayCrossings(f.data, second.position, 1.0).size)
    }
}
