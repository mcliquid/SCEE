package de.westnordost.streetcomplete.ui.common.quest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PreviousAnswerTest {

    @Test fun `wiki Key link becomes a single previous-answer key`() {
        assertEquals(listOf("tracktype"), previousAnswerKeysFromWikiLink("Key:tracktype"))
        assertEquals(listOf("surface"), previousAnswerKeysFromWikiLink("Key:surface"))
        assertEquals(listOf("smoothness"), previousAnswerKeysFromWikiLink("Key:smoothness"))
        assertEquals(listOf("roof:colour"), previousAnswerKeysFromWikiLink("Key:roof:colour"))
    }

    @Test fun `ambiguous wiki links yield no keys`() {
        assertEquals(emptyList(), previousAnswerKeysFromWikiLink(null))
        assertEquals(emptyList(), previousAnswerKeysFromWikiLink("Tag:highway=track"))
        assertEquals(emptyList(), previousAnswerKeysFromWikiLink("Key:"))
        assertEquals(emptyList(), previousAnswerKeysFromWikiLink("Key:disused:"))
        assertEquals(emptyList(), previousAnswerKeysFromWikiLink("Key:amenity=bbq"))
    }

    @Test fun `single key formats as bare value`() {
        assertEquals(
            "grade2",
            formatPreviousAnswerValues(mapOf("tracktype" to "grade2", "highway" to "track"), listOf("tracktype"))
        )
    }

    @Test fun `missing keys yield null`() {
        assertNull(formatPreviousAnswerValues(mapOf("highway" to "track"), listOf("tracktype")))
        assertNull(formatPreviousAnswerValues(mapOf("tracktype" to "grade2"), emptyList()))
    }

    @Test fun `multiple keys include key names`() {
        assertEquals(
            "sidewalk:left:surface=asphalt, sidewalk:right:surface=concrete",
            formatPreviousAnswerValues(
                mapOf(
                    "sidewalk:left:surface" to "asphalt",
                    "sidewalk:right:surface" to "concrete",
                ),
                listOf("sidewalk:left:surface", "sidewalk:right:surface"),
            )
        )
    }
}
