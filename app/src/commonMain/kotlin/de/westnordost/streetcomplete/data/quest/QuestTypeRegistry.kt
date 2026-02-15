package de.westnordost.streetcomplete.data.quest

import de.westnordost.streetcomplete.data.ObjectTypeRegistry

/** Every osm quest needs to be registered here.
 *
 * Could theoretically be done with Reflection, but that doesn't really work on Android.
 *
 * It is also used to define a (display) order of the quest types and to assign an ordinal to each
 * quest type for serialization.
 */
class QuestTypeRegistry(
    private val load: () -> List<Pair<Int, QuestType>>,
    private val ordinalsAndEntries: MutableList<Pair<Int, QuestType>> = load().toMutableList()
) : ObjectTypeRegistry<QuestType>(ordinalsAndEntries) {
    fun reload() {
        ordinalsAndEntries.clear()
        ordinalsAndEntries.addAll(load())
        byName.clear()
        byOrdinal.clear()
        ordinalByObject.clear()
        objects.clear()
        reloadInit()
    }
}
