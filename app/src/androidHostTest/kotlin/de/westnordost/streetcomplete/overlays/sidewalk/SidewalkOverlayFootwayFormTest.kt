package de.westnordost.streetcomplete.overlays.sidewalk

import android.app.Application
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.westnordost.osmfeatures.FeatureDictionary
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.meta.IncompleteCountryInfo
import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryModify
import de.westnordost.streetcomplete.data.osm.edits.update_tags.UpdateElementTagsAction
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.overlays.Edit
import de.westnordost.streetcomplete.data.overlays.OverlayAction
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.preferences.getLastPicked
import de.westnordost.streetcomplete.data.preferences.setLastPicked
import de.westnordost.streetcomplete.osm.footway.FootwayKind
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.way
import dev.mokkery.mock
import kotlinx.io.files.SystemFileSystem
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** Routing of the sidewalk overlay to its forms and the footway=* form for highway=footway */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS")
class SidewalkOverlayFootwayFormTest {
    @get:Rule val compose = createComposeRule()

    private val preferences = inMemoryPrefs()
    private val testKoin = koinApplication {
        modules(module {
            single<Preferences> { preferences }
            single<MapDataWithEditsSource> { mock() }
            single {
                FeatureDictionary.create(
                    SystemFileSystem,
                    "src/commonMain/composeResources/files/osmfeatures/default",
                )
            }
        })
    }

    private val FAVORITE_KEY = "FootwayKindForm"
    private val overlay = SidewalkOverlay()
    private val actions = ArrayList<OverlayAction>()

    private val geometry = ElementPolylinesGeometry(
        listOf(listOf(LatLon(50.0, 10.0), LatLon(50.0001, 10.0001))),
        LatLon(50.00005, 10.00005)
    )

    @After fun closeKoin() {
        testKoin.close()
    }

