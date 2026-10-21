package de.westnordost.streetcomplete.screens.settings

import io.github.vinceglb.filekit.dialogs.FileKitType

/**
 * FileKit [FileKitType.File] expects extensions without a leading dot (e.g. `"gpx"`).
 * On Windows, filekit builds dialog patterns as `*.$extension`; a leading dot yields `*..gpx`.
 */
internal val GpxOpenFilePickerType = FileKitType.File("gpx")

internal val CustomGeometryOpenFilePickerType = FileKitType.File("geojson", "json")

/** Mirrors filekit's Windows [COMDLG_FILTERSPEC] pattern for regression tests. */
internal fun windowsFileDialogFilterPattern(extensions: Set<String>): String =
    extensions.joinToString(";") { "*.$it" }
