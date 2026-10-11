package de.westnordost.streetcomplete.data.routing

import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.data.importGpx
import de.westnordost.streetcomplete.data.download.Downloader
import de.westnordost.streetcomplete.data.download.tiles.DownloadedTilesSource
import de.westnordost.streetcomplete.data.download.tiles.TilePos
import de.westnordost.streetcomplete.data.download.tiles.TilesRect
import de.westnordost.streetcomplete.data.download.tiles.enclosingTilesRect
import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolygonsGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.quest.Quest
import de.westnordost.streetcomplete.data.quest.QuestKey
import de.westnordost.streetcomplete.data.quest.VisibleQuestsSource
import de.westnordost.streetcomplete.data.visiblequests.VisibleEditTypeSource
import de.westnordost.streetcomplete.util.ktx.nowAsEpochMilliseconds
import de.westnordost.streetcomplete.util.logs.Log
import de.westnordost.streetcomplete.util.math.area
import de.westnordost.streetcomplete.util.math.distanceTo
import de.westnordost.streetcomplete.util.math.distanceToArcs
import de.westnordost.streetcomplete.util.math.enclosingBoundingBox
import de.westnordost.streetcomplete.util.math.enlargedBy
import de.westnordost.streetcomplete.util.math.intersect
import de.westnordost.streetcomplete.util.math.intersectsWith
import de.westnordost.streetcomplete.util.math.initialBearingTo
import de.westnordost.streetcomplete.util.math.translate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import kotlin.math.ceil

const val ROUTE_QUEST_DISTANCE_METERS = 25.0
const val ROUTE_DOWNLOAD_DISTANCE_METERS = 100.0
private const val ZOOM = ApplicationConstants.DOWNLOAD_TILE_ZOOM

data class RouteQuestCorridor(val points: List<LatLon>, val tiles: Set<TilePos>)
class RouteCorridorTooLargeException : Exception()

/** Separate from routing: never requests or changes a route. */
interface RouteQuestDataSource {
    val changes: Flow<Unit>
    suspend fun prepare(points: List<LatLon>): RouteQuestCorridor
    suspend fun load(corridor: RouteQuestCorridor)
    suspend fun isCovered(corridor: RouteQuestCorridor): Boolean
    suspend fun quests(corridor: RouteQuestCorridor): Set<QuestKey>
    suspend fun questsFor(corridors: List<RouteQuestCorridor>): List<Set<QuestKey>> = corridors.map { quests(it) }
}

class RouteQuestService(
    private val downloader: Downloader,
    private val downloaded: DownloadedTilesSource,
    private val visibleQuests: VisibleQuestsSource,
    private val visibleTypes: VisibleEditTypeSource,
) : RouteQuestDataSource {
    override val changes: Flow<Unit> = callbackFlow {
        val questsListener = object : VisibleQuestsSource.Listener {
            override fun onUpdated(added: Collection<Quest>, removed: Collection<QuestKey>) { trySend(Unit) }
            override fun onInvalidated() { trySend(Unit) }
        }
        val tilesListener = object : DownloadedTilesSource.Listener {
            override fun onUpdated() { trySend(Unit) }
        }
        visibleQuests.addListener(questsListener)
        downloaded.addListener(tilesListener)
        awaitClose {
            visibleQuests.removeListener(questsListener)
            downloaded.removeListener(tilesListener)
        }
    }

    override suspend fun prepare(points: List<LatLon>): RouteQuestCorridor = prepareRouteQuestCorridor(points)

    override suspend fun isCovered(corridor: RouteQuestCorridor): Boolean = withContext(Dispatchers.IO) {
        val fresh = freshSince()
        corridor.tiles.all { downloaded.contains(it.toTilesRect(), fresh) }
    }

    override suspend fun load(corridor: RouteQuestCorridor) = withContext(Dispatchers.IO) {
        val start = nowAsEpochMilliseconds()
        try {
            val missing = corridor.tiles.filter { !downloaded.contains(it.toTilesRect(), freshSince()) }
            for (tiles in routeDownloadBatches(missing)) {
                currentCoroutineContext().ensureActive()
                if (!downloaded.contains(tiles, freshSince())) {
                    // Await the real downloader including quest generation and coverage commit.
                    // No WorkManager enqueue/REPLACE, no viewport dependency, no cache bypass.
                    downloader.download(tiles.asBoundingBox(ZOOM), isUserInitiated = false, ignoreCache = false)
                }
            }
        } finally {
            Log.i("RouteQuests", "Corridor download took ${nowAsEpochMilliseconds() - start} ms")
        }
    }

    override suspend fun quests(corridor: RouteQuestCorridor): Set<QuestKey> = questsFor(listOf(corridor)).single()

    override suspend fun questsFor(corridors: List<RouteQuestCorridor>): List<Set<QuestKey>> = withContext(Dispatchers.IO) {
        val start = nowAsEpochMilliseconds()
        val activeTypes = visibleTypes.getVisible().mapTo(hashSetOf()) { it.name }
        val keys = queryRouteQuestCandidates(corridors, activeTypes,
            fetch = { tile -> visibleQuests.getAll(tile.asBoundingBox(ZOOM)) },
            resolve = visibleQuests::get)
        Log.i("RouteQuests", "Quest comparison took ${nowAsEpochMilliseconds() - start} ms (${keys.map { it.size }})")
        keys
    }

    private fun freshSince() = (nowAsEpochMilliseconds() - ApplicationConstants.REFRESH_DATA_AFTER).coerceAtLeast(0)
}

