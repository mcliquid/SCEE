package de.westnordost.streetcomplete.data.user

import de.westnordost.streetcomplete.data.user.oauth.OAuthApiClientImpl
import de.westnordost.streetcomplete.data.user.oauth.OAuthAuthorizationParams
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.http.Url
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OsmAuthConfigTest {

    private val previousClientIdLive = OsmAuthConfig.clientIdLive
    private val previousClientIdTest = OsmAuthConfig.clientIdTest
    private val previousCallbackScheme = OsmAuthConfig.callbackScheme
    private val previousRequestedScopes = OsmAuthConfig.requestedScopes

    @BeforeTest fun saveDefaults() {
        OsmAuthConfig.clientIdLive = OsmAuthConfig.DEFAULT_CLIENT_ID_LIVE
        OsmAuthConfig.clientIdTest = OsmAuthConfig.DEFAULT_CLIENT_ID_TEST
        OsmAuthConfig.callbackScheme = OsmAuthConfig.DEFAULT_CALLBACK_SCHEME
        OsmAuthConfig.requestedScopes = OsmAuthConfig.DEFAULT_REQUESTED_SCOPES
    }

    @AfterTest fun restore() {
        OsmAuthConfig.clientIdLive = previousClientIdLive
        OsmAuthConfig.clientIdTest = previousClientIdTest
        OsmAuthConfig.callbackScheme = previousCallbackScheme
        OsmAuthConfig.requestedScopes = previousRequestedScopes
    }

    @Test fun `requested scopes include write_gpx and cover required scopes`() {
        assertEquals(
            listOf("read_prefs", "write_api", "write_notes", "write_gpx"),
            OAUTH2_REQUESTED_SCOPES
        )
        assertTrue(OAUTH2_REQUESTED_SCOPES.containsAll(OAUTH2_REQUIRED_SCOPES))
    }

    @Test fun `authorization URL uses SCEE-mc client and redirect with the shared four scopes`() {
        OsmAuthConfig.clientIdLive = "sceemc-test-client-id"
        OsmAuthConfig.callbackScheme = "sceemc"

        val client = OAuthApiClientImpl(HttpClient(MockEngine { respondOk() }))
        val url = Url(
            client.getAuthorizationRequestUrl(
                OAuthAuthorizationParams(
                    OAUTH2_AUTHORIZATION_URL,
                    OAUTH2_TOKEN_URL,
                    OAUTH2_CLIENT_ID,
                    OAUTH2_REQUESTED_SCOPES,
                    OAUTH2_REDIRECT_URI
                )
            )
        )

        assertEquals("sceemc-test-client-id", url.parameters["client_id"])
        assertEquals("sceemc://oauth", url.parameters["redirect_uri"])
        assertEquals("read_prefs write_api write_notes write_gpx", url.parameters["scope"])
    }

    @Test fun `default authorization URL keeps streetcomplete redirect and four scopes`() {
        val client = OAuthApiClientImpl(HttpClient(MockEngine { respondOk() }))
        val url = Url(
            client.getAuthorizationRequestUrl(
                OAuthAuthorizationParams(
                    OAUTH2_AUTHORIZATION_URL,
                    OAUTH2_TOKEN_URL,
                    OAUTH2_CLIENT_ID,
                    OAUTH2_REQUESTED_SCOPES,
                    OAUTH2_REDIRECT_URI
                )
            )
        )

        assertEquals("streetcomplete://oauth", url.parameters["redirect_uri"])
        assertEquals("read_prefs write_api write_notes write_gpx", url.parameters["scope"])
    }
}
