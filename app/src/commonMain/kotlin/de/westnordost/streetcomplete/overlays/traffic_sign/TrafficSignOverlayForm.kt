package de.westnordost.streetcomplete.overlays.traffic_sign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.osm.traffic_sign.applyConfirmed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.UpdateElementTagsAction
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Way
import de.westnordost.streetcomplete.data.overlays.Action
import de.westnordost.streetcomplete.data.overlays.Edit
import de.westnordost.streetcomplete.data.overlays.OverlayAction
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.traffic_signs.TrafficSignCatalogues
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignCatalogue
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.overlay.OverlayForm
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private const val COUNTRY = "DE"

@Composable
fun TrafficSignOverlayForm(
    on: (OverlayAction) -> Unit,
    way: Way,
    geometry: ElementGeometry,
    countryInfo: CountryInfo,
    catalogues: TrafficSignCatalogues = koinInject(),
) {
    if (countryInfo.countryCode != COUNTRY) {
        OverlayForm(on = on, isComplete = false, hasChanges = false, onClickOk = {}) {
            Text(stringResource(Res.string.traffic_sign_overlay_germany_only))
        }
        return
    }
    when (val s = rememberTrafficSignCatalogueState(catalogues, COUNTRY)) {
        TrafficSignCatalogueState.Loading -> OverlayForm(on = on, isComplete = false, hasChanges = false, onClickOk = {}) {
            Text(stringResource(Res.string.traffic_sign_overlay_loading))
        }
        TrafficSignCatalogueState.Failed -> OverlayForm(on = on, isComplete = false, hasChanges = false, onClickOk = {}) {
            Text(stringResource(Res.string.traffic_sign_overlay_catalogue_missing))
        }
        is TrafficSignCatalogueState.Ready -> TrafficSignOverlayFormContent(on, way, geometry, s.catalogue)
    }
}

@Composable
private fun TrafficSignOverlayFormContent(
    on: (OverlayAction) -> Unit,
    way: Way,
    geometry: ElementGeometry,
    catalogue: TrafficSignCatalogue,
) {
    val state = rememberTrafficSignWayEditorState(way, catalogue)
    val preferences: Preferences = koinInject()
    var confirmNone by rememberSaveable { mutableStateOf(false) }

    fun confirm() {
        val changes = StringMapChangesBuilder(way.tags)
        state.tagging.applyConfirmed(changes, state.confirmed)
        state.rememberPicked(preferences)
        on(Edit(UpdateElementTagsAction(way, changes.create())))
    }

    OverlayForm(
        on = on,
        isComplete = state.isEdited,
        hasChanges = state.hasChanges,
        onClickOk = {
            if (state.needsConfirmation) confirmNone = true else confirm()
        },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TrafficSignWayEditor(state, geometry)
        }
    }

    if (confirmNone) {
        TrafficSignConfirmDialog(
            showNoneWarning = state.needsNoneConfirmation,
            preview = state.confirmationPreview,
            unreadable = state.replacedUnreadable,
            accessWarning = state.noneAccessWarning,
            proposals = state.appliedProposals,
            remainingExceptions = state.confirmedChanges.remainingExceptions,
            // nothing is saved when the tag editor is opened
            onEditTags = { confirmNone = false; on(Action.EditTags) },
            onCancel = { confirmNone = false },
            onConfirm = { confirmNone = false; confirm() },
        )
    }
}
