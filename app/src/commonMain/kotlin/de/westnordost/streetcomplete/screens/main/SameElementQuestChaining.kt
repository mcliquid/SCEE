package de.westnordost.streetcomplete.screens.main

import de.westnordost.streetcomplete.data.osm.edits.ElementEditAction
import de.westnordost.streetcomplete.data.osm.edits.ElementEditType
import de.westnordost.streetcomplete.data.osm.edits.update_tags.UpdateElementTagsAction
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.ElementKey
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuest
import de.westnordost.streetcomplete.data.quest.Quest
import de.westnordost.streetcomplete.data.quest.QuestType
import de.westnordost.streetcomplete.data.visiblequests.QuestTypeOrderSource

/**
 * Element edited by an ordinary tag update, when automatic same-element chaining is enabled.
 *
 * Structural actions (split, move, delete, create) and non-quest edits (tag editor, overlays,
 * external-source quests, notes) return null. Callers then keep the normal post-answer close.
 */
internal fun elementKeyForImmediateSameElementQuest(
    elementEditType: ElementEditType,
    elementEditAction: ElementEditAction,
    showNextImmediately: Boolean,
): ElementKey? {
    if (!showNextImmediately) return null
    if (elementEditType !is OsmElementQuestType<*>) return null
    val tagUpdate = elementEditAction as? UpdateElementTagsAction ?: return null
    return tagUpdate.originalElement.key
}

/**
 * Configured quest-type order for choosing the next quest on the same element.
 *
 * This is the registry order after [QuestTypeOrderSource.sort]: the same base order
 * [de.westnordost.streetcomplete.screens.main.map.QuestPinsManager] uses before map-only
 * display modifiers.
 *
 * Those modifiers are not applied here:
 * - `reverseQuestOrder` is a session-only map toggle (`MainViewModel.reverseQuestOrder`),
 *   not a stored quest-order preference. It only inverts pin stacking.
 * - Day/night priority (`Prefs.DAY_NIGHT_BEHAVIOR`) moves quests in front of pins based on
 *   the camera location inside QuestPinsManager. Copying that here would duplicate map code
 *   and would not change the configured quest order.
 */
internal fun questTypesInChainingOrder(
    questTypes: List<QuestType>,
    questTypeOrderSource: QuestTypeOrderSource,
): List<QuestType> {
    val sorted = questTypes.toMutableList()
    questTypeOrderSource.sort(sorted)
    return sorted
}

/**
 * Next visible OSM quest on [elementKey].
 *
 * Identity is [ElementKey]. Equal geometry on another element does not match.
 * Among matches, the earliest type in [orderedQuestTypes] wins (index 0 is highest priority,
 * same as pin order before the display modifiers documented on [questTypesInChainingOrder]).
 * Types missing from that list sort after every listed type, then by quest-type name.
 *
 * Quests with a non-null [QuestType.dotColor] stay excluded. The previous chaining code
 * skipped them because they are drawn as colored dots rather than normal quest pins.
 * Only quests present in [candidates] can be chosen, so a disabled or hidden quest that
 * [de.westnordost.streetcomplete.data.quest.VisibleQuestsSource] did not return cannot win.
 */
internal fun selectSameElementQuestSuccessor(
    candidates: Iterable<OsmQuest>,
    elementKey: ElementKey,
    orderedQuestTypes: List<QuestType>,
): OsmQuest? {
    val rank = HashMap<QuestType, Int>(orderedQuestTypes.size)
    orderedQuestTypes.forEachIndexed { index, type -> rank[type] = index }
    return candidates
        .filter { ElementKey(it.elementType, it.elementId) == elementKey && it.type.dotColor == null }
        .minWithOrNull(compareBy<OsmQuest> { rank[it.type] ?: Int.MAX_VALUE }.thenBy { it.type.name })
}

/**
 * Bottom sheet for the successor, or null when there is no successor or its element cannot be loaded.
 * A missing element must not throw; the caller then leaves the sheet closed.
 */
internal fun immediateSameElementQuestSheet(
    candidates: Iterable<Quest>,
    elementKey: ElementKey,
    orderedQuestTypes: List<QuestType>,
    loadElement: (ElementType, Long) -> Element?,
): ShownBottomSheet.OsmQuest? {
    val quest = selectSameElementQuestSuccessor(
        candidates.filterIsInstance<OsmQuest>(),
        elementKey,
        orderedQuestTypes,
    ) ?: return null
    val element = loadElement(quest.elementType, quest.elementId) ?: return null
    return ShownBottomSheet.OsmQuest(quest, element)
}
