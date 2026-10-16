package de.westnordost.streetcomplete.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.imagery.AerialImageryResolver
import de.westnordost.streetcomplete.data.imagery.AerialSourceSelection
import de.westnordost.streetcomplete.data.imagery.ManualRasterSourcesStore
import de.westnordost.streetcomplete.data.imagery.eli.EliIndexClient
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.background_type_aerial_auto
import de.westnordost.streetcomplete.resources.background_type_aerial_esri
import de.westnordost.streetcomplete.resources.background_type_map
import de.westnordost.streetcomplete.resources.pref_background_type_select
import de.westnordost.streetcomplete.ui.common.dialogs.SimpleListPickerDialog
import de.westnordost.streetcomplete.ui.common.settings.Preference
import de.westnordost.streetcomplete.util.ktx.rememberPreferenceString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

sealed class BackgroundChoice {
    data object Map : BackgroundChoice()
    data object Esri : BackgroundChoice()
    data object Auto : BackgroundChoice()
    data class Eli(val id: String, val name: String) : BackgroundChoice()
    data class Manual(val id: String, val name: String) : BackgroundChoice()
}

@Composable
fun BackgroundImageryPreference(
    mapCenter: LatLon?,
) {
    var showDialog by remember { mutableStateOf(false) }
    val description = rememberBackgroundImageryDescription(mapCenter)

    Preference(
        name = stringResource(Res.string.pref_background_type_select),
        description = description,
        onClick = { showDialog = true }
    )
    if (showDialog) {
        BackgroundImageryPickerDialog(
            mapCenter = mapCenter,
            onDismissRequest = { showDialog = false },
        )
    }
}

/** Current background / aerial source label for QuickSettings and Display settings. */
@Composable
fun rememberBackgroundImageryDescription(mapCenter: LatLon?): String? {
    val prefs: Preferences = koinInject()
    val resolver: AerialImageryResolver = koinInject()
    val manualStore: ManualRasterSourcesStore = koinInject()
    val eliClient: EliIndexClient = koinInject()
    val background = rememberPreferenceString(prefs, Prefs.THEME_BACKGROUND, "MAP")
    val aerialKey = rememberPreferenceString(
        prefs, Prefs.AERIAL_SOURCE, AerialSourceSelection.DEFAULT_ENCODED
    )
    var description by remember { mutableStateOf<String?>(null) }
    val mapLabel = stringResource(Res.string.background_type_map)
    val esriLabel = stringResource(Res.string.background_type_aerial_esri)
    val autoLabel = stringResource(Res.string.background_type_aerial_auto)

    LaunchedEffect(background, aerialKey, mapCenter, mapLabel, esriLabel, autoLabel) {
        val index = withContext(Dispatchers.IO) { runCatching { eliClient.getIndex() }.getOrNull() }
        val manuals = manualStore.getAll()
        description = when {
            background == "MAP" -> mapLabel
            else -> when (val sel = AerialSourceSelection.decode(aerialKey)) {
                AerialSourceSelection.Esri -> esriLabel
                AerialSourceSelection.Auto -> autoLabel
                is AerialSourceSelection.Eli ->
                    index?.findById(sel.id)?.name ?: esriLabel
                is AerialSourceSelection.Manual ->
                    manuals.find { it.id == sel.id }?.name ?: esriLabel
            }
        }
        // Touch resolver so invalid selections are cleaned when description is shown.
        resolver.ensureSelectionValid(index)
    }
    return description
}