    private fun show(tags: Map<String, String>) {
        Prefs.preferences = preferences
        compose.setContent {
            KoinIsolatedContext(testKoin) {
                MaterialTheme {
                    overlay.Form(
                        on = { actions.add(it) },
                        element = way(1, nodes = listOf(1, 2), tags = tags),
                        geometry = geometry,
                        countryInfo = CountryInfo("DE", listOf(IncompleteCountryInfo(isLeftHandTraffic = false))),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun select(label: String) {
        // the card with the currently selected item opens the selection dialog
        compose.onAllNodesWithText("footway", substring = true).onFirst().performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText(label).onLast().performClick()
        compose.waitForIdle()
    }

    private fun clickOk() {
        compose.onNodeWithContentDescription("OK").performClick()
        compose.waitForIdle()
    }

    private val changes get() = ((actions.single() as Edit).value as UpdateElementTagsAction).changes.changes

    /* ------------------------------------------ routing ----------------------------------------- */

    @Test fun `road shows the sidewalk form`() {
        show(mapOf("highway" to "residential"))
        // the street side form of the sidewalk form: one "Select" button per untagged side
        compose.onAllNodesWithText("Select").assertCountEquals(2)
        compose.onNodeWithText("Not specified").assertDoesNotExist()
        assertEquals(emptyList(), actions)
    }

    @Test fun `road with footway tag still shows the sidewalk form`() {
        show(mapOf("highway" to "residential", "footway" to "sidewalk"))
        compose.onAllNodesWithText("Select").assertCountEquals(2)
        compose.onNodeWithText("Sidewalk").assertDoesNotExist()
    }

    @Test fun `path and cycleway keep the separate cycleway form`() {
        show(mapOf("highway" to "path", "foot" to "designated", "bicycle" to "designated"))
        compose.onNodeWithText("Designated shared-use path").assertIsDisplayed()
        compose.onNodeWithText("Not specified").assertDoesNotExist()
        assertEquals(emptyList(), actions)
    }

    @Test fun `cycleway with footway tag keeps the separate cycleway form`() {
        show(mapOf("highway" to "cycleway", "cycleway" to "crossing"))
        compose.onNodeWithText("Bike path").assertIsDisplayed()
        compose.onNodeWithText("Crossing").assertDoesNotExist()
    }

    @Test fun `steps get no form that could retag them`() {
        assertNoForm(mapOf("highway" to "steps"))
    }

    @Test fun `bridleway gets no form that could retag it`() {
        assertNoForm(mapOf("highway" to "bridleway", "foot" to "designated", "bicycle" to "designated"))
    }

    private fun assertNoForm(tags: Map<String, String>) {
        show(tags)
        compose.onNodeWithText("Generic path", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Designated shared-use path").assertDoesNotExist()
        compose.onNodeWithText("Not specified").assertDoesNotExist()
        compose.onNodeWithContentDescription("OK").assertDoesNotExist()
        assertEquals(emptyList(), actions)
    }

    /* ------------------------------------------ footway form ------------------------------------ */

    @Test fun `missing footway is shown as not specified and opening changes nothing`() {
        show(mapOf("highway" to "footway"))
        compose.onNodeWithText("Not specified").assertIsDisplayed()
        compose.onNodeWithText("No footway tag").assertIsDisplayed()
        clickOk() // disabled, nothing changed
        assertEquals(emptyList(), actions)
    }

    @Test fun `supported value is preselected and confirming it unchanged changes nothing`() {
        show(mapOf("highway" to "footway", "footway" to "crossing"))
        compose.onNodeWithText("Crossing").assertIsDisplayed()
        compose.onNodeWithText("footway=crossing").assertIsDisplayed()
        clickOk()
        assertEquals(emptyList(), actions)
    }

    @Test fun `unsupported value is shown with its raw value and can not be confirmed`() {
        show(mapOf("highway" to "footway", "footway" to "link"))
        compose.onNodeWithText("Other value (not selectable)").assertIsDisplayed()
        compose.onNodeWithText("footway=link").assertIsDisplayed()
        compose.onNodeWithText("Not specified").assertDoesNotExist()
        compose.onNodeWithContentDescription("OK").assertDoesNotExist()
        assertEquals(emptyList(), actions)
    }

    @Test fun `link is not offered for selection`() {
        show(mapOf("highway" to "footway"))
        compose.onAllNodesWithText("footway", substring = true).onFirst().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Sidewalk").assertIsDisplayed()
        compose.onNodeWithText("Access aisle").assertIsDisplayed()
        compose.onNodeWithText("footway=link").assertDoesNotExist()
        compose.onNodeWithText("Other value (not selectable)").assertDoesNotExist()
    }

    @Test fun `set kind`() {
        show(mapOf("highway" to "footway", "surface" to "asphalt"))
        select("Sidewalk")
        clickOk()
        assertEquals(setOf(StringMapEntryAdd("footway", "sidewalk")), changes)
    }

    @Test fun `replace kind`() {
        show(mapOf("highway" to "footway", "footway" to "sidewalk", "crossing" to "marked"))
        select("Traffic island")
        clickOk()
        assertEquals(setOf(StringMapEntryModify("footway", "sidewalk", "traffic_island")), changes)
    }

    @Test fun `replace unsupported value by explicit selection`() {
        show(mapOf("highway" to "footway", "footway" to "alley"))
        select("Access aisle")
        clickOk()
        assertEquals(setOf(StringMapEntryModify("footway", "alley", "access_aisle")), changes)
    }

    @Test fun `not specified explicitly removes footway only`() {
        show(mapOf("highway" to "footway", "footway" to "crossing", "kerb" to "lowered", "foot" to "designated"))
        select("Not specified")
        clickOk()
        assertEquals(setOf(StringMapEntryDelete("footway", "crossing")), changes)
    }

    @Test fun `confirmed kind is remembered as last picked`() {
        show(mapOf("highway" to "footway"))
        select("Crossing")
        clickOk()
        assertEquals(listOf(FootwayKind.CROSSING), preferences.getLastPicked<FootwayKind>(FAVORITE_KEY))
    }

    @Test fun `last picked kinds are shown with their short label`() {
        preferences.setLastPicked(FAVORITE_KEY, listOf(FootwayKind.TRAFFIC_ISLAND))
        show(mapOf("highway" to "footway"))
        compose.onNodeWithText("Traffic island").assertIsDisplayed().performClick()
        compose.waitForIdle()
        clickOk()
        assertEquals(setOf(StringMapEntryAdd("footway", "traffic_island")), changes)
    }

    @Test fun `unsupported value is never remembered as last picked`() {
        show(mapOf("highway" to "footway", "footway" to "link"))
        clickOkIfPresent()
        assertEquals(emptyList(), preferences.getLastPicked<FootwayKind>(FAVORITE_KEY))
        assertEquals(emptyList(), actions)
    }

    private fun clickOkIfPresent() {
        val ok = compose.onAllNodesWithContentDescription("OK").fetchSemanticsNodes()
        if (ok.isNotEmpty()) clickOk()
    }

    @Test fun `selecting again the original value is no change`() {
        show(mapOf("highway" to "footway", "footway" to "sidewalk"))
        select("Crossing")
        select("Sidewalk")
        clickOk()
        assertEquals(emptyList(), actions)
    }
}
