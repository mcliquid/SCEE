package de.westnordost.streetcomplete.quests.cycleway_separation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import de.westnordost.osmfeatures.FeatureDictionary
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.meta.IncompleteCountryInfo
import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.osm.cycleway_separate.*
import de.westnordost.streetcomplete.testutils.*
import de.westnordost.streetcomplete.ui.common.quest.*
import dev.mokkery.mock
import kotlinx.io.files.SystemFileSystem
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CyclewaySeparationDesktopUiTest {
    @get:Rule val compose = createComposeRule()
    private val preferences = inMemoryPrefs()
    private val tags = mapOf("highway" to "cycleway")
    private val testKoin = koinApplication {
        modules(module {
            single<Preferences> { preferences }
            single<MapDataWithEditsSource> { mock() }
            single { FeatureDictionary.create(SystemFileSystem, "src/commonMain/composeResources/files/osmfeatures/default") }
        })
    }
    @After fun closeKoin() { testKoin.close() }

    @Test fun `mouse restores equal and asymmetric previews in native desktop layouts`() {
        var size by mutableStateOf(640)
        var same by mutableStateOf(false)
        fun save() = saveSeparationLastPicked(preferences, tags, CyclewaySeparationAnswer(CyclewaySeparation.KERB,
            if (same) CyclewaySeparation.KERB else CyclewaySeparation.FENCE))
        save()
        Prefs.preferences = preferences
        compose.setContent {
            KoinIsolatedContext(testKoin) { MaterialTheme {
                key(size, same) {
                    val quest = AddCyclewaySeparation()
                    val element = way(tags = tags)
                    CompositionLocalProvider(LocalQuestType provides quest, LocalElement provides element,
                        LocalMapRotation provides -45f) {
                        Box(Modifier.requiredWidth(size.dp)) {
                            AddCyclewaySeparationForm({}, element, pGeom(),
                                CountryInfo("DE", listOf(IncompleteCountryInfo(isLeftHandTraffic = false))), preferences)
                        }
                    }
                }
            } }
        }
        for (width in listOf(640, 900)) for (equal in listOf(false, true)) {
            compose.runOnIdle { size = width; same = equal; save() }
            compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("street-side-last-picked")),
                useUnmergedTree = true).onFirst().performMouseInput { click() }
            compose.waitForIdle()
            val parent = compose.onNodeWithTag("separation-preview").getUnclippedBoundsInRoot()
            val left = compose.onNodeWithTag("separation-selection-LEFT", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val right = compose.onNodeWithTag("separation-selection-RIGHT", useUnmergedTree = true).getUnclippedBoundsInRoot()
            for (bounds in listOf(left, right)) assertTrue(bounds.left >= parent.left && bounds.right <= parent.right &&
                bounds.top >= parent.top && bounds.bottom <= parent.bottom)
            assertTrue(left.right <= right.left || right.right <= left.left || left.bottom <= right.top || right.bottom <= left.top)
        }
    }
}
