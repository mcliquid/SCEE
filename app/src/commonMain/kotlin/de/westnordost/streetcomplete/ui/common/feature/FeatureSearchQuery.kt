package de.westnordost.streetcomplete.ui.common.feature

import de.westnordost.osmfeatures.Feature
import de.westnordost.osmfeatures.FeatureDictionary
import de.westnordost.osmfeatures.GeometryType

const val FEATURE_SEARCH_RESULT_LIMIT = 50

/**
 * Search osmfeatures/presets by term.
 *
 * When [searchMoreLanguages] is false, this is exactly the current normal-language search.
 * When true, distinct additional languages that are not already represented by [languages] are
 * queried as well; results are merged with normal-language hits first, then additional-language
 * hits, deduplicated by [Feature.id], and limited to [limit].
 */
fun searchFeaturesByTerm(
    query: String,
    languages: List<String?>,
    additionalLanguages: Collection<String>,
    searchMoreLanguages: Boolean,
    countryCode: String?,
    geometryType: GeometryType?,
    filterFn: (Feature) -> Boolean = { true },
    limit: Int = FEATURE_SEARCH_RESULT_LIMIT,
    getByTerm: (
        query: String,
        languages: List<String?>?,
        country: String?,
        geometry: GeometryType?,
    ) -> Sequence<Feature>,
): List<Feature> {
    fun query(langs: List<String?>) =
        getByTerm(query, langs, countryCode, geometryType).filter(filterFn)

    val normalResults = query(languages).toList()
    if (!searchMoreLanguages) return normalResults.take(limit)

    val extraLanguages = additionalLanguagesForFeatureSearch(languages, additionalLanguages)
    if (extraLanguages.isEmpty()) return normalResults.take(limit)

    val additionalResults = extraLanguages.asSequence()
        .flatMap { query(listOf(it)) }
        .toList()

    return (normalResults + additionalResults).distinctBy { it.id }.take(limit)
}

fun FeatureDictionary.searchFeaturesByTerm(
    query: String,
    languages: List<String?>,
    additionalLanguages: Collection<String>,
    searchMoreLanguages: Boolean,
    countryCode: String?,
    geometryType: GeometryType?,
    filterFn: (Feature) -> Boolean = { true },
    limit: Int = FEATURE_SEARCH_RESULT_LIMIT,
): List<Feature> = searchFeaturesByTerm(
    query = query,
    languages = languages,
    additionalLanguages = additionalLanguages,
    searchMoreLanguages = searchMoreLanguages,
    countryCode = countryCode,
    geometryType = geometryType,
    filterFn = filterFn,
    limit = limit,
    getByTerm = { search, langs, country, geometry ->
        this.getByTerm(
            search = search,
            languages = langs,
            country = country,
            geometry = geometry,
        )
    },
)

/**
 * Extra language tags to query when searching in more languages.
 *
 * Secondary configured/system locales (everything after the primary locale) plus official/local
 * country languages, excluding blanks, duplicates, and languages already represented by the
 * normal search — including region variants of the same language (`de` vs `de-DE`).
 */
fun additionalLanguagesForFeatureSearch(
    normalLanguages: List<String?>,
    extraLanguages: Collection<String>,
): List<String> {
    val seen = normalLanguages.mapNotNull { it?.let(::featureSearchLanguageKey) }.toMutableSet()
    val result = ArrayList<String>()
    for (language in extraLanguages) {
        if (language.isBlank()) continue
        val key = featureSearchLanguageKey(language)
        if (!seen.add(key)) continue
        result += language
    }
    return result
}

private fun featureSearchLanguageKey(tag: String): String =
    tag.substringBefore('-').lowercase()