/** Query shared tiles once, while retaining each route's position-index and geometry rules. */
internal suspend fun queryRouteQuestCandidates(
    corridors: List<RouteQuestCorridor>,
    activeTypes: Set<String>,
    fetch: (TilePos) -> Collection<Quest>,
    resolve: (QuestKey) -> Quest?,
): List<Set<QuestKey>> {
    val counters = corridors.map { RouteQuestCounter(it.points, activeTypes) }
    // This remains a minimum count: long ways/polygons with their quest position outside
    // the corridor tiles can be omitted even when all download tiles are fresh.
    for (tile in corridors.flatMap { it.tiles }.toSet()) {
        currentCoroutineContext().ensureActive()
        val relevant = corridors.indices.filter { tile in corridors[it].tiles }
        for (candidate in fetch(tile)) {
            currentCoroutineContext().ensureActive()
            val quest = resolve(candidate.key) ?: continue
            for (index in relevant) counters[index].add(quest)
        }
    }
    return counters.map { it.keys.toSet() }
}

/** Small adjoining runs of missing tiles; never fill gaps with already fresh tiles. */
internal fun routeDownloadBatches(tiles: Collection<TilePos>): List<TilesRect> {
    val result = mutableListOf<TilesRect>()
    for (tile in tiles.distinct().sortedWith(compareBy<TilePos> { it.y }.thenBy { it.x })) {
        val last = result.lastOrNull()
        if (last != null && last.top == tile.y && last.right + 1 == tile.x && last.size < 8) {
            result[result.lastIndex] = TilesRect(last.left, last.top, tile.x, tile.y)
        } else result.add(tile.toTilesRect())
    }
    return result
}

internal class RouteQuestCounter(points: List<LatLon>, private val activeTypes: Set<String>) {
    private val matcher = RouteQuestMatcher(points)
    private val examined = hashSetOf<QuestKey>()
    val keys = hashSetOf<QuestKey>()

    fun add(quest: Quest) {
        if (examined.add(quest.key) && quest.type.name in activeTypes && matcher.matches(quest)) keys.add(quest.key)
    }
}

/** Exact proximity after bounding-box pruning; large way bounds alone never qualify a quest. */
class RouteQuestMatcher(points: List<LatLon>) {
    private val segments = points.zipWithNext().map { (a, b) ->
        val line = listOf(a, b)
        // enlargedBy translates corners diagonally, so sqrt(2) is needed for 25m on each axis.
        line to line.enclosingBoundingBox().enlargedBy(ROUTE_QUEST_DISTANCE_METERS * sqrt(2.0))
    }

    fun matches(quest: Quest): Boolean {
        val geometry = quest.geometry
        return segments.any { (route, bounds) ->
            if (!geometry.bounds.intersect(bounds)) false
            else when (geometry) {
                is ElementPointGeometry -> quest.position.distanceToArcs(route) <= ROUTE_QUEST_DISTANCE_METERS
                is ElementPolylinesGeometry -> geometry.polylines.any { near(it, route) }
                is ElementPolygonsGeometry -> geometry.polygons.any { near(it, route) }
            }
        }
    }

    private fun near(line: List<LatLon>, route: List<LatLon>): Boolean = line.isNotEmpty() && (
        (line.size > 1 && line.intersectsWith(route)) ||
        line.any { it.distanceToArcs(route) <= ROUTE_QUEST_DISTANCE_METERS } ||
        route.any { it.distanceToArcs(line) <= ROUTE_QUEST_DISTANCE_METERS }
    )
}

internal suspend fun prepareRouteQuestCorridor(points: List<LatLon>, distance: Double = ROUTE_DOWNLOAD_DISTANCE_METERS): RouteQuestCorridor = withContext(Dispatchers.Default) {
    require(points.size >= 2)
    // The existing tile/corridor implementation does not cover a dateline crossing or poles.
    require(points.all { kotlin.math.abs(it.latitude) < 85.0 } &&
        points.zipWithNext().all { (a, b) -> kotlin.math.abs(a.longitude - b.longitude) < 180.0 })
    val tiles = linkedSetOf<TilePos>()
    var area = 0.0
    // GPX's box merging becomes expensive for a long track. Process bounded, overlapping
    // pieces, and stop at the area limit before processing the rest of an oversized route.
    for (piece in routeCorridorPieces(points)) {
        currentCoroutineContext().ensureActive()
        val imported = importGpx(piece, false, distance).getOrThrow()
        for (bbox in imported.downloadBBoxes) {
            require(!bbox.crosses180thMeridian)
            for (tile in bbox.enclosingTilesRect(ZOOM).asTilePosSequence()) {
                if (tiles.add(tile)) area += tile.asBoundingBox(ZOOM).area() / 1e6
                if (area > ApplicationConstants.MAX_DOWNLOADABLE_AREA_IN_SQKM) throw RouteCorridorTooLargeException()
            }
        }
    }
    RouteQuestCorridor(points, tiles)
}

private fun routeCorridorPieces(points: List<LatLon>): Sequence<List<LatLon>> = sequence {
    var piece = mutableListOf(points.first())
    var length = 0.0
    for ((a, b) in points.zipWithNext()) {
        val distance = a.distanceTo(b)
        val parts = ceil(distance / 1000).toInt().coerceAtLeast(1)
        val bearing = a.initialBearingTo(b)
        for (part in 1..parts) {
            val next = if (part == parts) b else a.translate(distance * part / parts, bearing)
            val step = piece.last().distanceTo(next)
            if (piece.size >= 256 || (piece.size > 1 && length + step > 2000)) {
                yield(piece)
                piece = mutableListOf(piece.last())
                length = 0.0
            }
            piece.add(next)
            length += step
        }
    }
    yield(piece)
}

