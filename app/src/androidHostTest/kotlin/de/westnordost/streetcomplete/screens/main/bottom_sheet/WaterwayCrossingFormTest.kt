package de.westnordost.streetcomplete.screens.main.bottom_sheet

import android.app.Application
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.westnordost.streetcomplete.data.osm.edits.ElementEditAction
import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.osm.edits.waterway_crossing.WaterwayCrossingTestData
import de.westnordost.streetcomplete.data.osm.edits.waterway_crossing.CreateWaterwayFordAction
import de.westnordost.streetcomplete.data.osm.edits.waterway_crossing.SetWaterwayStructureAction
import de.westnordost.streetcomplete.data.osm.edits.waterway_crossing.WaterwayStructure
import de.westnordost.streetcomplete.data.osm.mapdata.Way
import de.westnordost.streetcomplete.data.osm.mapdata.MutableMapData
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.ui.common.quest.LocalMapMetersPerDp
import de.westnordost.streetcomplete.util.sound.SoundEffectPlayer
import com.russhwolf.settings.MapSettings
import de.westnordost.streetcomplete.data.ConflictException
import de.westnordost.osmfeatures.FeatureDictionary
import de.westnordost.streetcomplete.data.osm.osmquests.Action
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.testutils.p
import dev.mokkery.answering.calls
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import org.junit.Rule
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

