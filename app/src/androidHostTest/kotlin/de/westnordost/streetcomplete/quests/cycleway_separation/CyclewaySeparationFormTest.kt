package de.westnordost.streetcomplete.quests.cycleway_separation

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import de.westnordost.osmfeatures.FeatureDictionary
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.meta.IncompleteCountryInfo
import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.osm.Sides
import de.westnordost.streetcomplete.osm.cycleway_separate.*
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.testutils.*
import de.westnordost.streetcomplete.ui.common.quest.*
import de.westnordost.streetcomplete.ui.common.street_side_select.StreetSideForm
import dev.mokkery.mock
import kotlinx.io.files.SystemFileSystem
import org.jetbrains.compose.resources.painterResource
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w1200dp-h1200dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class CyclewaySeparationFormTest {
    @get:Rule val compose = createComposeRule()
    private val preferences = inMemoryPrefs()
    private val tags = mapOf("highway" to "cycleway")
    private val country = CountryInfo("DE", listOf(IncompleteCountryInfo(isLeftHandTraffic = false)))
    private val quest = AddCyclewaySeparation()
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
        compose.setContent { KoinIsolatedContext(testKoin) { MaterialTheme { content() } } }
    }

    private fun restoreLastPicked() {
        compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("street-side-last-picked")),
            useUnmergedTree = true).onFirst().performClick()
        compose.waitForIdle()
    }

    @Test fun `manual answer is remembered and shortcut restores through normal OK pipeline`() {
        var id by mutableStateOf(1L)
        val answers = mutableListOf<CyclewaySeparationAnswer>()
        show {
            key(id) {
                val element = way(id, tags = tags)
                CompositionLocalProvider(LocalQuestType provides quest, LocalElement provides element) {
                    AddCyclewaySeparationForm(on = { action ->
                        if (action is Answer) { answers += action.value; id++ }
                    }, element, pGeom(), country, preferences)
                }
            }
        }
        compose.onNodeWithTag("separation-selection-LEFT", useUnmergedTree = true).performTouchInput { click() }
        compose.onNodeWithText("Kerb / raised edge").performClick()
        compose.onNodeWithTag("separation-selection-RIGHT", useUnmergedTree = true).performTouchInput { click() }
        compose.onNodeWithText("Fence").performScrollTo().performClick()
        compose.onNodeWithContentDescription("OK").performClick()
        assertEquals(1, answers.size)
        restoreLastPicked()
        assertEquals(1, answers.size)
        compose.onNodeWithContentDescription("OK").performClick()
        assertEquals(listOf(answers.first(), answers.first()), answers)
    }

    @Test fun `shortcut preserves an existing side on reversed cycleways`() {
        var context by mutableStateOf(tags + mapOf("oneway" to "-1", "separation:left" to "kerb"))
        val answer = CyclewaySeparationAnswer(null, CyclewaySeparation.FENCE)
        val answers = mutableListOf<CyclewaySeparationAnswer>()
        saveSeparationLastPicked(preferences, context, answer)
        show {
            key(context) {
                val element = way(tags = context)
                CompositionLocalProvider(LocalQuestType provides quest, LocalElement provides element) {
                    AddCyclewaySeparationForm({ if (it is Answer) answers += it.value }, element, pGeom(), country, preferences)
                }
            }
        }
        for (existing in listOf("kerb", "kerb;greenery")) {
            compose.runOnIdle {
                val next = tags + mapOf("oneway" to "-1", "separation:left" to existing)
                saveSeparationLastPicked(preferences, next, answer)
                context = next
            }
            restoreLastPicked()
            compose.onNodeWithTag("separation-selection-LEFT", useUnmergedTree = true)
                .assertContentDescriptionEquals("Fence")
            if (';' in existing) compose.onNodeWithText("kerb;greenery", substring = true).assertExists()
            compose.onNodeWithContentDescription("OK").performClick()
            assertEquals(answer, answers.last())
        }
    }

    private data class Profile(val width: Int, val density: Float, val rotation: Float, val identical: Boolean)

    @Test fun `selected previews stay square bounded and separate at phone and desktop sizes`() {
        var profile by mutableStateOf(Profile(240, 1f, 45f, false))
        fun answer(same: Boolean) = CyclewaySeparationAnswer(CyclewaySeparation.KERB,
            if (same) CyclewaySeparation.KERB else CyclewaySeparation.FENCE)
        saveSeparationLastPicked(preferences, tags, answer(false))
        show {
            key(profile) {
                val element = way(tags = tags)
                CompositionLocalProvider(LocalQuestType provides quest, LocalElement provides element,
                    LocalMapRotation provides -profile.rotation, LocalDensity provides Density(profile.density)) {
                    Box(Modifier.requiredWidth(profile.width.dp)) {
                        AddCyclewaySeparationForm({}, element, pGeom(), country, preferences)
                    }
                }
            }
        }
        val sizes = listOf(Triple(240, 1f, 45f), Triple(360, 3f, 0f), Triple(360, 2f, 90f),
            Triple(900, 1f, 45f), Triple(640, 1.5f, 0f), Triple(240, 3f, 90f))
        for (same in listOf(false, true)) for ((width, density, rotation) in sizes) {
            compose.runOnIdle {
                saveSeparationLastPicked(preferences, tags, answer(same))
                profile = Profile(width, density, rotation, same)
            }
            restoreLastPicked()
            val parent = compose.onNodeWithTag("separation-preview").getUnclippedBoundsInRoot()
            val left = compose.onNodeWithTag("separation-selection-LEFT", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val right = compose.onNodeWithTag("separation-selection-RIGHT", useUnmergedTree = true).getUnclippedBoundsInRoot()
            for (bounds in listOf(left, right)) {
                assertEquals((bounds.right - bounds.left).value, (bounds.bottom - bounds.top).value, 1f, profile.toString())
                assertTrue(bounds.left >= parent.left && bounds.right <= parent.right &&
                    bounds.top >= parent.top && bounds.bottom <= parent.bottom, profile.toString())
            }
            assertTrue(left.right <= right.left || right.right <= left.left || left.bottom <= right.top ||
                right.bottom <= left.top, profile.toString())
            if (density == 1f && width == 240) capture("after-${if (same) "same" else "different"}", "separation-preview")
        }
    }

    @Test fun `legacy texture rendering reproduces displaced perspective images`() {
        show {
            Box(Modifier.requiredWidth(360.dp).height(200.dp).testTag("legacy-preview")) {
                StreetSideForm(value = Sides(CyclewaySeparation.KERB, CyclewaySeparation.FENCE),
                    onValueChanged = {}, getIllustrationPainter = { value, _ -> painterResource(value!!.icon) },
                    onClickSide = {}, geometryRotation = 45f, mapRotation = 0f, mapTilt = 0f,
                    isLeftHandTraffic = false)
            }
        }
        capture("before", "legacy-preview")
    }

    private fun capture(name: String, tag: String) {
        val file = File("build/reports/separation-preview/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { out ->
            compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
