package de.westnordost.streetcomplete.data.osm.edits

import de.westnordost.streetcomplete.data.osm.mapdata.ElementKey

/** User-visible attention: server review, dependency block, or unresolvable temp references — not routine queue waiting. */
internal fun elementEditNeedsUserAttention(
    edit: ElementEdit,
    unsyncedById: Map<Long, ElementEdit>,
    findOwningEditId: (ElementKey) -> Long?,
): Boolean {
    if (edit.isSynced) return false
    if (edit.review != null) return true
    val negativeKeys = edit.action.elementKeys.filter { it.id < 0 }
    if (negativeKeys.isEmpty()) return false
    return !tempReferencesResolvable(edit, unsyncedById, findOwningEditId, mutableSetOf())
}

private fun tempReferencesResolvable(
    edit: ElementEdit,
    unsyncedById: Map<Long, ElementEdit>,
    findOwningEditId: (ElementKey) -> Long?,
    visiting: MutableSet<Long>,
): Boolean {
    if (edit.id in visiting) return false
    visiting.add(edit.id)
    for (key in edit.action.elementKeys) {
        if (key.id >= 0) continue
        val ownerId = findOwningEditId(key) ?: return false
        val owner = unsyncedById[ownerId] ?: return false
        if (owner.isSynced) return false
        if (owner.review != null) return false
        if (!tempReferencesResolvable(owner, unsyncedById, findOwningEditId, visiting)) return false
    }
    return true
}