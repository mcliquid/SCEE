package de.westnordost.streetcomplete.quests.tracktype

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.util.ktx.nowAsEpochMilliseconds
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class AddTracktypeTest {
    private lateinit var questType: AddTracktype
    private val now = nowAsEpochMilliseconds()

    @BeforeTest fun setUp() {
        Prefs.preferences = inMemoryPrefs()
        questType = AddTracktype()
    }

    private fun applicable(vararg pairs: Pair<String, String>, ageDays: Int = 0) =
        questType.isApplicableTo(
            way(nodes = listOf(1, 2, 3), tags = mapOf(*pairs), timestamp = now - ageDays.days.inWholeMilliseconds)
        )

    @Test fun `applicable to untagged tracktype`() {
        assertTrue(applicable("highway" to "track"))
    }

    @Test fun `not applicable to recently tagged tracktype`() {
        assertFalse(applicable("highway" to "track", "tracktype" to "grade3", "surface" to "gravel"))
    }

    /** O3: sealed surface + grade2–5, tracktype not re-surveyed within a year */
    @Test fun `filter asphalt and grade2 after one year`() {
        assertTrue(applicable("highway" to "track", "tracktype" to "grade2", "surface" to "asphalt", ageDays = 400))
        assertFalse(applicable("highway" to "track", "tracktype" to "grade2", "surface" to "asphalt", ageDays = 30))
    }

    @Test fun `filter concrete and grade3 after one year`() {
        assertTrue(applicable("highway" to "track", "tracktype" to "grade3", "surface" to "concrete", ageDays = 400))
    }

    @Test fun `filter gravel and grade2 not re-asked early via sealed-surface rule`() {
        assertFalse(applicable("highway" to "track", "tracktype" to "grade2", "surface" to "gravel", ageDays = 400))
    }

    @Test fun `filter asphalt and grade1 not re-asked early via sealed-surface rule`() {
        assertFalse(applicable("highway" to "track", "tracktype" to "grade1", "surface" to "asphalt", ageDays = 400))
    }

    @Test fun `sealed surface with grade2 to grade5 is asked again after a year`() {
        for (grade in listOf("grade2", "grade3", "grade4", "grade5")) {
            for (surface in listOf("asphalt", "concrete", "paved", "chipseal", "concrete:plates", "concrete:lanes")) {
                assertTrue(
                    applicable("highway" to "track", "tracktype" to grade, "surface" to surface, ageDays = 400),
                    "$grade/$surface"
                )
            }
        }
    }

    @Test fun `sealed surface with grade2 is not asked again when recently surveyed`() {
        assertFalse(applicable("highway" to "track", "tracktype" to "grade2", "surface" to "asphalt", ageDays = 30))
        assertFalse(applicable("highway" to "track", "tracktype" to "grade2", "surface" to "asphalt", ageDays = 0))
    }

    @Test fun `protection conditions still apply`() {
        val base = arrayOf("highway" to "track", "tracktype" to "grade2", "surface" to "asphalt")
        assertFalse(applicable(*base, "access" to "private", ageDays = 400))
        assertFalse(applicable(*base, "bridge" to "yes", ageDays = 400))
        assertFalse(applicable(*base, "ice_road" to "yes", ageDays = 400))
        assertTrue(applicable(*base, "access" to "private", "foot" to "yes", ageDays = 400))
    }
}
