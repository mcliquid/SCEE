package de.westnordost.streetcomplete.quests.surface

import android.app.Application
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import de.westnordost.osmfeatures.FeatureDictionary
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.osm.surface.Surface
import de.westnordost.streetcomplete.quests.tracktype.AddTracktype
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.ui.common.quest.LocalElement
import de.westnordost.streetcomplete.ui.common.quest.LocalQuestType
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS")
class SurfaceTracktypeConflictFormTest {
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

    @After fun closeKoin() {
        testKoin.close()
    }

    @Test fun `conflict overview shows current values and actions`() {
        Prefs.preferences = preferences
        val element = way(tags = mapOf(
            "highway" to "track",
            "surface" to "asphalt",
            "tracktype" to "grade3",
        ))
        val quest: OsmElementQuestType<*> = AddTracktype()
        compose.setContent {
            KoinIsolatedContext(testKoin) {
                MaterialTheme {
                    CompositionLocalProvider(
                        LocalQuestType provides quest,
                        LocalElement provides element,
                    ) {
                        SurfaceTracktypeConflictForm(
                            tags = element.tags,
                            countryInfo = CountryInfo("US", emptyList()),
                            on = {},
                            onBack = {},
                            initialSurface = Surface.ASPHALT,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("surface_tracktype_conflict_overview").assertIsDisplayed()
        compose.onNodeWithTag("surface_tracktype_conflict_values").assertIsDisplayed()
        compose.onNodeWithText("Correct surface material…").assertIsDisplayed()
        compose.onNodeWithText("Correct track firmness…").assertIsDisplayed()
        compose.onNodeWithText("Both values are correct").assertIsDisplayed()
    }
}