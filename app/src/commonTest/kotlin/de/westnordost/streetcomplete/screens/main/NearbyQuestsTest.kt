package de.westnordost.streetcomplete.screens.main

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.osmquests.ExternalAction
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.edits.ElementEdit
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.quest_address_title
import de.westnordost.streetcomplete.resources.quest_bridge
import de.westnordost.streetcomplete.data.externalsource.ExternalSourceQuest
import de.westnordost.streetcomplete.data.externalsource.ExternalSourceQuestType
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.quest.Quest
import de.westnordost.streetcomplete.data.quest.TestQuestTypeA
import de.westnordost.streetcomplete.data.quest.TestQuestTypeB
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.osmQuest
import de.westnordost.streetcomplete.testutils.pGeom
import de.westnordost.streetcomplete.ui.common.quest.Marker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NearbyQuestsTest {
    private val typeA = TestQuestTypeA()
    private val typeB = TestQuestTypeB()
    private val dotType = NearbyDotQuestType()

    private val currentGeom = pGeom(52.0, 13.0)
    private val current = osmQuest(typeA, ElementType.NODE, 1, currentGeom)

    private fun at(id: Long, type: TestQuestTypeA = typeB, lat: Double = 52.0) =
        osmQuest(type, ElementType.NODE, id, pGeom(lat, 13.0))

    private val white = -1
    private val green = 0xFF00FF00.toInt()
    private val yellow = 0xFFFFFF00.toInt()

    @Test fun `empty input gives empty result`() {
        assertEquals(NearbyQuests.EMPTY, buildNearbyQuests(current, emptyList()))
    }

    @Test fun `current quest and poi dot quests are ignored`() {
        val result = buildNearbyQuests(current, listOf(current, at(2, dotType)))
        assertEquals(NearbyQuests.EMPTY, result)
    }

    @Test fun `quests of the same element are listed white without marker`() {
        val sameElement = at(1, typeB)
        val result = buildNearbyQuests(current, listOf(sameElement))
        assertEquals(listOf(white to listOf<Quest>(sameElement)), result.groups)
        assertTrue(result.markers.isEmpty())
    }

    @Test fun `same element comes first, other elements get a marker each`() {
        val other = at(2, lat = 52.1)
        val same = at(1, typeB)
        val result = buildNearbyQuests(current, listOf(other, same))
        assertEquals(listOf(white, green), result.groups.map { it.first })
        assertEquals(listOf(Marker(other.geometry, color = green)), result.markers)
    }

    @Test fun `quests of one element share a color and a single marker`() {
        val a = osmQuest(typeA, ElementType.NODE, 2, pGeom(52.1, 13.0))
        val b = osmQuest(typeB, ElementType.NODE, 2, pGeom(52.1, 13.0))
        val c = at(3, lat = 52.2)
        val result = buildNearbyQuests(current, listOf(a, b, c))
        assertEquals(listOf(green to listOf<Quest>(a, b), yellow to listOf<Quest>(c)), result.groups)
        assertEquals(2, result.markers.size)
    }

    @Test fun `colors cycle after six elements`() {
        val quests = (2L..8L).map { at(it, lat = 52.0 + it / 100.0) }
        val colors = buildNearbyQuests(current, quests).groups.map { it.first }
        assertEquals(7, colors.size)
        assertEquals(colors[0], colors[6])
        assertEquals(6, colors.take(6).toSet().size)
    }

    @Test fun `external source quests are their own groups`() {
        val type = FakeExternalSourceQuestType()
        val ext = ExternalSourceQuest("x", pGeom(52.2, 13.0), type)
        val result = buildNearbyQuests(current, listOf(ext))
        assertEquals(listOf(green to listOf<Quest>(ext)), result.groups)
        assertEquals(listOf(Marker(ext.geometry, color = green)), result.markers)
    }

    @Test fun `external source quest can be the opened quest`() {
        val type = FakeExternalSourceQuestType()
        val ext = ExternalSourceQuest("x", pGeom(52.2, 13.0), type)
        val other = at(5, lat = 52.3)
        val result = buildNearbyQuests(ext, listOf(ext, other))
        assertEquals(listOf(green to listOf<Quest>(other)), result.groups)
    }

    @Test fun `merge keeps highlight marker and takes quest color at same position`() {
        val highlight = Marker(pGeom(52.1, 13.0), title = "h")
        val elsewhere = Marker(pGeom(52.3, 13.0), title = "e")
        val quest = Marker(pGeom(52.1, 13.0), color = green)
        val questOnly = Marker(pGeom(52.5, 13.0), color = yellow)
        val merged = mergeMarkersAtSamePosition(listOf(highlight, elsewhere), listOf(quest, questOnly))
        assertEquals(listOf(highlight.copy(color = green), elsewhere, questOnly), merged)
    }

    @Test fun `merge without quest markers returns highlights unchanged`() {
        val highlights = listOf(Marker(pGeom(52.1, 13.0)))
        assertEquals(highlights, mergeMarkersAtSamePosition(highlights, emptyList()))
    }

    @Test fun `mode 0 does not query anything`() {
        val prefs = inMemoryPrefs()
        var queried = false
        val result = findNearbyQuests(current, prefs) { _, _ -> queried = true; emptyList() }
        assertEquals(NearbyQuests.EMPTY, result)
        assertEquals(false, queried)
    }

    @Test fun `enabled mode queries with distance plus epsilon`() {
        val prefs = inMemoryPrefs()
        prefs.putInt(Prefs.SHOW_NEARBY_QUESTS, 2)
        prefs.putFloat(Prefs.SHOW_NEARBY_QUESTS_DISTANCE, 5f)
        var asked: Double? = null
        val other = at(2, lat = 52.1)
        val result = findNearbyQuests(current, prefs) { q, d -> assertEquals(current, q); asked = d; listOf(other) }
        assertEquals(5.01, asked!!, 1e-6)
        assertNotEquals(NearbyQuests.EMPTY, result)
        assertEquals(1, result.markers.size)
    }

    @Test fun `default distance is practically zero`() {
        val prefs = inMemoryPrefs()
        prefs.putInt(Prefs.SHOW_NEARBY_QUESTS, 1)
        var asked = -1.0
        findNearbyQuests(current, prefs) { _, d -> asked = d; emptyList() }
        assertEquals(0.01, asked, 1e-6)
    }
}

