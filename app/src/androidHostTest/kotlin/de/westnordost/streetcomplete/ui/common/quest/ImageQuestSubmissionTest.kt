package de.westnordost.streetcomplete.ui.common.quest

import android.app.Application
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.westnordost.osmfeatures.FeatureDictionary
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.quests.paving_stones_material.AddPavingStonesMaterial
import de.westnordost.streetcomplete.quests.paving_stones_material.PavingStonesMaterial
import de.westnordost.streetcomplete.screens.main.bottom_sheet.EditSubmission
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.pGeom
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS")
class ImageQuestSubmissionTest {
    @get:Rule val compose = createComposeRule()
    private val preferences = inMemoryPrefs()
    private val quest = AddPavingStonesMaterial()
    private val element = way(tags = mapOf("surface" to "paving_stones"))
    private val testKoin = koinApplication {
        modules(module {
            single<Preferences> { preferences }
            single<MapDataWithEditsSource> { mock() }
            single { FeatureDictionary.create(SystemFileSystem, "src/commonMain/composeResources/files/osmfeatures/default") }
        })
    }
    @After fun closeKoin() { testKoin.close() }

    private fun show(content: @Composable () -> Unit) {
        Prefs.preferences = preferences
        compose.setContent {
            KoinIsolatedContext(testKoin) {
                MaterialTheme {
                    CompositionLocalProvider(LocalQuestType provides quest, LocalElement provides element) { content() }
                }
            }
        }
    }

    @Test fun `paving material click dispatches answer immediately and repeated clicks persist once`() {
        val submission = EditSubmission()
        val answers = mutableListOf<PavingStonesMaterial>()
        show {
            quest.Form(on = { action ->
                if (action is Answer && action.value is PavingStonesMaterial) {
                    submission.run { answers += action.value as PavingStonesMaterial }
                }
            }, element = element, geometry = pGeom(), countryInfo = CountryInfo(null, emptyList()))
        }
        compose.onNodeWithText("Bricks").performClick()
        compose.onNodeWithText("Bricks").performClick()
        assertEquals(listOf(PavingStonesMaterial.BRICK), answers)
        val tags = element.tags.toMutableMap()
        quest.answerAppliedTo(answers.single(), element.tags).forEach { it.applyTo(tags) }
        assertEquals("brick", tags["paving_stones:material"])
    }

    @Test fun `form without opt in still requires OK`() {
        val answers = mutableListOf<String>()
        show {
            ItemSelectQuestForm(on = { if (it is Answer) answers += it.value },
                items = listOf("choice"), itemContent = { Text(it) }, preferences = preferences)
        }
        compose.onNodeWithText("choice").performClick()
        assertEquals(emptyList(), answers)
        compose.onNodeWithContentDescription("OK").performClick()
        assertEquals(listOf("choice"), answers)
    }
}
