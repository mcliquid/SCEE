package de.westnordost.streetcomplete.screens.main.bottom_sheet.split_way

import android.app.Application
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.data.osm.edits.split_way.SplitAtLinePosition
import de.westnordost.streetcomplete.data.osm.edits.split_way.SplitAtPoint
import de.westnordost.streetcomplete.data.osm.edits.split_way.SplitPolylineAtPosition
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.testutils.SnapshotMapSettings
import de.westnordost.streetcomplete.testutils.p
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.ui.common.quest.LocalMapMetersPerDp
import de.westnordost.streetcomplete.util.sound.SoundEffectPlayer
import dev.mokkery.MockMode
import dev.mokkery.mock
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The hint where the way must be moved to is shown exactly as long as the way is not snapped */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS")
class SplitWayFormTest {
    @get:Rule val compose = createComposeRule()

    // a way of ~111m per segment along the equator. With 0.1 m/dp, it is snapped to within a
    // few meters only
    private val positions = listOf(p(0.0, -0.001), p(0.0, 0.0), p(0.0, 0.001))
    private val way = way(1, listOf(1, 2, 3))
    private val geometry = ElementPolylinesGeometry(listOf(positions), positions[1])

    private val farAway = p(0.001, 0.0005)
    private val alsoFarAway = p(-0.002, -0.0003)
    private val onSegment = p(0.0, 0.0005)
    private val onVertex = p(0.0, 0.0)

    private val mapPosition = mutableStateOf<LatLon?>(farAway)
    private var confirmed: List<SplitPolylineAtPosition>? = null
    private var dismissed = false

    private val testKoin = koinApplication {
        modules(module {
            single { Preferences(SnapshotMapSettings()) }
            single<SoundEffectPlayer> { mock(MockMode.autoUnit) }
        })
    }
    @After fun closeKoin() { testKoin.close() }

    private fun show() = compose.setContent {
        KoinIsolatedContext(testKoin) {
            MaterialTheme {
                CompositionLocalProvider(LocalMapMetersPerDp provides 0.1) {
                    SplitWayForm(
                        onConfirmed = { confirmed = it },
                        onDismiss = { dismissed = true },
                        mapPosition = mapPosition.value,
                        way = way,
                        wayGeometry = geometry,
                    )
                }
            }
        }
    }

    private fun moveMapTo(position: LatLon?) = compose.runOnIdle { mapPosition.value = position }

    private val snapTarget get() = compose.onNodeWithTag(SPLIT_WAY_SNAP_TARGET_TAG)
    private val splitButton get() = compose.onNodeWithContentDescription("Split way")

    private fun assertNotSnapped() {
        snapTarget.assertExists()
        splitButton.assertIsNotEnabled()
    }

    private fun assertSnapped() {
        snapTarget.assertDoesNotExist()
        splitButton.assertIsEnabled()
    }

    @Test fun `snap target is shown right away when the way is out of reach`() {
        show()
        assertNotSnapped()
    }

    @Test fun `snap target is shown when the map position is not known yet`() {
        mapPosition.value = null
        show()
        assertNotSnapped()
    }

    @Test fun `snap target stays at the center of the screen when the map is moved`() {
        show()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val before = snapTarget.getUnclippedBoundsInRoot()
        assertCentersMatch(root, before)
        moveMapTo(alsoFarAway)
        assertEquals(before, snapTarget.getUnclippedBoundsInRoot())
    }

    @Test fun `snap target is replaced by the snap feedback when the way gets in reach and returns when it leaves`() {
        show()
        moveMapTo(onSegment)
        assertSnapped()
        moveMapTo(farAway)
        assertNotSnapped()
    }

    @Test fun `cannot split while the way is out of reach`() {
        show()
        splitButton.performScrollTo().performClick()
        compose.onNodeWithContentDescription("Undo").assertDoesNotExist()
        compose.onNodeWithContentDescription("OK").assertDoesNotExist()
        assertNull(confirmed)
    }

    @Test fun `splits once at the snapped position on the segment`() {
        mapPosition.value = onSegment
        show()
        splitButton.performScrollTo().performClick()
        moveMapTo(farAway)
        // the cut stays, even though there is nothing to snap to anymore
        assertNotSnapped()
        compose.onNodeWithContentDescription("OK").performClick()
        val split = assertIs<SplitAtLinePosition>(confirmed?.single())
        assertEquals(positions[1], split.pos1)
        assertEquals(positions[2], split.pos2)
        assertEquals(0.5, split.delta, 1e-6)
    }

    @Test fun `snaps to an existing vertex`() {
        mapPosition.value = onVertex
        show()
        assertSnapped()
        splitButton.performScrollTo().performClick()
        compose.onNodeWithContentDescription("OK").performClick()
        assertEquals(listOf<SplitPolylineAtPosition>(SplitAtPoint(positions[1])), confirmed)
    }

    @Test fun `does not snap to the ends of the way`() {
        mapPosition.value = positions.first()
        show()
        assertNotSnapped()
        moveMapTo(positions.last())
        assertNotSnapped()
    }

    @Test fun `undoing the cut returns to the state before`() {
        mapPosition.value = onSegment
        show()
        splitButton.performScrollTo().performClick()
        compose.onNodeWithContentDescription("Undo").performClick()
        compose.onNodeWithContentDescription("OK").assertDoesNotExist()
        assertSnapped()
        assertNull(confirmed)
    }

    @Test fun `cancelling without cuts dismisses the form`() {
        show()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        assertTrue(dismissed)
        assertNull(confirmed)
    }

    private fun assertCentersMatch(a: DpRect, b: DpRect) {
        assertTrue(abs((a.left + a.right - b.left - b.right).value) <= 1f, "$a vs $b")
        assertTrue(abs((a.top + a.bottom - b.top - b.bottom).value) <= 1f, "$a vs $b")
    }
}