internal class FakeExternalSourceQuestType : ExternalSourceQuestType {
    override val source = "test"
    override val icon = Res.drawable.quest_bridge
    override val title = Res.string.quest_address_title
    override val wikiLink: String? = null
    override val changesetComment = "test"
    override val achievements = emptyList<EditTypeAchievement>()

    @Composable
    override fun Form(on: (ExternalAction) -> Unit, quest: ExternalSourceQuest, countryInfo: CountryInfo) {}
    override suspend fun download(bbox: BoundingBox) = emptyList<ExternalSourceQuest>()
    override suspend fun upload() {}
    override fun getQuests(bbox: BoundingBox) = emptyList<ExternalSourceQuest>()
    override fun get(id: String): ExternalSourceQuest? = null
    override fun onAddedEdit(edit: ElementEdit, id: String) {}
    override fun onDeletedEdit(edit: ElementEdit, id: String?) {}
    override fun onSyncedEdit(edit: ElementEdit, id: String?) {}
    override fun onSyncEditFailed(edit: ElementEdit, id: String?) {}
    override suspend fun onUpload(edit: ElementEdit, id: String?) = true
    override fun deleteQuest(id: String) = false
    override fun deleteMetadataOlderThan(timestamp: Long) {}
}

private class NearbyDotQuestType : TestQuestTypeA() { override val dotColor = "#ff0000" }
