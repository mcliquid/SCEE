package de.westnordost.streetcomplete.osm.things

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.node
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ThingsKtTest {

    private var previousPreferences: Preferences? = null

    @BeforeTest fun setUp() {
        // isThing() reads the global Prefs.preferences.expertMode, so it must be initialized
        previousPreferences = runCatching { Prefs.preferences }.getOrNull()
        Prefs.preferences = inMemoryPrefs()
    }

    @AfterTest fun tearDown() {
        // restore whatever (if anything) was set before, so no global state leaks between tests
        previousPreferences?.let { Prefs.preferences = it }
    }

    @Test fun `disused bench matches`() {
        val node = node(tags = mapOf("disused:amenity" to "bench"))
        assertEquals(true, node.isDisusedThing())
        assertEquals(false, node.isThing())
    }

    @Test fun `flagpole matches`() {
        val node = node(tags = mapOf("man_made" to "flagpole"))
        assertEquals(true, node.isThing())
    }

    @Test fun `specific flagpole matches`() {
        // note that in search currently you may need to type "PL - Poland"
        val node = node(tags = mapOf(
            "country" to "PL",
            "flag:name" to "Poland",
            "flag:type" to "national",
            "flag:wikidata" to "Q42436",
            "man_made" to "flagpole",
            "subject" to "Poland",
            "subject:wikidata" to "Q36",
        ))
        assertEquals(true, node.isThing())
    }
}