/** Measures the actual bottom sheet: nested infinite-height scrolling used to crash here. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS")
class WaterwayCrossingFormTest {
    @get:Rule val compose = createComposeRule()
    private val f = WaterwayCrossingTestData()
    private var saved: ElementEditAction? = null
    private var dismissed = false
    private var questAction: Action? = null
    private var crossing = f.crossing
    private var saveCount = 0
    private val mapPosition = mutableStateOf(f.crossing.position)
    private val sound = mock<SoundEffectPlayer>()
    private val source = mock<MapDataWithEditsSource> {
        every { getWayComplete(any()) } calls { (id: Long) -> f.data.getWay(id)?.let { way ->
            MutableMapData(listOf(way) + way.nodeIds.mapNotNull { f.data.getNode(it) })
        } }
        every { getNode(any()) } calls { (id: Long) -> f.repo.getNode(id) }
        every { getWaysForNode(any()) } calls { (id: Long) -> f.repo.getWaysForNode(id) }
        every { getRelationsForNode(any()) } calls { (id: Long) -> f.repo.getRelationsForNode(id) }
        every { getRelationsForWay(any()) } calls { (id: Long) -> f.repo.getRelationsForWay(id) }
        every { getMapDataWithGeometry(any()) } calls { f.data }
    }
    private val testKoin = koinApplication {
        modules(module {
            single<MapDataWithEditsSource> { source }
            single<de.westnordost.streetcomplete.data.osm.osmquests.OsmQuestSource> {
                mock { every { getValidated(any()) } calls { null } }
            }
            single { Preferences(MapSettings()) }
            single<SoundEffectPlayer> { sound }
            single { FeatureDictionary.create(kotlinx.io.files.SystemFileSystem, "src/commonMain/composeResources/files/osmfeatures/default") }
        })
    }
    @After fun closeKoin() { testKoin.close() }

    private fun show() = compose.setContent {
        KoinIsolatedContext(testKoin) {
            MaterialTheme {
                CompositionLocalProvider(LocalMapMetersPerDp provides 0.1) {
                    WaterwayCrossingForm(crossing, mapPosition.value,
                        onDismiss = { dismissed = true },
                        onConfirmed = { saved = it; saveCount++ },
                        onQuestAction = { questAction = it; if (it == Action.Dismiss) dismissed = true })
                }
            }
        }
    }

    private fun awaitSave() = compose.waitUntil(10000) {
        saved != null || compose.onAllNodesWithText("OK").fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun `quest invalidated before form loads is dismissed instead of remaining unanswerable`() {
        f.data.putElement(f.road.copy(tags = f.road.tags + ("bridge" to "yes")))
        var closed = false
        compose.setContent {
            KoinIsolatedContext(testKoin) {
                MaterialTheme {
                    CompositionLocalProvider(de.westnordost.streetcomplete.ui.common.quest.LocalQuestType provides
                        de.westnordost.streetcomplete.quests.waterway_crossing.AddWaterwayCrossing()) {
                        de.westnordost.streetcomplete.quests.waterway_crossing.WaterwayCrossingQuestForm(
                            on = { if (it == Action.Dismiss) closed = true }, element = f.road,
                            geometry = de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry(p()), mapPosition = p())
                    }
                }
            }
        }
        compose.waitUntil(10000) { closed }
        assertNull(saved)
    }

    @Test fun `opening persisted quest includes known long road missing from local tile query`() {
        val road = f.road.copy(nodeIds = listOf(1, 4))
        f.data.putElement(road)
        f.data.putElement(node(1, p(0.0, -0.02)))
        f.data.putElement(node(4, p(0.0, 0.02)))
        every { source.getMapDataWithGeometry(any()) } calls {
            de.westnordost.streetcomplete.data.osm.mapdata.MutableMapDataWithGeometry().apply {
                f.water.nodeIds.forEach { putElement(f.data.getNode(it)!!) }
                putElement(f.water)
            }
        }
        compose.setContent {
            KoinIsolatedContext(testKoin) {
                MaterialTheme {
                    CompositionLocalProvider(de.westnordost.streetcomplete.ui.common.quest.LocalQuestType provides
                        de.westnordost.streetcomplete.quests.waterway_crossing.AddWaterwayCrossing()) {
                        de.westnordost.streetcomplete.quests.waterway_crossing.WaterwayCrossingQuestForm(
                            on = {}, element = road,
                            geometry = de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry(p()), mapPosition = p())
                    }
                }
            }
        }
        compose.waitUntil(10000) { compose.onAllNodesWithText("Bridge").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Ford").assertExists()
    }

    @Test fun `shared vertex offers only ford and saves without split or new node`() {
        crossing = f.sharedCrossing()
        show()
        compose.onNodeWithText("Bridge").assertDoesNotExist()
        compose.onNodeWithText("Culvert").assertDoesNotExist()
        compose.onNodeWithText("Ford").performClick()
        awaitSave()
        val action = assertIs<CreateWaterwayFordAction>(saved)
        assertEquals(0, action.newElementsCount.nodes)
        assertEquals(1, saveCount)
    }

    @Test fun `cancelling in flight validation never saves an edit`() {
        val started = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        every { source.getWayComplete(f.road.id) } calls {
            started.countDown()
            release.await(10, java.util.concurrent.TimeUnit.SECONDS)
            f.repo.getWayComplete(f.road.id)
        }
        show()
        compose.onNodeWithText("Ford").performScrollTo().performClick()
        compose.waitUntil(10000) { started.count == 0L }
        compose.onNodeWithText("Uh\u2026").performScrollTo().performClick()
        compose.onNodeWithText("Can\u2019t say\u2026").performClick()
        release.countDown()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Ford").fetchSemanticsNodes().isNotEmpty() }
        assertNull(saved)
        assertEquals(Action.CantSay, questAction)
    }

    @Test fun `ford answer is hidden on roads which only allow structures`() {
        crossing = crossing.copy(road = f.road.copy(tags = mapOf("highway" to "primary")))
        show()
        compose.onNodeWithText("Bridge").assertExists()
        compose.onNodeWithText("Culvert").assertExists()
        compose.onNodeWithText("Ford").assertDoesNotExist()
    }

    @Test fun `start uses regular answers and contains no technical details`() {
        show()
        compose.onNodeWithText("What is at this crossing?").assertExists()
        for (answer in listOf("Bridge", "Culvert", "Ford")) compose.onNodeWithText(answer).assertExists()
        compose.onNodeWithText("highway=", substring = true).assertDoesNotExist()
        compose.onNodeWithText("way 10", substring = true).assertDoesNotExist()
        assertNull(saved)
    }

    @Test fun `uncertain answer uses existing quest action menu`() {
        show()
        compose.onNodeWithText("Uh\u2026").performScrollTo().performClick()
        compose.onNodeWithText("Can\u2019t say\u2026").performClick()
        assertEquals(Action.CantSay, questAction)
        assertNull(saved)
    }

    @Test fun `leave note uses existing quest action menu`() {
        show()
        compose.onNodeWithText("Uh\u2026").performScrollTo().performClick()
        compose.onNodeWithText("Leave note").performClick()
        assertEquals(Action.LeaveNote, questAction)
        assertNull(saved)
    }

    @Test fun `ford saves directly without preview or confirmation`() {
        show()
        compose.onNodeWithText("Ford").performScrollTo().performClick()
        awaitSave()
        assertIs<CreateWaterwayFordAction>(saved)
        assertEquals(1, saveCount)
        compose.onNodeWithText("Preview", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Save").assertDoesNotExist()
        compose.onNodeWithText("Ford").assertDoesNotExist()
    }

    @Test fun `long selected ways absent from area query still save safely`() {
        every { source.getMapDataWithGeometry(any()) } calls {
            de.westnordost.streetcomplete.data.osm.mapdata.MutableMapDataWithGeometry()
        }
        show()
        compose.onNodeWithText("Ford").performScrollTo().performClick()
        awaitSave()
        assertIs<CreateWaterwayFordAction>(saved)
    }

    @Test fun `missing data is localized and never saved`() {
        every { source.getWayComplete(f.road.id) } calls { null }
        show()
        compose.onNodeWithText("Ford").performScrollTo().performClick()
        awaitSave()
        compose.onNodeWithText("This crossing cannot be edited automatically.").assertExists()
        compose.onNodeWithText("Crossing way deleted", substring = true).assertDoesNotExist()
        assertNull(saved)
    }

    @Test fun `ford too close to existing point has localized error and no edit`() {
        val near = p(0.0, -0.000001)
        f.data.putElement(node(2, near))
        crossing = f.crossing.copy(roadPositions = f.roadPositions.toMutableList().also { it[1] = near })
        show()
        compose.onNodeWithText("Ford").performScrollTo().performClick()
        awaitSave()
        compose.onNodeWithText("The crossing is too close to an existing point.").assertExists()
        assertNull(saved)
    }

    @Test fun `additional way at ford is rejected without technical text`() {
        f.data.putElement(node(7, p(-0.002, 0.000005)))
        f.data.putElement(node(8, p(0.002, 0.000005)))
        f.data.putElement(way(30, listOf(7, 8), mapOf("highway" to "service")))
        show()
        compose.onNodeWithText("Ford").performScrollTo().performClick()
        awaitSave()
        compose.onNodeWithText("There are several crossings here. Please edit manually.").assertExists()
        compose.onNodeWithText("Additional way", substring = true).assertDoesNotExist()
        assertNull(saved)
    }

    @Test fun `bridge saves atomically after exactly two cuts`() = structure("Bridge", f.bridge)
    @Test fun `culvert saves atomically after exactly two cuts`() = structure("Culvert", f.culvert)

    private fun cuts(label: String, action: SetWaterwayStructureAction) {
        mapPosition.value = action.splits[0].pos
        show()
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.onNodeWithText("Tap the button to split the way at the position of the crosshair.").assertExists()
        compose.onNodeWithText("Mark the start and end on the map.").assertDoesNotExist()
        compose.onNodeWithText("Save").assertDoesNotExist()
        compose.onNodeWithContentDescription("Split way").performScrollTo().performClick()
        compose.onNodeWithText("Save").assertDoesNotExist()
        assertNull(saved)
        compose.runOnIdle { mapPosition.value = action.splits[1].pos }
        compose.onNodeWithContentDescription("Split way").performScrollTo().performClick()
        compose.onNodeWithText("Save").assertExists()
        assertNull(saved)
    }

    private fun structure(label: String, action: SetWaterwayStructureAction) {
        cuts(label, action)
        compose.onNodeWithText("Save").performClick()
        awaitSave()
        val edit = assertIs<SetWaterwayStructureAction>(saved)
        assertEquals(1, saveCount)
        assertEquals(action.structure, edit.structure)
        val middle = (edit.createUpdates(f.repo, f.idProvider()).let { it.creations + it.modifications })
            .filterIsInstance<Way>().single { "bridge" in it.tags || "tunnel" in it.tags }
        assertEquals(if (action.structure == WaterwayStructure.BRIDGE) "1" else "-1", middle.tags["layer"])
        compose.onNodeWithText("Preview", substring = true).assertDoesNotExist()
    }

    @Test fun `changing layer during splitting prevents save`() {
        cuts("Bridge", f.bridge)
        compose.runOnIdle { f.data.putElement(f.road.copy(tags = f.road.tags + ("layer" to "1"))) }
        compose.onNodeWithText("Save").performClick()
        awaitSave()
        compose.onNodeWithText("This crossing cannot be edited automatically.").assertExists()
        assertNull(saved)
    }

    @Test fun `relation conflict prevents save`() {
        cuts("Bridge", f.bridge)
        every { source.getRelationsForWay(any()) } calls { throw ConflictException("Relation changed") }
        compose.onNodeWithText("Save").performClick()
        awaitSave()
        compose.onNodeWithText("This crossing cannot be edited automatically.").assertExists()
        assertNull(saved)
    }

    @Test fun `cancelling split before cuts returns to answers without saving`() {
        show()
        compose.onNodeWithText("Bridge").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.onNodeWithText("Ford").assertExists()
        assertNull(saved)
    }
}
