package de.westnordost.streetcomplete.quests.tracktype

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.osmquests.OsmFilterQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.BICYCLIST
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.CAR
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.osm.surface.UNPAVED_SURFACES
import de.westnordost.streetcomplete.osm.updateWithCheckDate
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import de.westnordost.streetcomplete.ui.common.quest.ItemSelectQuestForm
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// sealed surfaces contradict a tracktype of grade2-5 only mildly (grade2) or clearly (grade3-5): ask again
// (without changing any tag automatically), but at most once a year, as the check date is updated on confirmation
private val SEALED_SURFACES = setOf(
    "asphalt", "concrete", "chipseal", "paved", "concrete:plates", "concrete:lanes",
)

class AddTracktype : OsmFilterQuestType<Tracktype>() {

    override val elementFilter = """
        ways with
          highway = track
          and (
            !tracktype
            or tracktype != grade1 and tracktype older today -6 years
            or surface ~ ${UNPAVED_SURFACES.joinToString("|")} and tracktype older today -6 years
            or tracktype older today -8 years
            or tracktype ~ grade2|grade3|grade4|grade5 and surface ~ ${SEALED_SURFACES.joinToString("|")} and tracktype older today -1 years
          )
          and (access !~ private|no or (foot and foot !~ private|no))
          and !bridge
          and ice_road != yes
    """
    // ~paved tracks are less likely to change the surface type
    override val changesetComment = "Specify tracktypes"
    override val wikiLink = "Key:tracktype"
    override val icon = Res.drawable.quest_tractor
    override val title = Res.string.quest_tracktype_title
    override val achievements = listOf(CAR, BICYCLIST)

    @Composable
    override fun Form(on: (QuestAction<Tracktype>) -> Unit, element: Element, geometry: ElementGeometry, countryInfo: CountryInfo) {
        ItemSelectQuestForm(
            on = on,
            items = Tracktype.entries,
            itemContent = { ImageWithLabel(painterResource(it.icon), stringResource(it.title)) },
            submitOnSelection = true,
        )
    }

    override fun applyAnswerTo(answer: Tracktype, tags: Tags, geometry: ElementGeometry, timestampEdited: Long) {
        tags.updateWithCheckDate("tracktype", answer.osmValue)
    }
}
