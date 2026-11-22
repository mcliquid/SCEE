package de.westnordost.streetcomplete.ui.common.quest

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.quests.access_restriction.AgriculturalAccessSignGraphic
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.westnordost.osmfeatures.FeatureDictionary
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.osm.osmquests.Action
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.traffic_signs.TrafficSignCatalogues
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignTestData
import de.westnordost.streetcomplete.quests.access_restriction.AgriculturalAccessSign
import de.westnordost.streetcomplete.quests.access_restriction.CheckAgriculturalAccess
import de.westnordost.streetcomplete.quests.answerAppliedTo
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
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS")
class AgriculturalAccessFormTest {
    @get:Rule val compose = createComposeRule()
    private val preferences = inMemoryPrefs()
    private val catalogues: TrafficSignCatalogues = TrafficSignTestData.bundledCatalogues
    private val quest = CheckAgriculturalAccess()
    private val actions = mutableListOf<QuestAction<AgriculturalAccessSign>>()
    private var notes = 0
    private var hidden = 0
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
            single { catalogues }
        })
    }
    @After fun closeKoin() { testKoin.close() }

    private fun show(bicycle: String? = null) {
        Prefs.preferences = preferences
        val tags = mapOf("highway" to "track", "access" to "agricultural") +
            (bicycle?.let { mapOf("bicycle" to it) } ?: emptyMap())
        val element = way(nodes = listOf(1, 2, 3), tags = tags)
        compose.setContent {
            KoinIsolatedContext(testKoin) {
                MaterialTheme {
                    CompositionLocalProvider(LocalQuestType provides quest, LocalElement provides element) {
                        var cantSay by remember { mutableStateOf(false) }
                        quest.Form(on = {
                            actions += it
                            if (it == Action.CantSay) cantSay = true
                        }, element = element, geometry = pGeom(), countryInfo = CountryInfo(null, emptyList()))
                        if (cantSay) CantSayDialog(
                            onDismissRequest = { cantSay = false },
                            onLeaveNote = { notes++ },
                            onHideQuest = { hidden++ },
                            onDisableQuest = {},
                            onCreateCustomQuest = null,
                        )
                    }
                }
            }
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `bundled catalogue renders sign 250 without fallback border`() {
        compose.setContent {
            KoinIsolatedContext(testKoin) {
                MaterialTheme {
                    AgriculturalAccessSignGraphic(
                        AgriculturalAccessSign.NO_VEHICLES_WITH_EXCEPTION,
                        "agricultural",
                        Modifier.testTag("sign250"),
                    )
                }
            }
        }
        val sign250Name = catalogues.get("DE")!!.signs.single { it.osmValuePart == "250" }.descriptiveName
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription(sign250Name, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(sign250Name, useUnmergedTree = true).assertExists()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `all sign combinations render from catalogue`() {
        compose.setContent {
            KoinIsolatedContext(testKoin) {
                MaterialTheme {
                    Row(Modifier.testTag("signs")) {
                        AgriculturalAccessSign.entries.forEach {
                            AgriculturalAccessSignGraphic(it, "agricultural;forestry", Modifier.width(104.dp))
                        }
                    }
                }
            }
        }
        val de = catalogues.get("DE")!!
        val main260 = de.signs.single { it.osmValuePart == "260" }.descriptiveName
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription(main260).fetchSemanticsNodes().isNotEmpty()
        }
        val target = File("../output/6177-review/sign-combinations.png")
        target.parentFile?.mkdirs()
        target.outputStream().use {
            assertTrue(compose.onNodeWithTag("signs").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        compose.onNodeWithContentDescription(main260).assertExists()
        compose.onAllNodesWithContentDescription(
            de.signs.single { it.osmValuePart == "1026-38" }.descriptiveName,
        ).fetchSemanticsNodes().let { assertTrue(it.size >= 3) }
        compose.onNodeWithContentDescription(de.signs.single { it.osmValuePart == "1022-10" }.descriptiveName).assertExists()
    }

    @Test fun `German quest resources contain all translated answers and safety guidance`() {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
            InputSource(File("src/commonMain/composeResources/values-de/strings.xml").reader(Charsets.UTF_8)))
        val nodes = doc.getElementsByTagName("string")
        val strings = (0 until nodes.length).map { nodes.item(it) }
            .filter { it.attributes.getNamedItem("name").nodeValue.startsWith("quest_accessRestriction_") }
            .associate { it.attributes.getNamedItem("name").nodeValue to it.textContent }
        assertEquals(7, strings.size)
        assertEquals(
            "Gemeinsamer Geh- und Radweg, mit dieser Ausnahme",
            strings["quest_accessRestriction_shared_foot_cycle"],
        )
        assertEquals("Keine Fahrzeuge, mit dieser Ausnahme", strings["quest_accessRestriction_no_vehicles"])
        assertEquals("Keine Kraftfahrzeuge, mit dieser Ausnahme", strings["quest_accessRestriction_no_motor_vehicles"])
        assertEquals("Keine Fahrzeuge, Radverkehr frei, mit dieser Ausnahme", strings["quest_accessRestriction_no_vehicles_bicycles"])
        assertEquals("Keines dieser Schilder", strings["quest_accessRestriction_other"])
        assertTrue(strings.getValue("quest_accessRestriction_title").contains("beiden Richtungen"))
        assertTrue(strings.getValue("quest_accessRestriction_hint").contains("Bestehende spezifische Zugangsbeschr\u00e4nkungen"), strings.getValue("quest_accessRestriction_hint"))
    }

    @Test fun `none of these signs offers a note without dispatching a tag answer`() {
        show()
        compose.onNodeWithText("Uh\u2026").performScrollTo().performClick()
        compose.onNodeWithText("None of these signs").performClick()
        compose.onNodeWithText("Leave note").performClick()
        assertTrue(actions.single() == Action.CantSay)
        assertEquals(1, notes)
        assertEquals(0, hidden)
    }

    @Test fun `cant say offers local hiding without dispatching a tag answer`() {
        show()
        compose.onNodeWithText("Uh\u2026").performScrollTo().performClick()
        compose.onNodeWithText("Can\u2019t say\u2026").performClick()
        compose.onNodeWithText("Hide").performClick()
        assertTrue(actions.single() == Action.CantSay)
        assertEquals(0, notes)
        assertEquals(1, hidden)
    }

    @Test fun `bicycle conflict removes only the bicycle exemption answer`() {
        show("no")
        compose.onNodeWithText("No vehicles, bicycles allowed, with this exception").assertDoesNotExist()
        compose.onNodeWithText("No vehicles, with this exception").assertExists()
        compose.onNodeWithText("No motor vehicles, with this exception").assertExists()
    }

    @Test fun `confirmed sign answer removes access and closes the quest`() {
        show()
        compose.onNodeWithText("No motor vehicles, with this exception").performClick()
        assertTrue(actions.isEmpty())
        compose.onNodeWithContentDescription("OK").performClick()
        val answer = actions.single() as Answer<AgriculturalAccessSign>
        val tags = mutableMapOf("highway" to "track", "access" to "agricultural")
        quest.answerAppliedTo(answer.value, tags).forEach { it.applyTo(tags) }
        assertEquals(mapOf("highway" to "track", "motor_vehicle" to "agricultural",
            "traffic_sign" to "DE:260,1026-36"), tags)
        assertFalse(quest.isApplicableTo(way(nodes = listOf(1, 2, 3), tags = tags)))
    }
}