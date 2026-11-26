package de.westnordost.streetcomplete.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.cancel
import de.westnordost.streetcomplete.resources.ok
import de.westnordost.streetcomplete.resources.pref_show_nearby_quests_title
import de.westnordost.streetcomplete.resources.show_nearby_quests_all_types
import de.westnordost.streetcomplete.resources.show_nearby_quests_disable
import de.westnordost.streetcomplete.resources.show_nearby_quests_distance
import de.westnordost.streetcomplete.resources.show_nearby_quests_even_hidden
import de.westnordost.streetcomplete.resources.show_nearby_quests_visible
import de.westnordost.streetcomplete.ui.common.RadioGroup
import de.westnordost.streetcomplete.ui.common.TextField2
import de.westnordost.streetcomplete.ui.common.dialogs.AlertDialog
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal fun parseShowNearbyQuestsDistance(distanceText: String): Float =
    (distanceText.toFloatOrNull() ?: 0f).coerceIn(0f, 10f)

/** Persists mode (0–3) and distance — used in unit tests and when both must be set atomically. */
internal fun applyShowNearbyQuestsSettings(
    prefs: Preferences,
    mode: Int,
    distanceText: String,
) {
    prefs.putInt(Prefs.SHOW_NEARBY_QUESTS, mode)
    prefs.putFloat(Prefs.SHOW_NEARBY_QUESTS_DISTANCE, parseShowNearbyQuestsDistance(distanceText))
}

/** SCEE 63.4: distance was written only when OK was pressed. */
internal fun applyShowNearbyQuestsDistance(prefs: Preferences, distanceText: String) {
    prefs.putFloat(Prefs.SHOW_NEARBY_QUESTS_DISTANCE, parseShowNearbyQuestsDistance(distanceText))
}

internal fun defaultShowNearbyQuestsMode(prefs: Preferences): Int =
    prefs.getInt(Prefs.SHOW_NEARBY_QUESTS, 0)

internal fun nearbyQuestModeOptions(expertMode: Boolean): List<Pair<Int, StringResource>> = listOfNotNull(
    0 to Res.string.show_nearby_quests_disable,
    1 to Res.string.show_nearby_quests_visible,
    if (expertMode) 2 to Res.string.show_nearby_quests_all_types else null,
    if (expertMode) 3 to Res.string.show_nearby_quests_even_hidden else null,
)

@Composable
internal fun ShowNearbyQuestsDialog(
    prefs: Preferences,
    onDismissRequest: () -> Unit,
) {
    val items = remember(prefs.expertMode) { nearbyQuestModeOptions(prefs.expertMode) }
    var selected by remember { mutableIntStateOf(defaultShowNearbyQuestsMode(prefs)) }
    var distance by remember {
        mutableStateOf(TextFieldValue(prefs.getFloat(Prefs.SHOW_NEARBY_QUESTS_DISTANCE, 0.0f).toString()))
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(Res.string.pref_show_nearby_quests_title)) },
        buttonRow = {
            TextButton(onDismissRequest) { Text(stringResource(Res.string.cancel)) }
            TextButton({
                applyShowNearbyQuestsDistance(prefs, distance.text)
                onDismissRequest()
            }) { Text(stringResource(Res.string.ok)) }
        },
        text = {
            Column {
                RadioGroup(
                    items,
                    onSelectionChange = {
                        selected = it.first
                        // SCEE legacy: mode was written on radio change in the Android dialog
                        prefs.putInt(Prefs.SHOW_NEARBY_QUESTS, it.first)
                    },
                    selectedOption = items.find { it.first == selected },
                    itemContent = { Text(stringResource(it.second)) },
                )
                Text(stringResource(Res.string.show_nearby_quests_distance))
                TextField2(
                    distance,
                    { distance = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
    )
}