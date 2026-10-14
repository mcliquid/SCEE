package de.westnordost.streetcomplete.data.osm.osmquests

import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.quest.OsmQuestKey
import de.westnordost.streetcomplete.data.quest.QuestType

interface OsmQuestSource {

    interface Listener {
        fun onUpdated(added: Collection<OsmQuest>, deleted: Collection<OsmQuestKey>)
        fun onInvalidated()
    }

    /** get single quest by id */
    fun get(key: OsmQuestKey): OsmQuest?

    /** Revalidate a cached quest before opening, including quests created dynamically. */
    fun getValidated(quest: OsmQuest): OsmQuest? = get(quest.key)

    /** Get all quests of optionally the given types in given bounding box.
     *
     * @param refreshCrossingQuests When true, recompute [AddWaterwayCrossing] quests for this bbox
     *   (download/map-update path). When false, read persisted quests only (map pins, lookups). */
    fun getAllInBBox(
        bbox: BoundingBox,
        questTypes: Collection<QuestType>? = null,
        refreshCrossingQuests: Boolean = false,
    ): Collection<OsmQuest>

    fun addListener(listener: Listener)
    fun removeListener(listener: Listener)
}
