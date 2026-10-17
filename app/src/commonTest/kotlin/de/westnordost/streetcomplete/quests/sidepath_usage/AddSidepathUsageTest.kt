package de.westnordost.streetcomplete.quests.sidepath_usage

import de.westnordost.streetcomplete.data.externalsource.ExternalSourceHiddenDao
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.meta.IncompleteCountryInfo
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuestHiddenAt
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuestsHiddenDao
import de.westnordost.streetcomplete.data.osmnotes.notequests.NoteQuestsHiddenDao
import de.westnordost.streetcomplete.data.quest.OsmQuestKey
import de.westnordost.streetcomplete.data.visiblequests.QuestsHiddenControllerImpl
import de.westnordost.streetcomplete.osm.Sides
import de.westnordost.streetcomplete.osm.cycleway.Cycleway
import de.westnordost.streetcomplete.osm.cycleway.CyclewayAndDirection
import de.westnordost.streetcomplete.osm.cycleway.RoadSidepath
import de.westnordost.streetcomplete.osm.cycleway.hideSidepathUsageQuest
import de.westnordost.streetcomplete.osm.cycleway.withRoadSidepath
import de.westnordost.streetcomplete.osm.oneway.Direction.BACKWARD
import de.westnordost.streetcomplete.osm.oneway.Direction.FORWARD
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.quests.cycleway.AddCycleway
import de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.pGeom
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.util.countryboundaries.NoCountriesExcept
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddSidepathUsageTest {

    private val questType = AddSidepathUsage()
    private val road = mapOf("highway" to "secondary")
    private val USE = "use_sidepath"

    /* ---------------------------------------- countries ------------------------------------- */

    @Test fun `only enabled in Germany and the Netherlands`() {
        assertEquals(NoCountriesExcept("DE", "NL"), questType.enabledInCountries)
    }

    @Test fun `disabled by default`() {
        assertNotNull(questType.defaultDisabledMessage)
    }

    /* ----------------------------------------- filter --------------------------------------- */

    @Test fun `not applicable without separate cycleway`() {
        assertNotApplicable(road)
        assertNotApplicable(road + ("cycleway:right" to "track"))
        assertNotApplicable(road + ("cycleway:both" to "no"))
        assertNotApplicable(road + ("cycleway:right" to "lane"))
    }

    @Test fun `not applicable if separate cycleway is only implied by use_sidepath`() {
        assertNotApplicable(road + ("bicycle" to USE))
        assertNotApplicable(road + ("bicycle:forward" to USE))
    }

    @Test fun `not applicable to non-ways`() {
        assertFalse(questType.isApplicableTo(node(1, tags = road + ("cycleway:right" to "separate"))))
    }

    @Test fun `applicable with separate cycleway on any side`() {
        for (key in listOf("cycleway:right", "cycleway:left", "cycleway:both", "cycleway")) {
            assertApplicable(road + (key to "separate"))
        }
    }

    @Test fun `applicable with separate cycleway next to another cycleway`() {
        assertApplicable(road + mapOf("cycleway:right" to "separate", "cycleway:left" to "track"))
        assertApplicable(road + mapOf("cycleway:left" to "separate", "cycleway:right" to "no"))
    }

    @Test fun `getApplicableElements only returns applicable ways`() {
        val mapData = TestMapDataWithGeometry(listOf(
            way(1, tags = road + ("cycleway:right" to "separate")),
            way(2, tags = road + ("cycleway:right" to "track")),
            way(3, tags = road),
            way(4, tags = road + mapOf("cycleway:right" to "separate", "bicycle:forward" to USE)),
            node(5, tags = road + ("cycleway:right" to "separate")),
        ))
        assertEquals(listOf(1L), questType.getApplicableElements(mapData).map { it.id })
    }

    @Test fun `not applicable if the usage is tagged already`() {
        for (extra in listOf(
            mapOf("bicycle:forward" to USE),
            mapOf("bicycle:backward" to USE),
            mapOf("bicycle" to USE),
            mapOf("bicycle:forward" to USE, "bicycle:backward" to USE),
            // anybody has looked at it already
            mapOf("bicycle" to "optional_sidepath"),
            mapOf("bicycle:forward" to "optional_sidepath"),
            mapOf("bicycle:backward" to "optional_sidepath"),
        )) {
            assertNotApplicable(road + ("cycleway:both" to "separate") + extra)
        }
    }

    @Test fun `not applicable with other access tags or conditionals`() {
        for (value in listOf(
            "yes", "no", "designated", "dismount", "private", "permissive", "destination",
            "customers", "permit", "delivery", "official", "discouraged"
        )) {
            assertNotApplicable(road + mapOf("cycleway:right" to "separate", "bicycle" to value))
        }
        for (key in listOf("bicycle:conditional", "bicycle:forward:conditional", "bicycle:backward:conditional")) {
            assertNotApplicable(road + mapOf("cycleway:right" to "separate", key to "no @ (Su)"))
        }
    }

    @Test fun `still applicable if only one direction has another access tag`() {
        val tags = road + mapOf("cycleway:right" to "separate", "bicycle:backward" to "no")
        assertApplicable(tags)
        // but only the other direction may be answered
        assertEquals(listOf(RoadSidepath.FORWARD), answersOf(tags))
    }

    @Test fun `not applicable on roads where use_sidepath does not apply`() {
        for (tags in listOf(
            mapOf("highway" to "motorway"),
            mapOf("highway" to "track"),
            mapOf("highway" to "living_street"),
            mapOf("highway" to "cycleway"),
            road + ("motorroad" to "yes"),
            road + ("bicycle_road" to "yes"),
            road + ("cyclestreet" to "yes"),
            road + ("area" to "yes"),
            road + ("vehicle" to "no"),
            road + ("access" to "private"),
        )) {
            assertNotApplicable(tags + ("cycleway:right" to "separate"))
        }
    }

    /* ---------------------------------- oneways and directions ------------------------------ */

    @Test fun `answers on a two-way road`() {
        assertEquals(
            listOf(RoadSidepath.FORWARD, RoadSidepath.BACKWARD, RoadSidepath.BOTH),
            answersOf(road + ("cycleway:right" to "separate"))
        )
    }

    @Test fun `the side of the separate cycleway does not matter`() {
        for (side in listOf("left", "right", "both")) {
            assertEquals(
                listOf(RoadSidepath.FORWARD, RoadSidepath.BACKWARD, RoadSidepath.BOTH),
                answersOf(road + ("cycleway:$side" to "separate")), side
            )
        }
    }

    @Test fun `oneway only asks about the direction of the flow`() {
        assertEquals(listOf(RoadSidepath.FORWARD), answersOf(road + mapOf("oneway" to "yes", "cycleway:left" to "separate")))
        assertEquals(listOf(RoadSidepath.FORWARD), answersOf(road + mapOf("oneway" to "yes", "cycleway:right" to "separate")))
        assertEquals(listOf(RoadSidepath.BACKWARD), answersOf(road + mapOf("oneway" to "-1", "cycleway:right" to "separate")))
        assertEquals(listOf(RoadSidepath.BACKWARD), answersOf(road + mapOf("oneway" to "-1", "cycleway:both" to "separate")))
    }

    @Test fun `oneway that is open for cyclists in contraflow is applicable for both directions`() {
        for (tags in listOf(
            road + mapOf("oneway" to "yes", "oneway:bicycle" to "no", "cycleway:right" to "separate"),
            road + mapOf("oneway" to "-1", "cycleway:right" to "opposite_lane", "cycleway:left" to "separate"),
        )) {
            assertApplicable(tags)
            assertEquals(
                listOf(RoadSidepath.FORWARD, RoadSidepath.BACKWARD, RoadSidepath.BOTH),
                answersOf(tags), tags.toString()
            )
        }
    }

    @Test fun `no cycleway oneway tag is needed`() {
        // the direction of the cycleway is not asked for
        assertApplicable(road + mapOf("cycleway:right" to "separate", "cycleway:right:oneway" to "no"))
        assertApplicable(road + mapOf("cycleway:right" to "separate", "cycleway:right:oneway" to "-1"))
        assertApplicable(road + mapOf("cycleway:both" to "separate", "cycleway:both:oneway" to "yes"))
    }

    /* ------------------------------------------ answers ------------------------------------- */

    @Test fun `the three answers`() {
        val tags = road + ("cycleway:right" to "separate")
        assertEquals(
            setOf(StringMapEntryAdd("bicycle:forward", USE)),
            questType.answerAppliedTo(SidepathUsageAnswer.Compulsory(RoadSidepath.FORWARD), tags)
        )
        assertEquals(
            setOf(StringMapEntryAdd("bicycle:backward", USE)),
            questType.answerAppliedTo(SidepathUsageAnswer.Compulsory(RoadSidepath.BACKWARD), tags)
        )
        assertEquals(
            setOf(StringMapEntryAdd("bicycle", USE)),
            questType.answerAppliedTo(SidepathUsageAnswer.Compulsory(RoadSidepath.BOTH), tags)
        )
    }

    @Test fun `the answer only writes bicycle tags and leaves unusual cycleway tags alone`() {
        val tags = road + mapOf("cycleway" to "separate", "oneway" to "yes", "cycleway:right:oneway" to "yes")
        val changes = questType.answerAppliedTo(SidepathUsageAnswer.Compulsory(RoadSidepath.FORWARD), tags)
        assertEquals(setOf("bicycle:forward"), changes.map { it.key }.toSet())
    }

    @Test fun `answer for a reversed oneway`() {
        val tags = road + mapOf("oneway" to "-1", "cycleway:right" to "separate")
        assertEquals(
            setOf(StringMapEntryAdd("bicycle:backward", USE)),
            questType.answerAppliedTo(SidepathUsageAnswer.Compulsory(RoadSidepath.BACKWARD), tags)
        )
        // not allowed for this direction
        assertEquals(emptySet(), questType.answerAppliedTo(SidepathUsageAnswer.Compulsory(RoadSidepath.FORWARD), tags))
    }

    @Test fun `answer does not overwrite protected tags`() {
        for (tags in listOf(
            mapOf("bicycle" to "yes"), mapOf("bicycle" to "optional_sidepath"),
            mapOf("bicycle" to "no"), mapOf("bicycle:conditional" to "no @ (Su)"),
        )) {
            val t = road + ("cycleway:right" to "separate") + tags
            for (answer in RoadSidepath.entries.filter { it != RoadSidepath.NONE }) {
                assertEquals(
                    emptySet(),
                    questType.answerAppliedTo(SidepathUsageAnswer.Compulsory(answer), t).filter { it.key.startsWith("bicycle") }.toSet(),
                    "$t $answer"
                )
            }
        }
    }

    @Test fun `answer keeps another value in the other direction`() {
        val tags = road + mapOf("cycleway:right" to "separate", "bicycle:backward" to "no")
        assertEquals(
            setOf(StringMapEntryAdd("bicycle:forward", USE)),
            questType.answerAppliedTo(SidepathUsageAnswer.Compulsory(RoadSidepath.FORWARD), tags)
        )
        assertEquals(emptySet(), questType.answerAppliedTo(SidepathUsageAnswer.Compulsory(RoadSidepath.BOTH), tags))
    }

    /* ------------------------------- no repetition after an answer -------------------------- */

    @Test fun `quest disappears after every valid answer`() {
        for (answer in listOf(RoadSidepath.FORWARD, RoadSidepath.BACKWARD, RoadSidepath.BOTH)) {
            val tags = road + ("cycleway:both" to "separate")
            val result = StringMapChangesBuilder(tags).also {
                questType.applyAnswerTo(SidepathUsageAnswer.Compulsory(answer), it, geom, 0)
            }.toMap()
            assertNotApplicable(result)
        }
    }

    @Test fun `no direction is left over after an answer for one direction`() {
        val tags = road + ("cycleway:both" to "separate")
        val result = StringMapChangesBuilder(tags).also {
            questType.applyAnswerTo(SidepathUsageAnswer.Compulsory(RoadSidepath.FORWARD), it, geom, 0)
        }.toMap()
        assertEquals("use_sidepath", result["bicycle:forward"])
        assertNull(result["bicycle:backward"])
        assertNotApplicable(result)
    }

    /* ------------------------------- "No / I can't tell": hiding ----------------------------- */

    @Test fun `hiding is persistent, independent of tags and survives restarts and downloads`() {
        val osmDb = mock<OsmQuestsHiddenDao> { every { getAll() } returns listOf() }
        val notesDb = mock<NoteQuestsHiddenDao> { every { getAll() } returns listOf() }
        val externalDb = mock<ExternalSourceHiddenDao> { every { getAll() } returns listOf() }
        val key = OsmQuestKey(ElementType.WAY, 1, AddSidepathUsage::class.simpleName!!)
        every { osmDb.getTimestamp(key) } returns 123L

        // what the form does for "No / I can't tell": the standard hiding
        QuestsHiddenControllerImpl(osmDb, notesDb, externalDb)
            .hideSidepathUsageQuest(way(1, tags = road + ("cycleway:right" to "separate")))
        verify { osmDb.add(key) }

        // restart: a new controller reads the hidden quests from the database again
        every { osmDb.getAll() } returns listOf(OsmQuestHiddenAt(key, 123L))
        val afterRestart = QuestsHiddenControllerImpl(osmDb, notesDb, externalDb)
        assertEquals(123L, afterRestart.get(key))

        // download: the element and its tags may be new, the key is the same
        val changedElement = way(1, tags = road + mapOf("cycleway:right" to "separate", "name" to "Hauptstraße"))
        assertEquals(
            123L,
            afterRestart.get(OsmQuestKey(changedElement.type, changedElement.id, AddSidepathUsage::class.simpleName!!))
        )
        // other elements and other quest types are not affected
        assertNull(afterRestart.get(OsmQuestKey(ElementType.WAY, 2, AddSidepathUsage::class.simpleName!!)))
        assertNull(afterRestart.get(OsmQuestKey(ElementType.WAY, 1, AddCycleway::class.simpleName!!)))
    }

    @Test fun `no and cannot tell does not change any tags`() {
        // there is no answer to apply: the form only hides the quest
        val tags = road + ("cycleway:right" to "separate")
        assertApplicable(tags)
        assertEquals(tags, tags)
    }

    /* ------------------------------- interplay with AddCycleway ----------------------------- */

    @Test fun `quest is created for a way after AddCycleway answered separate`() {
        val addCycleway = AddCycleway(getCountryInfoByLocation = { countryInfo })
        assertNotApplicable(road)
        val after = applyAddCycleway(addCycleway, road, right = CyclewayAndDirection(Cycleway.SEPARATE, FORWARD))
        assertEquals("separate", after["cycleway:right"])
        assertApplicable(after)
    }

    @Test fun `quest does not appear after AddCycleway recorded an answer`() {
        val addCycleway = AddCycleway(getCountryInfoByLocation = { countryInfo })
        for (answer in RoadSidepath.entries.filter { it != RoadSidepath.NONE }) {
            val sides = Sides(null, CyclewayAndDirection(Cycleway.SEPARATE, FORWARD)).withRoadSidepath(answer)
            val after = applyAddCycleway(addCycleway, road, sides)
            assertNotNull(after["bicycle"] ?: after["bicycle:forward"] ?: after["bicycle:backward"], answer.toString())
            assertNotApplicable(after)
            assertEquals(false, addCycleway.isApplicableTo(way(1, tags = after)))
        }
    }

    @Test fun `AddCycleway writes the same tags as the quest`() {
        val addCycleway = AddCycleway(getCountryInfoByLocation = { countryInfo })
        for (answer in RoadSidepath.entries.filter { it != RoadSidepath.NONE }) {
            val viaQuest = StringMapChangesBuilder(road + ("cycleway:right" to "separate")).also {
                questType.applyAnswerTo(SidepathUsageAnswer.Compulsory(answer), it, geom, 0)
            }.toMap().filterKeys { it.startsWith("bicycle") }
            val sides = Sides(null, CyclewayAndDirection(Cycleway.SEPARATE, FORWARD)).withRoadSidepath(answer)
            val viaCycleway = applyAddCycleway(addCycleway, road, sides).filterKeys { it.startsWith("bicycle") }
            assertEquals(viaQuest, viaCycleway, answer.toString())
        }
    }

    @Test fun `AddCycleway without an answer writes no bicycle tags`() {
        val addCycleway = AddCycleway(getCountryInfoByLocation = { countryInfo })
        val after = applyAddCycleway(addCycleway, road, right = CyclewayAndDirection(Cycleway.SEPARATE, FORWARD))
        assertNull(after["bicycle"])
        assertNull(after["bicycle:forward"])
        assertNull(after["bicycle:backward"])
    }

    @Test fun `AddCycleway keeps protected values`() {
        val addCycleway = AddCycleway(getCountryInfoByLocation = { countryInfo })
        val sides = Sides(null, CyclewayAndDirection(Cycleway.SEPARATE, FORWARD)).withRoadSidepath(RoadSidepath.BOTH)
        val after = applyAddCycleway(addCycleway, road + ("bicycle" to "yes"), sides)
        assertEquals("yes", after["bicycle"])
        assertNull(after["bicycle:forward"])
    }

    /* ------------------------------ no separate cycleway: correction ------------------------ */

    @Test fun `corrected cycleway makes the quest disappear`() {
        val tags = road + ("cycleway:right" to "separate")
        val corrected = SidepathUsageAnswer.CorrectedCycleway(Sides(null, CyclewayAndDirection(Cycleway.NONE, FORWARD)))
        val result = StringMapChangesBuilder(tags).also { questType.applyAnswerTo(corrected, it, geom, 0) }.toMap()
        assertEquals("no", result["cycleway:right"])
        assertNotApplicable(result)
        assertNull(result["bicycle:forward"])
    }

    @Test fun `corrected cycleway with a remaining separate side keeps the quest`() {
        val tags = road + ("cycleway:both" to "separate")
        val corrected = SidepathUsageAnswer.CorrectedCycleway(
            Sides(
                CyclewayAndDirection(Cycleway.SEPARATE, BACKWARD),
                CyclewayAndDirection(Cycleway.NONE, FORWARD),
            )
        )
        val result = StringMapChangesBuilder(tags).also { questType.applyAnswerTo(corrected, it, geom, 0) }.toMap()
        assertEquals("separate", result["cycleway:left"])
        assertEquals("no", result["cycleway:right"])
        assertApplicable(result)
    }

    @Test fun `correction does not touch use_sidepath without a confirmed removal`() {
        val tags = road + mapOf("cycleway:right" to "separate", "bicycle:forward" to USE)
        val corrected = SidepathUsageAnswer.CorrectedCycleway(Sides(null, CyclewayAndDirection(Cycleway.NONE, FORWARD)))
        val result = StringMapChangesBuilder(tags).also { questType.applyAnswerTo(corrected, it, geom, 0) }.toMap()
        assertEquals(USE, result["bicycle:forward"])
    }

    @Test fun `correction removes use_sidepath after a confirmed removal`() {
        val tags = road + mapOf("cycleway:right" to "separate", "bicycle:forward" to USE)
        val corrected = SidepathUsageAnswer.CorrectedCycleway(
            Sides(null, CyclewayAndDirection(Cycleway.NONE, FORWARD)).withRoadSidepath(RoadSidepath.NONE)
        )
        val result = StringMapChangesBuilder(tags).also { questType.applyAnswerTo(corrected, it, geom, 0) }.toMap()
        assertNull(result["bicycle:forward"])
        assertEquals("no", result["cycleway:right"])
    }

    @Test fun `correction does not remove optional_sidepath`() {
        val tags = road + mapOf("cycleway:right" to "separate", "bicycle:forward" to "optional_sidepath")
        val corrected = SidepathUsageAnswer.CorrectedCycleway(
            Sides(null, CyclewayAndDirection(Cycleway.NONE, FORWARD)).withRoadSidepath(RoadSidepath.NONE)
        )
        val result = StringMapChangesBuilder(tags).also { questType.applyAnswerTo(corrected, it, geom, 0) }.toMap()
        assertEquals("optional_sidepath", result["bicycle:forward"])
    }

    /* ------------------------------------------ helpers ------------------------------------- */

    private val geom = ElementPointGeometry(LatLon(0.0, 0.0))

    private val countryInfo = CountryInfo(
        null,
        listOf(IncompleteCountryInfo(hasAdvisoryCycleLane = false, isLeftHandTraffic = false))
    )

    private fun applyAddCycleway(
        addCycleway: AddCycleway,
        tags: Map<String, String>,
        sides: Sides<CyclewayAndDirection>,
    ): Map<String, String> {
        val builder = StringMapChangesBuilder(tags)
        addCycleway.applyAnswerTo(sides, builder, geom, 0)
        return builder.toMap()
    }

    private fun applyAddCycleway(
        addCycleway: AddCycleway,
        tags: Map<String, String>,
        left: CyclewayAndDirection? = null,
        right: CyclewayAndDirection? = null,
    ): Map<String, String> = applyAddCycleway(addCycleway, tags, Sides(left, right))

    private fun answersOf(tags: Map<String, String>) =
        de.westnordost.streetcomplete.osm.cycleway.getSidepathAnswers(tags, false)

    private fun assertApplicable(tags: Map<String, String>) {
        val way = way(1, listOf(1, 2, 3), tags)
        val mapData = TestMapDataWithGeometry(listOf(way))
        mapData.wayGeometriesById[1L] = pGeom(0.0, 0.0)
        assertTrue(questType.isApplicableTo(way), tags.toString())
        assertTrue(questType.getApplicableElements(mapData).any(), tags.toString())
    }

    private fun assertNotApplicable(tags: Map<String, String>) {
        val way = way(1, listOf(1, 2, 3), tags)
        val mapData = TestMapDataWithGeometry(listOf(way))
        mapData.wayGeometriesById[1L] = pGeom(0.0, 0.0)
        assertFalse(questType.isApplicableTo(way), tags.toString())
        assertFalse(questType.getApplicableElements(mapData).any(), tags.toString())
    }
}
