package de.westnordost.streetcomplete

import de.westnordost.streetcomplete.screens.settings.CustomGeometryOpenFilePickerType
import de.westnordost.streetcomplete.screens.settings.GpxOpenFilePickerType
import de.westnordost.streetcomplete.screens.settings.GpxParserContract
import de.westnordost.streetcomplete.screens.settings.windowsFileDialogFilterPattern
import io.github.vinceglb.filekit.dialogs.FileKitType
import de.westnordost.streetcomplete.util.platform.DesktopToaster
import de.westnordost.streetcomplete.util.platform.platformFileFromUri
import io.github.vinceglb.filekit.absolutePath
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopGpxParserTest : GpxParserContract()

class DesktopPlatformTest {
    @Test fun `file uris and plain paths point to the same file`() {
        val file = File.createTempFile("m5test", ".txt").apply { deleteOnExit() }
        assertEquals(file.absolutePath, platformFileFromUri(file.toURI().toString()).absolutePath())
        assertEquals(file.absolutePath, platformFileFromUri(file.absolutePath).absolutePath())
    }

    @Test fun `uri with spaces and unicode is decoded`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "m5 ä").apply { mkdirs(); deleteOnExit() }
        val file = File(dir, "a b.gpx").apply { writeText("x"); deleteOnExit() }
        assertEquals(file.absolutePath, platformFileFromUri(file.toURI().toString()).absolutePath())
    }

    @Test fun `open file picker types use extensions without leading dot`() {
        fun extensions(type: FileKitType.File) = type.extensions ?: error("expected extensions")
        val gpx = extensions(GpxOpenFilePickerType)
        assertTrue(gpx.none { it.startsWith('.') })
        assertEquals("*.gpx", windowsFileDialogFilterPattern(gpx))

        val geo = extensions(CustomGeometryOpenFilePickerType)
        assertTrue(geo.none { it.startsWith('.') })
        assertEquals("*.geojson;*.json", windowsFileDialogFilterPattern(geo))
    }

    @Test fun `toast never throws and is not shown over dialogs`() {
        val toaster = DesktopToaster()
        assertFalse(toaster.canShowOverDialogs)
        if (java.awt.GraphicsEnvironment.isHeadless()) toaster.show("headless")
    }
}
