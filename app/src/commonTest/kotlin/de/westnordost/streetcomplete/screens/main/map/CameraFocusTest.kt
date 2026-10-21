package de.westnordost.streetcomplete.screens.main.map

import de.westnordost.streetcomplete.data.externalsource.ExternalSourceQuest
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.screens.main.FakeExternalSourceQuestType
import de.westnordost.streetcomplete.screens.main.ShownBottomSheet
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.osmQuest
import de.westnordost.streetcomplete.testutils.pGeom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CameraFocusTest {
    private val geom = pGeom(52.0, 13.0)

    @Test fun `osm quests are focused`() {
        val quest = osmQuest(geometry = geom)
        assertEquals(geom, questGeometryToFocus(ShownBottomSheet.OsmQuest(quest, node())))
    }

    @Test fun `external source quests (osmose, custom) are focused like osm quests`() {
        val quest = ExternalSourceQuest("x", geom, FakeExternalSourceQuestType())
        assertEquals(geom, questGeometryToFocus(ShownBottomSheet.ExternalSourceQuest(quest)))
    }

    @Test fun `forms that are not quests keep the map in place`() {
        assertNull(questGeometryToFocus(ShownBottomSheet.CreateOsmNote(null)))
        assertNull(questGeometryToFocus(ShownBottomSheet.InsertNode(pGeom().center)))
    }
}
