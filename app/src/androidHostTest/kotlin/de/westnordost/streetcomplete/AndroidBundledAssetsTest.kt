package de.westnordost.streetcomplete

import android.app.Application
import de.westnordost.streetcomplete.util.BundledAssetsContract
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidBundledAssetsTest : BundledAssetsContract() {
    // Robolectric's asset layer cannot open non-ASCII asset names on Windows (the merged asset exists, the lookup fails).
    // No shipped asset has such a name; this stays a Linux/device check.
    @Test override fun `Unicode filenames and UTF8 fixture contents are supported`() {
        assumeFalse("Robolectric on Windows cannot resolve non-ASCII asset names", System.getProperty("os.name").startsWith("Windows"))
        super.`Unicode filenames and UTF8 fixture contents are supported`()
    }
}
