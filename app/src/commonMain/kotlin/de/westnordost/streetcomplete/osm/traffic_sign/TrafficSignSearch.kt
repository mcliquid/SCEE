package de.westnordost.streetcomplete.osm.traffic_sign

/** Filter for the sign catalogue picker */
enum class TrafficSignPickerKind { MAIN, MODIFIER }

/** Whether the catalogue recommends tags for this sign on ways */
val TrafficSign.hasWayRecommendation: Boolean get() =
    tagRecommendations?.any { "way" in it.geometries } == true

/** Whether [this] additional sign is a sensible addition to [main]. False if the catalogue says that
 *  [main] can not receive additional signs or lists this sign as incompatible. This is only a hint:
 *  unusual combinations stay possible. */
fun TrafficSign.isCompatibleModifierOf(main: TrafficSign?): Boolean {
    if (main == null) return true
    if (main.canReceiveModifiers == false) return false
    return osmValuePart !in main.incompatibleModifiers && signId !in main.incompatibleModifiers
}

/** Search the signs of this catalogue.
 *
 *  @param query matches the number ("237", "274-30"), the official name ("Zeichen 237") and the
 *               description ("Radweg") case-insensitively. Blank: everything.
 *  @param kind main signs or additional signs
 *  @param category if set, only signs of this [TrafficSign.category]
 *  @param main the main sign that additional signs are to be added to. Unless [showAll], additional
 *              signs that are not compatible with it are left out
 *
 *  Best matches first (exact number, number prefix, name, description); at equal rank, signs with
 *  recommended tags for ways first, then in the order of the catalogue. */
fun TrafficSignCatalogue.search(
    query: String,
    kind: TrafficSignPickerKind,
    category: String? = null,
    main: TrafficSign? = null,
    showAll: Boolean = false,
): List<TrafficSign> {
    val q = query.trim().lowercase()
    val candidates = signs.withIndex().filter { (_, sign) ->
        (sign.isModifier == (kind == TrafficSignPickerKind.MODIFIER)) &&
            (category == null || sign.category == category) &&
            (kind == TrafficSignPickerKind.MAIN || showAll || sign.isCompatibleModifierOf(main))
    }
    data class Ranked(val sign: TrafficSign, val rank: Int, val index: Int)
    val ranked = candidates.mapNotNull { (index, sign) ->
        val rank = rank(sign, q) ?: return@mapNotNull null
        Ranked(sign, rank, index)
    }
    return ranked
        .sortedWith(compareBy<Ranked>({ it.rank }, { if (it.sign.hasWayRecommendation) 0 else 1 }, { it.index }))
        .map { it.sign }
}

/** categories that occur among the signs of the given kind, in catalogue order */
fun TrafficSignCatalogue.categories(kind: TrafficSignPickerKind): List<String> =
    signs.filter { it.isModifier == (kind == TrafficSignPickerKind.MODIFIER) }
        .map { it.category }.distinct()

private fun rank(sign: TrafficSign, q: String): Int? {
    if (q.isEmpty()) return 0
    val id = sign.osmValuePart.lowercase()
    val signId = sign.signId.lowercase()
    return when {
        id == q || signId == q -> 0
        id.startsWith(q) || signId.startsWith(q) -> 1
        sign.name.lowercase().contains(q) -> 2
        sign.descriptiveName.lowercase().contains(q) -> 3
        else -> null
    }
}