@Composable
fun BackgroundImageryPickerDialog(
    mapCenter: LatLon?,
    onDismissRequest: () -> Unit,
    includeMapOption: Boolean = true,
) {
    val prefs: Preferences = koinInject()
    val resolver: AerialImageryResolver = koinInject()
    val manualStore: ManualRasterSourcesStore = koinInject()
    val eliClient: EliIndexClient = koinInject()
    val background = rememberPreferenceString(prefs, Prefs.THEME_BACKGROUND, "MAP")
    val aerialKey = rememberPreferenceString(
        prefs, Prefs.AERIAL_SOURCE, AerialSourceSelection.DEFAULT_ENCODED
    )
    var choices by remember { mutableStateOf<List<BackgroundChoice>>(emptyList()) }
    val mapLabel = stringResource(Res.string.background_type_map)
    val esriLabel = stringResource(Res.string.background_type_aerial_esri)
    val autoLabel = stringResource(Res.string.background_type_aerial_auto)

    LaunchedEffect(mapCenter, mapLabel, esriLabel, autoLabel, includeMapOption) {
        val index = withContext(Dispatchers.IO) { runCatching { eliClient.getIndex() }.getOrNull() }
        val manuals = manualStore.getAll()
        val eliLayers = resolver.coveringEliLayers(mapCenter, index)
        choices = buildList {
            if (includeMapOption) add(BackgroundChoice.Map)
            add(BackgroundChoice.Esri)
            add(BackgroundChoice.Auto)
            eliLayers.forEach { add(BackgroundChoice.Eli(it.id, it.name)) }
            manuals.forEach { add(BackgroundChoice.Manual(it.id, it.name)) }
        }
    }

    val selected = currentChoice(background, aerialKey, choices, includeMapOption)
    SimpleListPickerDialog(
        onDismissRequest = onDismissRequest,
        items = choices,
        selectedItem = selected,
        onItemSelected = { choice ->
            applyBackgroundChoice(choice, prefs, resolver)
            onDismissRequest()
        },
        getItemName = {
            when (it) {
                BackgroundChoice.Map -> mapLabel
                BackgroundChoice.Esri -> esriLabel
                BackgroundChoice.Auto -> autoLabel
                is BackgroundChoice.Eli -> it.name
                is BackgroundChoice.Manual -> it.name
            }
        },
    )
}

fun applyBackgroundChoice(
    choice: BackgroundChoice,
    prefs: Preferences,
    resolver: AerialImageryResolver,
) {
    when (choice) {
        BackgroundChoice.Map ->
            prefs.putString(Prefs.THEME_BACKGROUND, "MAP")
        BackgroundChoice.Esri -> {
            prefs.putString(Prefs.THEME_BACKGROUND, "AERIAL")
            resolver.setSelection(AerialSourceSelection.Esri)
        }
        BackgroundChoice.Auto -> {
            prefs.putString(Prefs.THEME_BACKGROUND, "AERIAL")
            resolver.setSelection(AerialSourceSelection.Auto)
        }
        is BackgroundChoice.Eli -> {
            prefs.putString(Prefs.THEME_BACKGROUND, "AERIAL")
            resolver.setSelection(AerialSourceSelection.Eli(choice.id))
        }
        is BackgroundChoice.Manual -> {
            prefs.putString(Prefs.THEME_BACKGROUND, "AERIAL")
            resolver.setSelection(AerialSourceSelection.Manual(choice.id))
        }
    }
}

private fun currentChoice(
    background: String,
    aerialKey: String,
    choices: List<BackgroundChoice>,
    includeMapOption: Boolean,
): BackgroundChoice {
    if (includeMapOption && background == "MAP") return BackgroundChoice.Map
    return when (val sel = AerialSourceSelection.decode(aerialKey)) {
        AerialSourceSelection.Esri -> BackgroundChoice.Esri
        AerialSourceSelection.Auto -> BackgroundChoice.Auto
        is AerialSourceSelection.Eli ->
            choices.filterIsInstance<BackgroundChoice.Eli>().find { it.id == sel.id }
                ?: BackgroundChoice.Esri
        is AerialSourceSelection.Manual ->
            choices.filterIsInstance<BackgroundChoice.Manual>().find { it.id == sel.id }
                ?: BackgroundChoice.Esri
    }
}
