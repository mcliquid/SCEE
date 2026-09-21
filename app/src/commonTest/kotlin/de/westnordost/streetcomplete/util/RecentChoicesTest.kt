package de.westnordost.streetcomplete.util

import kotlin.test.Test
import kotlin.test.assertEquals

class RecentChoicesTest {

    private val defaults = listOf("asphalt", "concrete", "paving_stones", "gravel", "wood")

    @Test fun `recent valid answer appears before ordinary items`() {
        assertEquals(
            listOf("gravel", "asphalt", "concrete", "paving_stones", "wood"),
            withRecentFirst(defaults, recent = listOf("gravel")),
        )
    }

    @Test fun `duplicate is removed from its original position`() {
        val ordered = withRecentFirst(defaults, recent = listOf("concrete", "concrete"))
        assertEquals(listOf("concrete", "asphalt", "paving_stones", "gravel", "wood"), ordered)
        assertEquals(1, ordered.count { it == "concrete" })
    }

    @Test fun `multiple recent values keep newest-first order`() {
        assertEquals(
            listOf("wood", "gravel", "asphalt", "concrete", "paving_stones"),
            withRecentFirst(defaults, recent = listOf("wood", "gravel", "wood")),
        )
    }

    @Test fun `available recent value missing from the defaults is prepended`() {
        val available = defaults + "sett"
        assertEquals(
            listOf("sett", "asphalt", "concrete", "paving_stones", "gravel", "wood"),
            withRecentFirst(defaults, recent = listOf("sett")) { it in available },
        )
    }

    @Test fun `unavailable recent value is ignored`() {
        val available = setOf("asphalt", "concrete", "paving_stones", "gravel")
        assertEquals(
            listOf("gravel", "asphalt", "concrete", "paving_stones", "wood"),
            withRecentFirst(defaults, recent = listOf("removed", "gravel")) { it in available },
        )
    }

    @Test fun `empty recent history keeps the original order`() {
        assertEquals(defaults, withRecentFirst(defaults, recent = emptyList()))
    }

    @Test fun `search results keep relevance order`() {
        val searchResults = listOf("paving_stones", "wood", "gravel")
        assertEquals(
            searchResults,
            choicesForQuery(
                query = "paving",
                searchResults = searchResults,
                defaultChoices = withRecentFirst(defaults, recent = listOf("wood")),
            ),
        )
    }
}