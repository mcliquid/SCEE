package de.westnordost.streetcomplete.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AppBarDefaults
import androidx.compose.material.IconButton
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.data.imagery.ImageryCoverageParser
import de.westnordost.streetcomplete.data.imagery.ManualRasterCoverage
import de.westnordost.streetcomplete.data.imagery.ManualRasterSource
import de.westnordost.streetcomplete.data.imagery.ManualRasterSourcesStore
import de.westnordost.streetcomplete.data.imagery.RasterTileUrlNormalizer
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.cancel
import de.westnordost.streetcomplete.resources.ok
import de.westnordost.streetcomplete.resources.pref_tile_maxzoom
import de.westnordost.streetcomplete.resources.pref_tile_source_add
import de.westnordost.streetcomplete.resources.pref_tile_source_coverage
import de.westnordost.streetcomplete.resources.pref_tile_source_coverage_hint
import de.westnordost.streetcomplete.resources.pref_tile_source_delete
import de.westnordost.streetcomplete.resources.pref_tile_source_edit_title
import de.westnordost.streetcomplete.resources.pref_tile_source_hide_labels
import de.westnordost.streetcomplete.resources.pref_tile_source_import_geojson
import de.westnordost.streetcomplete.resources.pref_tile_source_message
import de.westnordost.streetcomplete.resources.pref_tile_source_name
import de.westnordost.streetcomplete.resources.pref_tile_source_prefer_auto
import de.westnordost.streetcomplete.resources.pref_tile_source_prefer_auto_summary
import de.westnordost.streetcomplete.resources.pref_tile_source_title
import de.westnordost.streetcomplete.ui.common.BackIcon
import de.westnordost.streetcomplete.ui.common.TextField2
import de.westnordost.streetcomplete.ui.common.dialogs.AlertDialog
import de.westnordost.streetcomplete.ui.common.settings.Preference
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.readString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun ManualRasterSourcesScreen(
    onClickBack: () -> Unit,
) {
    val store: ManualRasterSourcesStore = koinInject()
    var sources by remember { mutableStateOf(store.getAll()) }
    var editing by remember { mutableStateOf<ManualRasterSource?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(Res.string.pref_tile_source_title)) },
            windowInsets = AppBarDefaults.topAppBarWindowInsets,
            navigationIcon = { IconButton(onClick = onClickBack) { BackIcon() } },
        )
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
        ) {
            Preference(
                name = stringResource(Res.string.pref_tile_source_add),
                onClick = { creating = true }
            )
            val preferLabel = stringResource(Res.string.pref_tile_source_prefer_auto)
            val coverageLabel = stringResource(Res.string.pref_tile_source_coverage)
            sources.forEach { source ->
                val hasCoverage = ImageryCoverageParser.parse(source.coverage) != null
                val coverageHint = when {
                    hasCoverage && source.preferInAuto -> preferLabel
                    hasCoverage -> coverageLabel
                    else -> source.url.take(80)
                }
                Preference(
                    name = source.name,
                    description = coverageHint,
                    onClick = { editing = source }
                )
            }
        }
    }

    if (creating) {
        ManualRasterSourceDialog(
            initial = ManualRasterSource(
                id = store.createId(),
                name = "",
                url = "",
                maxZoom = ApplicationConstants.RASTER_DEFAULT_MAXZOOM,
            ),
            onDismiss = { creating = false },
            onSave = {
                store.put(it)
                sources = store.getAll()
                creating = false
            },
            onDelete = null,
        )
    }
    editing?.let { source ->
        ManualRasterSourceDialog(
            initial = source,
            onDismiss = { editing = null },
            onSave = {
                store.put(it)
                sources = store.getAll()
                editing = null
            },
            onDelete = {
                store.delete(source.id)
                sources = store.getAll()
                editing = null
            },
        )
    }
}

