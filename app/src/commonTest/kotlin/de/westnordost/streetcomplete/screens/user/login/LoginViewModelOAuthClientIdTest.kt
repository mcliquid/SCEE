package de.westnordost.streetcomplete.screens.user.login

import de.westnordost.streetcomplete.data.UnsyncedChangesCountSource
import de.westnordost.streetcomplete.data.osm.edits.ElementEditsSource
import de.westnordost.streetcomplete.data.osmnotes.edits.NoteEditsSource
import de.westnordost.streetcomplete.data.user.OAUTH2_CLIENT_ID
import de.westnordost.streetcomplete.data.user.OsmAuthConfig
import de.westnordost.streetcomplete.data.user.UserLoginController
import de.westnordost.streetcomplete.data.user.oauth.OAuthApiClientImpl
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.http.Url
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LoginViewModelOAuthClientIdTest {

    private val previousClientIdLive = OsmAuthConfig.clientIdLive
    private val previousCallbackScheme = OsmAuthConfig.callbackScheme
    private val previousRequestedScopes = OsmAuthConfig.requestedScopes

    @BeforeTest fun saveDefaults() {
        OsmAuthConfig.clientIdLive = OsmAuthConfig.DEFAULT_CLIENT_ID_LIVE
        OsmAuthConfig.callbackScheme = "sceemc"
        OsmAuthConfig.requestedScopes = OsmAuthConfig.DEFAULT_REQUESTED_SCOPES
    }

    @AfterTest fun restore() {
        OsmAuthConfig.clientIdLive = previousClientIdLive
        OsmAuthConfig.callbackScheme = previousCallbackScheme
        OsmAuthConfig.requestedScopes = previousRequestedScopes
    }

    @Test fun `startLogin with blank client id sets MissingClientId without creating a URL session`() {
        OsmAuthConfig.clientIdLive = ""
        assertEquals("", OAUTH2_CLIENT_ID)

        val viewModel = createViewModel()
        assertIs<LoggedOut>(viewModel.loginState.value)

        viewModel.startLogin()

        assertEquals(LoginError.MissingClientId, viewModel.loginState.value)
        assertFailsWith<IllegalStateException> { viewModel.authorizationRequestUrl }
        assertEquals(false, viewModel.isAuthorizationResponseUrl("sceemc://oauth"))
    }

    @Test fun `startLogin with client id creates authorization URL containing client_id and sceemc redirect`() {
        OsmAuthConfig.clientIdLive = "sceemc-debug-client-id"
        OsmAuthConfig.callbackScheme = "sceemc"

        val viewModel = createViewModel()
        viewModel.startLogin()

        assertIs<RequestingAuthorization>(viewModel.loginState.value)
        val url = Url(viewModel.authorizationRequestUrl)
        assertEquals("sceemc-debug-client-id", url.parameters["client_id"])
        assertEquals("sceemc://oauth", url.parameters["redirect_uri"])
        assertTrue(url.parameters["code_challenge"].orEmpty().isNotBlank())
    }

    @Test fun `each successful startLogin creates a fresh PKCE code challenge`() {
        OsmAuthConfig.clientIdLive = "sceemc-debug-client-id"
        OsmAuthConfig.callbackScheme = "sceemc"

        val first = createViewModel().also { it.startLogin() }
        val challenge1 = Url(first.authorizationRequestUrl).parameters["code_challenge"]

        val second = createViewModel().also { it.startLogin() }
        val challenge2 = Url(second.authorizationRequestUrl).parameters["code_challenge"]

        assertNotEquals(challenge1, challenge2)
    }

    private fun createViewModel(): LoginViewModelImpl {
        val noteEditsSource = mock<NoteEditsSource> {
            every { getUnsyncedCount() } returns 0
            every { addListener(any()) } returns Unit
            every { removeListener(any()) } returns Unit
        }
        val elementEditsSource = mock<ElementEditsSource> {
            every { getUnsyncedCount() } returns 0
            every { getPositiveUnsyncedCount() } returns 0
            every { addListener(any()) } returns Unit
            every { removeListener(any()) } returns Unit
        }
        return LoginViewModelImpl(
            UnsyncedChangesCountSource(noteEditsSource, elementEditsSource),
            UserLoginController(inMemoryPrefs()),
            OAuthApiClientImpl(HttpClient(MockEngine { respondOk() })),
        )
    }
}
