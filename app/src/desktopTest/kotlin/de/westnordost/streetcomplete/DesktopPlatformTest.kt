package de.westnordost.streetcomplete

import de.westnordost.streetcomplete.screens.settings.GpxParserContract
import de.westnordost.streetcomplete.util.platform.DesktopToaster
import de.westnordost.streetcomplete.util.platform.platformFileFromUri
import io.github.vinceglb.filekit.absolutePath
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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

    @Test fun `toast never throws and is not shown over dialogs`() {
        val toaster = DesktopToaster()
        assertFalse(toaster.canShowOverDialogs)
        if (java.awt.GraphicsEnvironment.isHeadless()) toaster.show("headless")
    }
}
