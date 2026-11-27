package de.westnordost.streetcomplete.overlays.sidewalk

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.updateElementTagsActionOrNull
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.overlays.Edit
import de.westnordost.streetcomplete.data.overlays.OverlayAction
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.osm.footway.FootwayKind
import de.westnordost.streetcomplete.osm.footway.applyTo
import de.westnordost.streetcomplete.osm.footway.icon
import de.westnordost.streetcomplete.osm.footway.parseFootwayKind
import de.westnordost.streetcomplete.osm.footway.title
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithDescription
import de.westnordost.streetcomplete.ui.common.overlay.ItemSelectOverlayForm
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Form to select the kind of footway (footway=*) of a highway=footway. Only footway=* is changed.
 *  An existing value that can not be selected is shown with its raw value and kept until another
 *  kind is selected explicitly. */
@Composable
fun FootwayKindForm(
    on: (OverlayAction) -> Unit,
    element: Element,
    preferences: Preferences = koinInject(),
) {
    val originalKind = remember(element) { parseFootwayKind(element.tags) }
    val rawFootway = element.tags["footway"]

    ItemSelectOverlayForm(
        on = on,
        itemsPerRow = 1,
        items = FootwayKind.entries,
        selectableItems = FootwayKind.selectable,
        initialSelectedItem = originalKind,
        itemContent = { item ->
            ImageWithDescription(
                painter = item.icon?.let { painterResource(it) },
                title = stringResource(item.title),
                description = when (item) {
                    FootwayKind.UNSPECIFIED -> stringResource(Res.string.footway_kind_unspecified_description)
                    FootwayKind.UNSUPPORTED -> "footway=$rawFootway"
                    else -> "footway=${item.osmValue}"
                },
                imageSize = DpSize(48.dp, 48.dp),
            )
        },
        // several kinds share the same neutral icon, so the label is shown instead
        lastPickedItemContent = { item -> Text(stringResource(item.title)) },
        onClickOk = { selectedItem ->
            val tagChanges = StringMapChangesBuilder(element.tags)
            selectedItem.applyTo(tagChanges)
            updateElementTagsActionOrNull(element, tagChanges.create())?.let { on(Edit(it)) }
        },
        prefs = preferences,
        favoriteKey = "FootwayKindForm",
    )
}
