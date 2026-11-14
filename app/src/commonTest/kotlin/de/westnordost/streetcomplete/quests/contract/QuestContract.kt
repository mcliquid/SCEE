package de.westnordost.streetcomplete.quests.contract

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.osm.LAST_CHECK_DATE_KEYS
import de.westnordost.streetcomplete.osm.getLastCheckDateKeys
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

fun <T> OsmElementQuestType<T>.answeredTags(
    answer: T,
    tags: Map<String, String>,
    geometry: ElementGeometry = ElementPointGeometry(LatLon(0.0, 0.0)),
): Map<String, String> {
    val builder = StringMapChangesBuilder(tags)
    applyAnswerTo(answer, builder, geometry, 0)
    return tags.toMutableMap().also { builder.create().applyTo(it) }
}

/** Geometry-dependent quests supply a map-based eligibility check, never treat null as closed. */
fun <T> assertQuestContract(
    quest: OsmElementQuestType<T>,
    answer: T,
    tags: Map<String, String>,
    isApplicable: (Map<String, String>) -> Boolean,
    preservedKeys: Set<String> = emptySet(),
    geometry: ElementGeometry = ElementPointGeometry(LatLon(0.0, 0.0)),
): Map<String, String> {
    val before = mapOf("note" to "keep", "source:ref" to "survey reference") + tags
    assertTrue(isApplicable(before), "Contract precondition: quest applies")
    val builder = StringMapChangesBuilder(before)
    quest.applyAnswerTo(answer, builder, geometry, 0)
    assertFalse(builder.create().isEmpty(), "Q2: nonempty edit")
    val after = before.toMutableMap().also { builder.create().applyTo(it) }
    assertNotEquals(before, after, "Q2: no silent no-op")
    for (key in preservedKeys + setOf("note", "source:ref")) {
        assertEquals(before[key], after[key], "Q3: preserve $key")
    }
    assertFalse(isApplicable(after), "Q1/Q6: answer closes quest")
    val twice = quest.answeredTags(answer, after, geometry)
    val dateKeys = LAST_CHECK_DATE_KEYS + (after.keys + twice.keys).flatMap { getLastCheckDateKeys(it).toList() }
    assertEquals(after - dateKeys.toSet(), twice - dateKeys.toSet(), "Q8: idempotent answer apart from survey dates")
    return after
}

internal class QuestContract<T>(
    val quest: OsmElementQuestType<T>,
    val answer: T,
    private val tags: Map<String, String>,
    private val isApplicable: (Map<String, String>) -> Boolean,
    private val preservedKeys: Set<String> = emptySet(),
) {
    fun verify(): Map<String, String> = assertQuestContract(quest, answer, tags, isApplicable, preservedKeys)
}
