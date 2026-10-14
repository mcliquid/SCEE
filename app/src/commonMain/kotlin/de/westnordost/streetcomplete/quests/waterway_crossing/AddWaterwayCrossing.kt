package de.westnordost.streetcomplete.quests.waterway_crossing

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.data.ConflictException
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.edits.ElementEditAction
import de.westnordost.streetcomplete.data.osm.edits.waterway_crossing.*
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.*
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.resources.*
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import de.westnordost.streetcomplete.util.ktx.equalsInOsm
import de.westnordost.streetcomplete.util.math.contains
import de.westnordost.streetcomplete.util.math.distanceTo
import de.westnordost.streetcomplete.util.math.enclosingBoundingBox

/** One independently identified quest per unresolved crossing. Answers edit both ways atomically. */
class AddWaterwayCrossing : OsmElementQuestType<ElementEditAction> {
    override val icon = Res.drawable.quest_waterway_crossing
    override val title = Res.string.waterway_crossing_edit
    override val wikiLink = "Key:bridge"
    override val changesetComment = "Describe waterway crossings as bridges, culverts or fords"

    override fun isApplicableTo(element: Element): Boolean? =
        if (element is Way && element.isCrossingRoad() && !element.hasCrossingStructure()
            && !element.isClosed && element.tags["area"] != "yes" && "railway" !in element.tags) null else false

    override fun getApplicableElements(mapData: MapDataWithGeometry): Iterable<Element> =
        getCrossings(mapData).map { it.road }.distinctBy { it.id }

    private val cacheLock = ReentrantLock()
    private var cachedInput: List<Element>? = null
    private var cachedCandidates = emptyList<Candidate>()

    fun getCrossings(data: MapData, bbox: BoundingBox? = null,
        preferredPositions: Map<Long, List<LatLon>> = emptyMap()): List<Candidate> = cacheLock.withLock {
        val input = crossingDetectionInput(data)
        if (input != cachedInput) {
            cachedCandidates = findCandidates(data)
            cachedInput = input
        }
        cachedCandidates.filter { bbox == null || bbox.contains(it.position) }.map { candidate ->
            val preferred = preferredPositions[candidate.road.id].orEmpty().singleOrNull { it.equalsInOsm(candidate.position) }
            candidate.copy(road = data.getWay(candidate.road.id) ?: candidate.road,
                waterway = data.getWay(candidate.waterway.id) ?: candidate.waterway,
                position = preferred ?: candidate.position)
        }.distinctBy { Triple(it.road.id, it.position.latitude, it.position.longitude) }
    }

    internal fun cachedCandidateAt(roadId: Long, position: LatLon): Candidate? = cacheLock.withLock {
        cachedCandidates.singleOrNull { it.road.id == roadId && it.position.equalsInOsm(position) }
    }

    internal fun cachedCandidatesForDeletedWays(ids: Set<Long>): List<Candidate> = cacheLock.withLock {
        cachedCandidates.filter { it.road.id in ids || it.waterway.id in ids }
    }

    private fun findCandidates(data: MapData): List<Candidate> {
        val geometryChecks = CrossingGeometryChecks()
        // Bounding-box prefilter before segment intersection, using downloaded complete nodes.
        fun geometry(way: Way): List<LatLon>? {
            if (way.hasCrossingStructure() || way.isClosed || way.tags["area"] == "yes"
                || "railway" in way.tags || way.nodeIds.size < 2
                || way.nodeIds.distinct().size != way.nodeIds.size) return null
            val positions = way.nodeIds.map { data.getNode(it)?.position ?: return null }
            return positions
        }
        val waters = data.ways.filter { it.isCrossingWaterway() }.mapNotNull { way ->
            geometry(way)?.let { Triple(way, it, it.enclosingBoundingBox()) }
        }.sortedBy { it.third.min.latitude }
        return data.ways.filter { isApplicableTo(it) == null }.flatMap { road ->
            val rp = geometry(road) ?: return@flatMap emptyList()
            val bounds = rp.enclosingBoundingBox()
            val candidates = waters.asSequence()
                .takeWhile { it.third.min.latitude <= bounds.max.latitude }
                .filter { (_, _, b) -> b.max.latitude >= bounds.min.latitude
                    && (bounds.crosses180thMeridian || b.crosses180thMeridian
                        || b.min.longitude <= bounds.max.longitude && b.max.longitude >= bounds.min.longitude) }
                .flatMap { (water, wp, _) ->
                    try {
                        geometryChecks.checkSimple(road, rp)
                        geometryChecks.checkSimple(water, wp)
                    } catch (_: ConflictException) { return@flatMap emptySequence() }
                    geometryChecks.intersections(road, water, rp, wp).mapNotNull { point ->
                        try { checkEditableCrossing(data, road, water, rp, wp, point, geometryChecks) } catch (_: ConflictException) { return@mapNotNull null }
                        if (!crossingSupportsBridgeOrCulvert(road, water, rp, point)) return@mapNotNull null
                        if (crossingContext(data, road, water, point) == null) return@mapNotNull null
                        // A ford/structure on the crossing vertex already describes this crossing.
                        if ((road.nodeIds + water.nodeIds).any { id -> data.getNode(id)?.let {
                            it.position.distanceTo(point) < 0.01
                                && it.tags.keys.any { key -> key in setOf("ford", "bridge", "tunnel", "covered", "layer", "level") }
                        } == true }) return@mapNotNull null
                        Candidate(road, water, point)
                    }.asSequence()
                }.toList()
            candidates
        }
    }

    override fun getHighlightedElements(element: Element, mapData: MapDataWithGeometry): Sequence<Element> {
        if (element !is Way) return emptySequence()
        return getCrossings(mapData).filter { it.road.id == element.id }.map { it.waterway }.distinctBy { it.id }.asSequence()
    }

    @Composable override fun Form(on: (QuestAction<ElementEditAction>) -> Unit, element: Element,
        geometry: ElementGeometry, countryInfo: CountryInfo) {
        WaterwayCrossingQuestForm(on, element, geometry, geometry.center)
    }

    override fun applyAnswerTo(answer: ElementEditAction, tags: Tags, geometry: ElementGeometry, timestampEdited: Long) =
        error("Waterway crossings must be saved as a geometry action")

    data class Candidate(val road: Way, val waterway: Way, val position: LatLon)
}