@Composable
private fun ManualRasterSourceDialog(
    initial: ManualRasterSource,
    onDismiss: () -> Unit,
    onSave: (ManualRasterSource) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(TextFieldValue(initial.name)) }
    var url by remember { mutableStateOf(TextFieldValue(initial.url)) }
    var maxZoom by remember {
        mutableStateOf(TextFieldValue(initial.maxZoom.toString()))
    }
    var hideLabels by remember { mutableStateOf(initial.hideLabels) }
    var preferInAuto by remember { mutableStateOf(initial.preferInAuto) }
    var coverageGeoJson by remember {
        mutableStateOf(TextFieldValue(initial.coverage?.geoJson.orEmpty()))
    }
    var minLat by remember {
        mutableStateOf(TextFieldValue(initial.coverage?.minLat?.toString().orEmpty()))
    }
    var minLon by remember {
        mutableStateOf(TextFieldValue(initial.coverage?.minLon?.toString().orEmpty()))
    }
    var maxLat by remember {
        mutableStateOf(TextFieldValue(initial.coverage?.maxLat?.toString().orEmpty()))
    }
    var maxLon by remember {
        mutableStateOf(TextFieldValue(initial.coverage?.maxLon?.toString().orEmpty()))
    }
    val zoomOk = maxZoom.text.toIntOrNull() != null
    val urlOk = RasterTileUrlNormalizer.isValidTemplate(url.text)
    val nameOk = name.text.isNotBlank()
    val geoJsonText = coverageGeoJson.text.trim()
    val geoJsonOk = geoJsonText.isEmpty() || ImageryCoverageParser.parseGeoJson(geoJsonText) != null
    val bboxValues = listOf(minLat, minLon, maxLat, maxLon).map { it.text.trim() }
    val bboxPartial = bboxValues.any { it.isNotEmpty() }
    val bboxOk = !bboxPartial || bboxValues.all { it.toDoubleOrNull() != null }

    fun buildCoverage(): ManualRasterCoverage? {
        val geo = geoJsonText.ifEmpty { null }
        val hasBbox = bboxValues.all { it.isNotEmpty() }
        if (geo == null && !hasBbox) return null
        return ManualRasterCoverage(
            minLat = if (hasBbox) minLat.text.trim().toDoubleOrNull() else null,
            minLon = if (hasBbox) minLon.text.trim().toDoubleOrNull() else null,
            maxLat = if (hasBbox) maxLat.text.trim().toDoubleOrNull() else null,
            maxLon = if (hasBbox) maxLon.text.trim().toDoubleOrNull() else null,
            geoJson = geo,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.pref_tile_source_edit_title)) },
        buttonRow = {
            if (onDelete != null) {
                TextButton(onDelete) { Text(stringResource(Res.string.pref_tile_source_delete)) }
            }
            TextButton(onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.text.trim(),
                            url = url.text.trim(),
                            maxZoom = maxZoom.text.toInt(),
                            hideLabels = hideLabels,
                            preferInAuto = preferInAuto,
                            coverage = buildCoverage(),
                        )
                    )
                },
                enabled = zoomOk && urlOk && nameOk && geoJsonOk && bboxOk,
            ) { Text(stringResource(Res.string.ok)) }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(Res.string.pref_tile_source_message))
                Spacer(Modifier.size(6.dp))
                TextField2(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.pref_tile_source_name)) },
                )
                Spacer(Modifier.size(6.dp))
                TextField2(value = url, onValueChange = { url = it })
                Spacer(Modifier.size(6.dp))
                TextField2(
                    value = maxZoom,
                    onValueChange = { maxZoom = it },
                    label = { Text(stringResource(Res.string.pref_tile_maxzoom)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { hideLabels = !hideLabels },
                ) {
                    Text(stringResource(Res.string.pref_tile_source_hide_labels), Modifier.weight(1f))
                    Switch(hideLabels, { hideLabels = it })
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { preferInAuto = !preferInAuto },
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.pref_tile_source_prefer_auto))
                        Text(
                            stringResource(Res.string.pref_tile_source_prefer_auto_summary),
                            style = androidx.compose.material.MaterialTheme.typography.caption,
                        )
                    }
                    Switch(preferInAuto, { preferInAuto = it })
                }
                Spacer(Modifier.size(6.dp))
                Text(stringResource(Res.string.pref_tile_source_coverage))
                Text(
                    stringResource(Res.string.pref_tile_source_coverage_hint),
                    style = androidx.compose.material.MaterialTheme.typography.caption,
                )
                Spacer(Modifier.size(4.dp))
                TextButton({
                    scope.launch {
                        val file = FileKit.openFilePicker()
                        if (file != null) {
                            val text = withContext(Dispatchers.IO) { file.readString() }
                            coverageGeoJson = TextFieldValue(text)
                        }
                    }
                }) {
                    Text(stringResource(Res.string.pref_tile_source_import_geojson))
                }
                TextField2(
                    value = coverageGeoJson,
                    onValueChange = { coverageGeoJson = it },
                    label = { Text("GeoJSON") },
                    singleLine = false,
                )
                Spacer(Modifier.size(6.dp))
                Text("Bounding box (optional)")
                Row(Modifier.fillMaxWidth()) {
                    TextField2(
                        value = minLat,
                        onValueChange = { minLat = it },
                        label = { Text("minLat") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Spacer(Modifier.size(4.dp))
                    TextField2(
                        value = minLon,
                        onValueChange = { minLon = it },
                        label = { Text("minLon") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    TextField2(
                        value = maxLat,
                        onValueChange = { maxLat = it },
                        label = { Text("maxLat") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Spacer(Modifier.size(4.dp))
                    TextField2(
                        value = maxLon,
                        onValueChange = { maxLon = it },
                        label = { Text("maxLon") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
        },
    )
}
