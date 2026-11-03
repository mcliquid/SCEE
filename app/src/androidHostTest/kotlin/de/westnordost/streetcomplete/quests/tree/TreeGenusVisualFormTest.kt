package de.westnordost.streetcomplete.quests.tree

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.cheonjaeung.compose.grid.SimpleGridCells
import de.westnordost.osmfeatures.FeatureDictionary
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.meta.IncompleteCountryInfo
import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.osm.osmquests.Action
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.preferences.getLastPicked
import de.westnordost.streetcomplete.quests.orchard_produce.AddOrchardProduce
import de.westnordost.streetcomplete.quests.orchard_produce.AddOrchardProduceForm
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.produce_apple
import de.westnordost.streetcomplete.resources.tree_genus_acer
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import de.westnordost.streetcomplete.ui.common.item_select.ItemSelectGrid
import de.westnordost.streetcomplete.ui.common.item_select.ItemsSelectGrid
import de.westnordost.streetcomplete.ui.common.quest.ItemSelectQuestForm
import de.westnordost.streetcomplete.ui.common.quest.LocalElement
import de.westnordost.streetcomplete.ui.common.quest.LocalQuestType
import de.westnordost.streetcomplete.ui.theme.AppTheme
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import io.github.vinceglb.filekit.initializer.FileKitInitializer
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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

@RunWith(RobolectricTestRunner::class)

@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS")

@GraphicsMode(GraphicsMode.Mode.NATIVE)

@OptIn(ExperimentalTestApi::class)

class TreeGenusVisualFormTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val preferences = inMemoryPrefs()

    private val actions = mutableListOf<QuestAction<TreeAnswer>>()

    private var advanced = false

    private val testKoin = koinApplication {

        modules(module {

            single<Preferences> { preferences }

            single<MapDataWithEditsSource> { mock<MapDataWithEditsSource>().also { source ->
                every { source.getWaysForNode(any()) } returns emptyList()
            } }

            single { FeatureDictionary.create(SystemFileSystem, "src/commonMain/composeResources/files/osmfeatures/default") }

        })

    }

    @After fun closeKoin() { testKoin.close() }

    private fun show(orchard: Boolean = false, content: (@Composable () -> Unit)? = null,
        actualForm: Boolean = false, restoration: StateRestorationTester? = null) {

        Prefs.preferences = preferences

        if (actualForm) FileKitInitializer().create(compose.activity)
        val body: @Composable () -> Unit = {

            KoinIsolatedContext(testKoin) {

                AppTheme(darkTheme = false) {

                    CompositionLocalProvider(

                        LocalQuestType provides if (orchard) AddOrchardProduce() else AddTreeGenus(),

                        LocalElement provides if (orchard) way(tags = mapOf("landuse" to "orchard"))
                            else node(tags = mapOf("natural" to "tree")),

                    ) {

                        if (content != null) content()
                        else if (orchard) AddOrchardProduceForm({}, CountryInfo("DE", listOf(IncompleteCountryInfo(
                            orchardProduces = listOf("apple", "pear", "cherry", "plum", "walnut", "chestnut")
                        ))))
                        else if (actualForm) TreeGenusForm({ actions += it }, CountryInfo("DE", emptyList()))
                        else TreeGenusVisualForm(on = { actions += it }, onAdvanced = { advanced = true })

                    }

                }

            }

        }

        if (restoration != null) restoration.setContent(body) else compose.setContent(body)
    }

    @Test fun `image selection directly submits scientific genus without dialog`() {

        show()

        openSearch()
        compose.onNodeWithText("Search tree names").performTextInput("oak")

        compose.onNodeWithText("Oak").performScrollTo().performClick()

        val answer = (actions.single() as Answer).value as Tree

        assertEquals("Quercus", answer.name)

        assertEquals(false, answer.isSpecies)

        compose.onNodeWithText("Identification examples").assertDoesNotExist()

        compose.onNodeWithContentDescription("OK").assertDoesNotExist()

        assertTrue(preferences.getLastPicked<String>("AddTreeGenusForm").contains("false§Quercus"))

    }

    @Test fun `search filters without submitting`() {

        show()

        openSearch()
        compose.onNodeWithText("Search tree names").performTextInput("birch")

        compose.onNodeWithText("Birch").performScrollTo()

        compose.onNodeWithText("Oak").assertDoesNotExist()

        assertTrue(actions.isEmpty())

    }

    @Test fun `uncertainty uses non-answer action and advanced route emits no answer`() {

        show()

        openSearch()
        compose.onNodeWithText("Search tree names").performTextInput("not in catalog")

        compose.onNodeWithText("Uh…").performScrollTo().performClick()

        compose.onNodeWithText("Enter genus or species as text").performClick()

        assertTrue(advanced)

        assertTrue(actions.isEmpty())

        compose.onNodeWithText("Uh…").performScrollTo().performClick()

        compose.onNodeWithText("Not sure / not listed").performClick()

        assertEquals(listOf<QuestAction<TreeAnswer>>(Action.CantSay), actions)

    }

    @Test

    @Config(qualifiers = "de-rDE-w360dp-h800dp-mdpi")

    fun `German chestnut search shows both choices without selecting either`() {

        show()

        val first = compose.onNodeWithText("Ahorn").fetchSemanticsNode().boundsInRoot

        val second = compose.onNodeWithText("Eiche").fetchSemanticsNode().boundsInRoot

        val third = compose.onNodeWithText("Buche").fetchSemanticsNode().boundsInRoot

        assertTrue(first.left < second.left && second.left < third.left)

        assertTrue(kotlin.math.abs(first.top - second.top) < 2f)

        compose.onRoot().performTouchInput { swipeUp() }
        compose.onNodeWithText("Ahorn").performScrollTo()
        capture("grid-de-360")

        openSearch("Baumnamen suchen", "Äh …")
        compose.onNodeWithText("Baumnamen suchen").performTextInput("Kastanie")

        compose.onNodeWithText("Rosskastanie").performScrollTo()

        compose.onNodeWithText("Edelkastanie").performScrollTo()
        capture("grid-de-search")

        assertTrue(actions.isEmpty())

    }

    private fun openSearch(label: String = "Search tree names", menu: String = "Uh…") {
        compose.onNodeWithText(menu).performScrollTo().performClick()
        compose.onNodeWithText(label).performClick()
    }

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h800dp-mdpi")
    fun `square resources use the same tile and label geometry as orchard`() {
        show(content = {
            Column {
                ItemsSelectGrid(columns = SimpleGridCells.Fixed(3), items = (1..6).toList(),
                    selectedItems = emptySet(), onSelect = { _, _ -> }, modifier = Modifier.fillMaxWidth()) {
                    ImageWithLabel(painterResource(Res.drawable.produce_apple), "Apfel", Modifier.testTag("reference-$it"))
                }
                ItemSelectGrid(columns = SimpleGridCells.Fixed(3), items = (1..6).toList(),
                    selectedItem = null, onSelect = {}, modifier = Modifier.fillMaxWidth()) {
                    ImageWithLabel(painterResource(Res.drawable.tree_genus_acer), "Ahorn", Modifier.testTag("actual-$it"))
                }
            }
        })
        val reference = (1..6).map { compose.onNodeWithTag("reference-$it", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }
        val actual = (1..6).map { compose.onNodeWithTag("actual-$it", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }
        for (i in actual.indices) {
            assertEquals(reference[i].size, actual[i].size)
            assertEquals(reference[i].left - reference[0].left, actual[i].left - actual[0].left)
            assertEquals(reference[i].top - reference[0].top, actual[i].top - actual[0].top)
        }
        assertTrue(actual[0].left < actual[1].left && actual[1].left < actual[2].left)
        assertTrue(actual[3].top > actual[0].top)
        val referenceLabels = compose.onAllNodesWithText("Apfel", useUnmergedTree = true).fetchSemanticsNodes()
        val actualLabels = compose.onAllNodesWithText("Ahorn", useUnmergedTree = true).fetchSemanticsNodes()
        for (i in actual.indices) {
            assertEquals(referenceLabels[i].boundsInRoot.height, actualLabels[i].boundsInRoot.height)
            assertEquals(referenceLabels[i].boundsInRoot.top - reference[i].top,
                actualLabels[i].boundsInRoot.top - actual[i].top)
        }
    }

    @Test fun `search survives saved instance restoration without submitting`() {
        val restoration = StateRestorationTester(compose)
        show(restoration = restoration)
        openSearch()
        compose.onNodeWithText("Search tree names").performTextInput("oak")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Oak").performScrollTo().performClick()
        assertEquals("Quercus", ((actions.single() as Answer).value as Tree).name)
    }

    @Test fun `shared selection hint remains default and can be hidden without changing selection`() {
        val hint = mutableStateOf(true)
        val answers = mutableListOf<String>()
        show(content = {
            ItemSelectQuestForm(on = { if (it is Answer) answers += it.value }, items = listOf("Choice"),
                itemContent = { Text(it) }, showSelectionHint = hint.value, submitOnSelection = true)
        })
        compose.onNodeWithText("Select one:").assertExists()
        compose.runOnIdle { hint.value = false }
        compose.onNodeWithText("Select one:").assertDoesNotExist()
        compose.onNodeWithText("Choice").performScrollTo().performClick()
        assertEquals(listOf("Choice"), answers)
    }

    @Test fun `actual advanced entry survives restoration and returns to visual grid`() {
        val restoration = StateRestorationTester(compose)
        show(actualForm = true, restoration = restoration)
        compose.onNodeWithText("Uh…").performScrollTo().performClick()
        compose.onNodeWithText("Enter genus or species as text").performClick()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        restoration.emulateSavedInstanceStateRestore()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNodeWithText("Uh…").performScrollTo().performClick()
        compose.onNodeWithText("Choose using photos").performClick()
        compose.onNodeWithText("Maple").performScrollTo()
        compose.onNodeWithContentDescription("OK").assertDoesNotExist()
        assertTrue(actions.isEmpty())
    }

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h800dp-mdpi")
    fun `actual orchard reference with the same viewport theme and font scale`() {
        show(orchard = true)
        compose.onRoot().performTouchInput { swipeUp() }
        compose.onNodeWithText("Äpfel").performScrollTo()
        val labels = listOf("Äpfel", "Birnen", "Kirschen").map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
        assertTrue(labels[0].left < labels[1].left && labels[1].left < labels[2].left)
        assertEquals(labels[0].top, labels[1].top)
        capture("orchard-de-360")
    }

    private fun capture(name: String) {

        val file = File("build/reports/tree-cleanup/$name.png")

        file.parentFile!!.mkdirs()

        file.outputStream().use {

            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)

        }

    }

}
