package de.westnordost.streetcomplete.quests.shoulder

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.osmquests.OsmFilterQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.CAR
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.osm.maxspeed.MAX_SPEED_TYPE_KEYS
import de.westnordost.streetcomplete.osm.surface.UNPAVED_SURFACES
import de.westnordost.streetcomplete.resources.*

class AddShoulder : OsmFilterQuestType<ShoulderSides>() {

    /* Trunks always, smaller roads only if they are either motorroads, bridges or tunnels or if
     * they are likely rural roads (high max speeds - implicit or explicit - or no sidewalk).
     *
     * The existence of a parking lane, cycle lane excludes the existence of a shoulder because they
     * are in themselves kind of a shoulder (with a special purpose)
     *
     */
    override val elementFilter = """
        ways with
          (
            (
              highway ~ trunk|primary|secondary|tertiary|unclassified
              and (
                motorroad = yes
                or expressway = yes
                or tunnel ~ yes|building_passage|avalanche_protector
                or bridge = yes
                or sidewalk ~ no|none
                or !maxspeed and highway = trunk
                or maxspeed > 50
                or ~"${(MAX_SPEED_TYPE_KEYS + "maxspeed").joinToString("|")}" ~ ".*:(rural|trunk|motorway|nsl_single|nsl_dual)"
              )
            ) or (
              highway ~ motorway|motorway_link|trunk|trunk_link|primary|primary_link|secondary|secondary_link|tertiary|tertiary_link|unclassified
              and (foot ~ yes|designated or bicycle ~ yes|designated)
            )
          )
          and lane_markings != no
          and surface !~ ${UNPAVED_SURFACES.joinToString("|")}
          and (!parking:lane or parking:lane ~ no|none|no_stopping|no_parking|separate)
          and (!parking:lane:left or parking:lane:left ~ no|none|no_stopping|no_parking|separate)
          and (!parking:lane:right or parking:lane:right ~ no|none|no_stopping|no_parking|separate)
          and (!parking:lane:both or parking:lane:both ~ no|none|no_stopping|no_parking|separate)
          and cycleway != lane
          and cycleway:left != lane
          and cycleway:right != lane
          and cycleway:both != lane
          and !verge
          and !shoulder
          and !shoulder:left and !shoulder:right and !shoulder:both
          and (access !~ private|no or (foot and foot !~ private|no))
    """.trimIndent()

    override val changesetComment = "Specify whether roads have shoulders"
    override val wikiLink = "Key:shoulder"
    override val icon = Res.drawable.quest_street_shoulder
    override val title = Res.string.quest_shoulder_title
    override val achievements = listOf(CAR)
    override val hint = Res.string.quest_shoulder_explanation2
    override val defaultDisabledMessage = Res.string.default_disabled_msg_difficult_and_time_consuming

    @Composable
    override fun Form(
        on: (QuestAction<ShoulderSides>) -> Unit,
        element: Element,
        geometry: ElementGeometry,
        countryInfo: CountryInfo,
    ) {
        AddShoulderForm(on, element, geometry, countryInfo)
    }

    override fun applyAnswerTo(
        answer: ShoulderSides,
        tags: Tags,
        geometry: ElementGeometry,
        timestampEdited: Long,
    ) {
        if (tags.containsKey("shoulder")
            || tags.containsKey("shoulder:left")
            || tags.containsKey("shoulder:right")
            || tags.containsKey("shoulder:both")
        ) {
            return
        }
        tags["shoulder"] = answer.osmValue
    }
}
