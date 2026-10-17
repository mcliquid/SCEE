package de.westnordost.streetcomplete.overlays.traffic_sign

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSign
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignCatalogue
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignPart
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignPickerKind
import de.westnordost.streetcomplete.osm.traffic_sign.categories
import de.westnordost.streetcomplete.osm.traffic_sign.isValidTrafficSignValueInput
import de.westnordost.streetcomplete.osm.traffic_sign.normalizeTrafficSignValueInput
import de.westnordost.streetcomplete.osm.traffic_sign.parseCustomTrafficSign
import de.westnordost.streetcomplete.osm.traffic_sign.search
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.dialogs.AlertDialog
import de.westnordost.streetcomplete.ui.common.dialogs.ScrollableAlertDialog
import de.westnordost.streetcomplete.ui.common.traffic_sign.TrafficSignPartImage
import org.jetbrains.compose.resources.stringResource

/** Dialog to pick one sign from the catalogue: search, categories, and a way to enter a sign that
 *  is not in the list. If [main] is set, additional signs are picked for it: by default only the
 *  ones that fit to it, but all can be shown. */
@Composable
fun TrafficSignCatalogueDialog(
    catalogue: TrafficSignCatalogue,
    kind: TrafficSignPickerKind,
    main: TrafficSign?,
    onDismiss: () -> Unit,
    onPick: (TrafficSignPart) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var customText by rememberSaveable { mutableStateOf("") }
    var valueFor by remember { mutableStateOf<TrafficSign?>(null) }

    val categories = remember(catalogue, kind) { catalogue.categories(kind) }
    val results = remember(catalogue, kind, query, category, main, showAll) {
        catalogue.search(query, kind, category, main, showAll)
    }
    val custom = remember(customText, kind) {
        if (customText.isBlank()) null
        else parseCustomTrafficSign(customText, catalogue, kind == TrafficSignPickerKind.MODIFIER)
    }

    fun pick(sign: TrafficSign) {
        if (sign.hasVariableValue && sign.valuePrompt != null) valueFor = sign
        else onPick(TrafficSignPart.of(sign))
    }

    ScrollableAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(
                if (kind == TrafficSignPickerKind.MAIN) Res.string.traffic_sign_overlay_add_main
                else Res.string.traffic_sign_overlay_add_modifier
            ))
        },
        content = {
            Column(
                Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(Res.string.traffic_sign_overlay_search)) },
                    singleLine = true,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Chip(stringResource(Res.string.traffic_sign_overlay_filter_all), category == null) { category = null }
                    for (c in categories) Chip(c, category == c) { category = c }
                }
                if (kind == TrafficSignPickerKind.MODIFIER && main != null) {
                    Row(
                        Modifier.clickable { showAll = !showAll },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material.Checkbox(showAll, { showAll = it })
                        Text(stringResource(Res.string.traffic_sign_overlay_show_all))
                    }
                }
                if (results.isEmpty()) {
                    Text(stringResource(Res.string.traffic_sign_overlay_no_results))
                }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(results, key = { it.osmValuePart }) { sign ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { pick(sign) }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TrafficSignPartImage(TrafficSignPart.of(sign), catalogue.country, signHeight = 56.dp)
                            Column(Modifier.weight(1f)) {
                                Text(sign.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.body2)
                                Text(sign.descriptiveName, style = MaterialTheme.typography.body2)
                            }
                        }
                    }
                }
                Text(
                    stringResource(Res.string.traffic_sign_overlay_custom_sign),
                    style = MaterialTheme.typography.body2,
                    fontWeight = FontWeight.Bold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(Res.string.traffic_sign_overlay_custom_sign_hint)) },
                        singleLine = true,
                    )
                    TextButton(onClick = { custom?.let(onPick) }, enabled = custom != null) {
                        Text(stringResource(Res.string.traffic_sign_overlay_custom_sign_add))
                    }
                }
            }
        },
        buttonRow = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )

    valueFor?.let { sign ->
        TrafficSignValueDialog(
            sign = sign,
            onDismiss = { valueFor = null },
            onConfirm = { value ->
                valueFor = null
                onPick(TrafficSignPart.of(sign, value))
            }
        )
    }
}

/** Dialog to enter the variable value of a sign, e.g. the speed limit */
@Composable
private fun TrafficSignValueDialog(
    sign: TrafficSign,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val prompt = sign.valuePrompt ?: return
    var text by rememberSaveable { mutableStateOf(sign.defaultValue ?: prompt.defaultValue) }
    val isValid = isValidTrafficSignValueInput(prompt.format, text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.traffic_sign_overlay_value_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(sign.descriptiveName.replace("??", "…"), style = MaterialTheme.typography.body2)
                Text(prompt.prompt, style = MaterialTheme.typography.caption)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    isError = !isValid,
                )
                if (!isValid) {
                    Text(
                        stringResource(Res.string.traffic_sign_overlay_value_invalid),
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption,
                    )
                }
            }
        },
        buttonRow = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(
                onClick = { onConfirm(normalizeTrafficSignValueInput(prompt.format, text)) },
                enabled = isValid,
            ) { Text(stringResource(Res.string.ok)) }
        },
    )
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
    Text(
        text,
        modifier = Modifier
            .border(BorderStroke(if (selected) 2.dp else 1.dp, color), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
        style = MaterialTheme.typography.body2,
    )
}
