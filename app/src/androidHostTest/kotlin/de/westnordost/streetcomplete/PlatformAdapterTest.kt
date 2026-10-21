package de.westnordost.streetcomplete

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import de.westnordost.streetcomplete.quests.evse_id.getUrlPath
import de.westnordost.streetcomplete.quests.evse_id.getUrlQueryParameter
import de.westnordost.streetcomplete.quests.evse_id.normalizeUrl
import de.westnordost.streetcomplete.screens.settings.GpxParserContract
import de.westnordost.streetcomplete.util.platform.AndroidToaster
import de.westnordost.streetcomplete.util.platform.platformFileFromUri
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidGpxParserTest : GpxParserContract()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlatformAdapterTest {
    private val urls = listOf(
        "https://chrg.me/x?evseId=DE*ABC*E1234",
        "https://example.org/DE*ABC*E1234",
        "example.org/path/DE%2AABC%2AE1234/",
        "http://example.org/a%20b?evseid=DE%2AABC&evseId=second&x=%C3%A4",
        "https://example.org?evseId=",
        "https://example.org/?evseId=a+b",
        "https://user@example.org:8080/a/b/c?x=1#evseId=frag",
        "https://example.org",
        "not a url at all",
        "DE*ABC*E1234",
    )

    @Test fun `url parsing equals android Uri on realistic inputs`() {
        for (url in urls) {
            val uri = normalizeUrl(url).toUri()
            for (key in listOf("evseId", "evseid", "x", "missing")) {
                assertEquals(uri.getQueryParameter(key), getUrlQueryParameter(url, key), "$url / $key")
            }
            // text that is no URL cannot reach getUrlPath (it needs a known URL prefix); ktor reports it as null, Uri as ""
            if (url != "not a url at all") assertEquals(uri.path?.replace("/", ""), getUrlPath(url), url)
            else assertEquals(null, getUrlPath(url))
        }
    }

    @Test fun `file uri is resolved by the android file abstraction`() {
        val file = platformFileFromUri("file:///data/local/tmp/x.gpx")
        assertTrue(file.toString().contains("x.gpx"))
    }

    @Test fun `toast is shown on the main looper with the full text`() {
        val context = RuntimeEnvironment.getApplication()
        val toaster = AndroidToaster(context)
        assertTrue(toaster.canShowOverDialogs)
        toaster.show("ban check ä\n2")
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("ban check ä\n2", ShadowToast.getTextOfLatestToast())
    }
}
