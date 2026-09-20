package de.westnordost.streetcomplete.ui.common.feature

import de.westnordost.osmfeatures.Feature
import de.westnordost.osmfeatures.GeometryType
import de.westnordost.streetcomplete.testutils.feature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeatureSearchQueryTest {

    private val bakery = feature(id = "shop/bakery", names = listOf("Bakery"))
    private val kiosk = feature(id = "shop/kiosk", names = listOf("Kiosk"))
    private val leipomo = feature(id = "amenity/leipomo", names = listOf("Leipomo"))
    private val pharmacy = feature(id = "amenity/pharmacy", names = listOf("Pharmacy"))

    private val normalLanguages = listOf("en", null)
    private val officialFinnish = listOf("fi")

    @Test fun `OFF does not return additional-language-only features`() {
        val results = search(
            searchMoreLanguages = false,
            additionalLanguages = officialFinnish,
        )
        assertEquals(listOf(bakery), results)
    }

    @Test fun `OFF still returns normal-language matches`() {
        val results = search(
            query = "Bakery",
            searchMoreLanguages = false,
        )
        assertEquals(listOf(bakery), results)
    }

    @Test fun `ON returns additional-language-only features`() {
        val results = search(
            searchMoreLanguages = true,
            additionalLanguages = officialFinnish,
        )
        assertEquals(listOf(bakery, leipomo), results)
    }

    @Test fun `features found in both normal and additional languages appear once`() {
        val results = search(
            searchMoreLanguages = true,
            additionalLanguages = listOf("de"),
            byLanguage = mapOf(
                "en" to listOf(bakery, kiosk),
                "de" to listOf(bakery, pharmacy),
            ),
        )
        assertEquals(listOf(bakery, kiosk, pharmacy), results)
    }

    @Test fun `normal-language matches precede additional-only matches`() {
        val results = search(
            searchMoreLanguages = true,
            additionalLanguages = officialFinnish,
            byLanguage = mapOf(
                "en" to listOf(kiosk),
                "fi" to listOf(leipomo, bakery),
            ),
        )
        assertEquals(listOf(kiosk, leipomo, bakery), results)
    }

    @Test fun `merged results never exceed 50`() {
        val normal = (1..40).map { feature(id = "normal/$it") }
        val extra = (1..20).map { feature(id = "extra/$it") }
        val results = search(
            searchMoreLanguages = true,
            additionalLanguages = officialFinnish,
            byLanguage = mapOf(
                "en" to normal,
                "fi" to extra,
            ),
            matchAll = true,
        )
        assertEquals(50, results.size)
        assertEquals(normal + extra.take(10), results)
    }

    @Test fun `additional language already in the normal search is not queried again`() {
        val calls = mutableListOf<SearchCall>()
        search(
            searchMoreLanguages = true,
            additionalLanguages = listOf("en", "en-GB"),
            calls = calls,
        )
        assertEquals(1, calls.size)
        assertEquals(normalLanguages, calls.single().languages)
    }

    @Test fun `duplicate extra language tags are queried once`() {
        val calls = mutableListOf<SearchCall>()
        search(
            searchMoreLanguages = true,
            additionalLanguages = listOf("fi", "fi-FI", "FI"),
            calls = calls,
        )
        assertEquals(
            listOf(normalLanguages, listOf("fi")),
            calls.map { it.languages },
        )
    }

    @Test fun `feature filter applies to additional-language queries`() {
        val results = search(
            searchMoreLanguages = true,
            additionalLanguages = officialFinnish,
            filterFn = { it.id.startsWith("shop/") },
        )
        assertEquals(listOf(bakery), results)
    }

    @Test fun `country and geometry are propagated to every query`() {
        val calls = mutableListOf<SearchCall>()
        search(
            searchMoreLanguages = true,
            additionalLanguages = officialFinnish,
            countryCode = "FI",
            geometryType = GeometryType.POINT,
            calls = calls,
        )
        assertTrue(calls.isNotEmpty())
        assertTrue(calls.all { it.country == "FI" && it.geometry == GeometryType.POINT })
        assertEquals(2, calls.size)
    }

    @Test fun `region variants of a normal language are treated as already covered`() {
        assertEquals(
            emptyList(),
            additionalLanguagesForFeatureSearch(
                normalLanguages = listOf("de-DE", "en", null),
                extraLanguages = listOf("de", "de-AT"),
            )
        )
    }

    private fun search(
        query: String = "x",
        searchMoreLanguages: Boolean,
        additionalLanguages: Collection<String> = emptyList(),
        countryCode: String? = "DE",
        geometryType: GeometryType? = GeometryType.VERTEX,
        filterFn: (Feature) -> Boolean = { true },
        byLanguage: Map<String?, List<Feature>> = mapOf(
            "en" to listOf(bakery),
            "fi" to listOf(leipomo),
            "de" to listOf(bakery, pharmacy),
        ),
        matchAll: Boolean = false,
        calls: MutableList<SearchCall>? = null,
    ): List<Feature> = searchFeaturesByTerm(
        query = query,
        languages = normalLanguages,
        additionalLanguages = additionalLanguages,
        searchMoreLanguages = searchMoreLanguages,
        countryCode = countryCode,
        geometryType = geometryType,
        filterFn = filterFn,
        getByTerm = { q, langs, country, geometry ->
            calls?.add(SearchCall(q, langs, country, geometry))
            langs.orEmpty().asSequence().flatMap { lang ->
                byLanguage[lang].orEmpty().asSequence().filter { feature ->
                    matchAll || feature.names.any { it.contains(q, ignoreCase = true) } || q == "x"
                }
            }
        },
    )

    private data class SearchCall(
        val query: String,
        val languages: List<String?>?,
        val country: String?,
        val geometry: GeometryType?,
    )
}
