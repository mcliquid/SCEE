package de.westnordost.streetcomplete.data.routing

import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.data.download.tiles.TilePos
import de.westnordost.streetcomplete.data.download.tiles.enclosingTilePos
import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.quest.QuestKey
import de.westnordost.streetcomplete.testutils.QUEST_TYPE
import de.westnordost.streetcomplete.testutils.osmNoteQuest
import de.westnordost.streetcomplete.testutils.osmQuest
import de.westnordost.streetcomplete.util.math.translate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.time.TimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteQuestServiceTest {
    private val start = LatLon(52.5, 13.3)
    private val end = start.translate(1000.0, 90.0)
    private val route = listOf(start, end)

    @Test fun `dense eight hundred meter route with duplicates ends quickly`() = runBlocking {
        val points = (0..800).flatMap { meters ->
            val position = start.translate(meters.toDouble(), 90.0)
            listOf(position, position)
        }
        val time = TimeSource.Monotonic.markNow()
        val corridor = withTimeout(1000) { prepareRouteQuestCorridor(points) }
        val elapsed = time.elapsedNow().inWholeMilliseconds
        println("800m corridor, ${points.size} input points: $elapsed ms; ${corridor.tiles.size} tiles")
        assertTrue(elapsed < 1000)
        assertTrue(corridor.tiles.size < 30)
        for (point in points) {
            assertTrue(point.enclosingTilePos(ApplicationConstants.DOWNLOAD_TILE_ZOOM) in corridor.tiles)
        }
    }

    @Test fun `expanded corridor covers piece boundaries start and end without tile holes`() = runBlocking {
        val longEnd = start.translate(3000.0, 90.0)
        val corridor = prepareRouteQuestCorridor(listOf(start, longEnd), 250.0)
        for (meters in 0..3000 step 25) {
            val position = start.translate(meters.toDouble(), 90.0)
            for (bearing in listOf(0.0, 180.0)) {
                assertTrue(position.translate(240.0, bearing).enclosingTilePos(ApplicationConstants.DOWNLOAD_TILE_ZOOM) in corridor.tiles,
                    "Missing tile at $meters m, bearing $bearing")
            }
        }
    }

    @Test fun `short corridor retains both endpoints`() = runBlocking {
        val shortEnd = start.translate(40.0, 90.0)
        val corridor = prepareRouteQuestCorridor(listOf(start, shortEnd))
        assertTrue(start.enclosingTilePos(ApplicationConstants.DOWNLOAD_TILE_ZOOM) in corridor.tiles)
        assertTrue(shortEnd.enclosingTilePos(ApplicationConstants.DOWNLOAD_TILE_ZOOM) in corridor.tiles)
    }

    @Test fun `corridor covers intermediate points and both sides without city bounding box`() = runBlocking {
        val corridor = prepareRouteQuestCorridor(route)
        for (meters in 0..1000 step 50) {
            val point = start.translate(meters.toDouble(), 90.0)
            for (bearing in listOf(0.0, 180.0)) {
                assertTrue(point.translate(90.0, bearing).enclosingTilePos(ApplicationConstants.DOWNLOAD_TILE_ZOOM) in corridor.tiles)
            }
        }
        assertFalse(start.translate(5000.0, 0.0).enclosingTilePos(ApplicationConstants.DOWNLOAD_TILE_ZOOM) in corridor.tiles)
    }

    @Test fun `oversized corridor is refused before any download`() = runBlocking {
        assertFailsWith<RouteCorridorTooLargeException> {
            prepareRouteQuestCorridor(listOf(start, start.translate(60000.0, 90.0)))
        }
        Unit
    }

    @Test fun `points use twenty five meter distance`() {
        val matcher = RouteQuestMatcher(route)
        val middle = start.translate(500.0, 90.0)
        assertTrue(matcher.matches(osmQuest(geometry = ElementPointGeometry(middle.translate(24.0, 0.0)))))
        assertFalse(matcher.matches(osmQuest(geometry = ElementPointGeometry(middle.translate(26.0, 0.0)))))
    }

    @Test fun `crossing way counts even when endpoints are far away`() {
        val middle = start.translate(500.0, 90.0)
        val line = listOf(middle.translate(500.0, 0.0), middle.translate(500.0, 180.0))
        assertTrue(RouteQuestMatcher(route).matches(osmQuest(geometry = ElementPolylinesGeometry(listOf(line), middle))))
    }

    @Test fun `large overlapping way bounds alone do not count`() {
        val middle = start.translate(500.0, 90.0)
        val line = listOf(start.translate(200.0, 270.0).translate(200.0, 180.0),
            start.translate(200.0, 270.0).translate(200.0, 0.0),
            end.translate(200.0, 90.0).translate(200.0, 0.0))
        assertFalse(RouteQuestMatcher(route).matches(osmQuest(geometry = ElementPolylinesGeometry(listOf(line), middle))))
    }

    @Test fun `active types only and keys are deduplicated across corridor queries`() {
        val quest = osmQuest(geometry = ElementPointGeometry(start))
        val note = osmNoteQuest(pos = start)
        val counter = RouteQuestCounter(route, setOf(QUEST_TYPE.name))
        listOf(quest, quest, note, quest).forEach(counter::add)
        assertEquals<Set<QuestKey>>(setOf(quest.key), counter.keys)
        val disabled = RouteQuestCounter(route, emptySet())
        listOf(quest, note).forEach(disabled::add)
        assertTrue(disabled.keys.isEmpty())
    }

    @Test fun `notes count only when their type is active and obey proximity and deduplication`() {
        val near = osmNoteQuest(id = 1, pos = start.translate(24.0, 0.0))
        val far = osmNoteQuest(id = 2, pos = start.translate(40.0, 0.0))
        val enabled = RouteQuestCounter(route, setOf(near.type.name))
        listOf(near, near, far).forEach(enabled::add)
        assertEquals<Set<QuestKey>>(setOf(near.key), enabled.keys)
        val disabled = RouteQuestCounter(route, setOf(QUEST_TYPE.name))
        listOf(near, far).forEach(disabled::add)
        assertTrue(disabled.keys.isEmpty())
    }

    @Test fun `shared tile candidates fetched once and counted per route with live filters`() = runBlocking {
        val tile = start.enclosingTilePos(ApplicationConstants.DOWNLOAD_TILE_ZOOM)
        val near = osmNoteQuest(id = 1, pos = start)
        val hidden = osmNoteQuest(id = 2, pos = start)
        val inactive = osmQuest(geometry = ElementPointGeometry(start))
        val shifted = route.map { it.translate(100.0, 0.0) }
        val plans = listOf(RouteQuestCorridor(route, setOf(tile)), RouteQuestCorridor(shifted, setOf(tile)))
        var fetches = 0
        val keys = queryRouteQuestCandidates(plans, setOf(near.type.name),
            fetch = { fetches++; listOf(near, near, hidden, inactive) },
            resolve = { key -> if (key == hidden.key) null else listOf(near, inactive).first { it.key == key } })
        assertEquals(1, fetches)
        assertEquals<Set<QuestKey>>(setOf(near.key), keys[0])
        assertTrue(keys[1].isEmpty())
    }

    @Test fun `download batches contain only missing tiles and stay small`() {
        val missing = (0..18).filter { it != 5 }.map { TilePos(it, 1) } + TilePos(0, 2)
        val batches = routeDownloadBatches(missing)
        assertEquals(missing.toSet(), batches.flatMap { it.asTilePosSequence().toList() }.toSet())
        assertTrue(batches.all { it.size <= 8 })
    }
}
