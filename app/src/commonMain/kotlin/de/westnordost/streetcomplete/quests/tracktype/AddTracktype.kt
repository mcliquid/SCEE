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
import de.westnordost.streetcomplete.osm.surface.SurfaceTracktypeResolution
import de.westnordost.streetcomplete.osm.surface.applyTo
import de.westnordost.streetcomplete.osm.surface.UNPAVED_SURFACES
import de.westnordost.streetcomplete.osm.surface.tracktypeAnswerNeedsConflictDialog
import de.westnordost.streetcomplete.quests.surface.SurfaceTracktypeConflictForm
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import de.westnordost.streetcomplete.ui.common.quest.ItemSelectQuestForm
import de.westnordost.streetcomplete.ui.util.rememberSerializable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.westnordost.streetcomplete.data.osm.osmquests.Action
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// sealed surfaces contradict a tracktype of grade2-5 only mildly (grade2) or clearly (grade3-5): ask again
// (without changing any tag automatically), but at most once a year, as the check date is updated on confirmation
private val SEALED_SURFACES = setOf(
    "asphalt", "concrete", "chipseal", "paved", "concrete:plates", "concrete:lanes",
)

class AddTracktype : OsmFilterQuestType<SurfaceTracktypeResolution>() {

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
    override fun Form(on: (QuestAction<SurfaceTracktypeResolution>) -> Unit, element: Element, geometry: ElementGeometry, countryInfo: CountryInfo) {
        var conflictTracktype by rememberSerializable { mutableStateOf<Tracktype?>(null) }
        val pendingTracktype = conflictTracktype
        if (pendingTracktype != null) {
            SurfaceTracktypeConflictForm(
                tags = element.tags,
                countryInfo = countryInfo,
                on = on,
                onBack = { conflictTracktype = null },
                initialTracktype = pendingTracktype,
            )
            return
        }

        ItemSelectQuestForm(
            on = { action ->
                when (action) {
                    is Answer<Tracktype> -> {
                        val tracktype = action.value
                        if (tracktypeAnswerNeedsConflictDialog(element.tags, tracktype.osmValue)) {
                            conflictTracktype = tracktype
                        } else {
                            on(Answer(SurfaceTracktypeResolution.TracktypeOnly(tracktype.osmValue)))
                        }
                    }
                    is Action -> on(action)
                }
            },
            items = Tracktype.entries,
            itemContent = { ImageWithLabel(painterResource(it.icon), stringResource(it.title)) },
            submitOnSelection = true,
        )
    }

    override fun applyAnswerTo(answer: SurfaceTracktypeResolution, tags: Tags, geometry: ElementGeometry, timestampEdited: Long) {
        answer.applyTo(tags)
    }
}
