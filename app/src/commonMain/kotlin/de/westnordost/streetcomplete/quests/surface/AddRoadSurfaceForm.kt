package de.westnordost.streetcomplete.quests.surface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.osmquests.Action
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.osm.surface.Surface
import de.westnordost.streetcomplete.osm.surface.SurfaceTracktypeResolution
import de.westnordost.streetcomplete.osm.surface.icon
import de.westnordost.streetcomplete.osm.surface.surfaceAnswerNeedsConflictDialog
import de.westnordost.streetcomplete.osm.surface.title
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import de.westnordost.streetcomplete.ui.common.quest.ItemSelectQuestForm
import de.westnordost.streetcomplete.ui.util.rememberSerializable
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AddRoadSurfaceForm(
    on: (QuestAction<SurfaceTracktypeResolution>) -> Unit,
    element: Element,
    geometry: ElementGeometry,
    countryInfo: CountryInfo,
) {
    var conflictSurface by rememberSerializable { mutableStateOf<Surface?>(null) }
    val pendingSurface = conflictSurface
    if (pendingSurface != null) {
        SurfaceTracktypeConflictForm(
            tags = element.tags,
            countryInfo = countryInfo,
            on = on,
            onBack = { conflictSurface = null },
            initialSurface = pendingSurface,
        )
        return
    }

    ItemSelectQuestForm(
        on = { action ->
            when (action) {
                is Answer<Surface> -> {
                    val surface = action.value
                    if (surfaceAnswerNeedsConflictDialog(element.tags, surface)) {
                        conflictSurface = surface
                    } else {
                        on(Answer(SurfaceTracktypeResolution.MaterialOnly(surface)))
                    }
                }
                is Action -> on(action)
            }
        },
        items = Surface.getSelectableValuesForWays(countryInfo.countryCode),
        itemContent = { item ->
            ImageWithLabel(item.icon?.let { painterResource(it) }, stringResource(item.title))
        },
        favoriteKey = "AddRoadSurfaceForm",
        submitOnSelection = true,
        title = stringResource(
            if (element.tags["area"] == "yes") Res.string.quest_streetSurface_square_title
            else Res.string.quest_streetSurface_title
        ),
    )
}