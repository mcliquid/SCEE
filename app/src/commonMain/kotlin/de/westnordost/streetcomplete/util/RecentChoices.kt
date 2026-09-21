package de.westnordost.streetcomplete.util

/**
 * Puts still-valid [recent] values ahead of [items].
 *
 * [recent] keeps its relative order (newest first, as stored by last-picked preferences).
 * Each value appears once. Remaining [items] keep their original order.
 * Recent values for which [isAvailable] is false are ignored and do not remove a default item.
 */
fun <T> withRecentFirst(
    items: List<T>,
    recent: List<T>,
    isAvailable: (T) -> Boolean = { it in items },
): List<T> {
    val recentValid = recent.filter(isAvailable).distinct()
    val recentIds = recentValid.toSet()
    return recentValid + items.filter { it !in recentIds }
}

/**
 * A non-empty search query keeps the relevance order of [searchResults].
 * Recency is applied only to the unfiltered default list, before this function is called.
 */
fun <T> choicesForQuery(
    query: String,
    searchResults: List<T>,
    defaultChoices: List<T>,
): List<T> = if (query.isNotEmpty()) searchResults else defaultChoices
