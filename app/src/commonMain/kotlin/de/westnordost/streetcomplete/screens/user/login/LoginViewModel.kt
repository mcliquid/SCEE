package de.westnordost.streetcomplete.screens.user.login

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import de.westnordost.streetcomplete.data.UnsyncedChangesCountSource
import de.westnordost.streetcomplete.data.user.OAUTH2_AUTHORIZATION_URL
import de.westnordost.streetcomplete.data.user.OAUTH2_CLIENT_ID
import de.westnordost.streetcomplete.data.user.OAUTH2_REDIRECT_URI
import de.westnordost.streetcomplete.data.user.OAUTH2_REQUESTED_SCOPES
import de.westnordost.streetcomplete.data.user.OAUTH2_REQUIRED_SCOPES
import de.westnordost.streetcomplete.data.user.OAUTH2_TOKEN_URL
import de.westnordost.streetcomplete.data.user.UserLoginController
import de.westnordost.streetcomplete.data.user.UserLoginSource
import de.westnordost.streetcomplete.data.user.oauth.OAuthApiClient
import de.westnordost.streetcomplete.data.user.oauth.OAuthAuthorizationParams
import de.westnordost.streetcomplete.data.user.oauth.OAuthException
import de.westnordost.streetcomplete.util.ktx.launch
import de.westnordost.streetcomplete.util.logs.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@Stable
abstract class LoginViewModel : ViewModel() {
    abstract val unsyncedChangesCount: StateFlow<Int>

    abstract val loginState: StateFlow<LoginState>

    abstract val authorizationRequestUrl: String

    /** Starts the OAuth2 based login flow. */
    abstract fun startLogin()

    /** Call when the web view / browser received an error when loading the (authorization) page */
    abstract fun failAuthorization(url: String, errorCode: Int, description: String?)

    /** Returns whether the url is a redirect url destined for this OAuth authorization flow */
    abstract fun isAuthorizationResponseUrl(url: String): Boolean

    /** Continues OAuth authorization flow with given redirect url */
    abstract fun finishAuthorization(authorizationResponseUrl: String)

    /** Resets the login state to LoggedOut. Only works if current state is LoginError */
    abstract fun resetLogin()
}

sealed interface LoginState
data object LoggedOut : LoginState
data object RequestingAuthorization : LoginState
data object RetrievingAccessToken : LoginState
enum class LoginError : LoginState {
    RequiredPermissionsNotGranted,
    CommunicationError,
    /** Local/debug build without `app.sceemc.oauth2ClientId` (release builds fail at compile/packaging). */
    MissingClientId,
}
data object LoggedIn : LoginState

@Stable
class LoginViewModelImpl(
    private val unsyncedChangesCountSource: UnsyncedChangesCountSource,
    private val userLoginController: UserLoginController,
    private val oAuthApiClient: OAuthApiClient
) : LoginViewModel() {
    override val loginState = MutableStateFlow<LoginState>(
        if (userLoginController.isLoggedIn) LoggedIn else LoggedOut
    )
    override val unsyncedChangesCount = MutableStateFlow(0)

    /**
     * Authorization URL for the current login attempt. Only valid while [loginState] is
     * [RequestingAuthorization] after a successful [startLogin] that created an OAuth/PKCE session.
     */
    override val authorizationRequestUrl: String get() {
        val session = oAuthSession
            ?: error("OAuth session missing; startLogin() must create one before loading the login web view")
        return oAuthApiClient.getAuthorizationRequestUrl(session)
    }

    /**
     * One OAuth/PKCE session per login attempt. Created in [startLogin] only when
     * [OAUTH2_CLIENT_ID] is non-blank (from [de.westnordost.streetcomplete.data.user.OsmAuthConfig]
     * after Android Application startup applied BuildConfig).
     */
    private var oAuthSession: OAuthAuthorizationParams? = null

    private fun requireOAuthSession(): OAuthAuthorizationParams =
        oAuthSession
            ?: error("OAuth session missing; startLogin() must create one before continuing authorization")

    private val loginStatusListener = object : UserLoginSource.Listener {
        override fun onLoggedIn() { loginState.value = LoggedIn }
        override fun onLoggedOut() { loginState.value = LoggedOut }
    }

    init {
        launch(Dispatchers.IO) {
            unsyncedChangesCount.update { unsyncedChangesCountSource.getCount() }
        }
        userLoginController.addListener(loginStatusListener)
    }

    override fun onCleared() {
        userLoginController.removeListener(loginStatusListener)
    }

    override fun startLogin() {
        if (loginState.value != LoggedOut) return
        oAuthSession = null
        val clientId = OAUTH2_CLIENT_ID
        if (clientId.isBlank()) {
            // Debug/local: show a message instead of crashing when opening the login web view.
            // Release APKs must embed a non-blank client id (Gradle verifyReleaseOAuthClientId).
            loginState.value = LoginError.MissingClientId
            return
        }
        oAuthSession = OAuthAuthorizationParams(
            OAUTH2_AUTHORIZATION_URL,
            OAUTH2_TOKEN_URL,
            clientId,
            OAUTH2_REQUESTED_SCOPES,
            OAUTH2_REDIRECT_URI
        )
        loginState.value = RequestingAuthorization
    }

    override fun failAuthorization(url: String, errorCode: Int, description: String?) {
        Log.e(TAG, "Error for URL " + url + if (description != null) ": $description" else "")
        loginState.compareAndSet(RequestingAuthorization, LoginError.CommunicationError)
    }

    override fun isAuthorizationResponseUrl(url: String): Boolean =
        oAuthSession?.itsForMe(url) == true

    override fun finishAuthorization(authorizationResponseUrl: String) {
        launch {
            val accessToken = retrieveAccessToken(authorizationResponseUrl)
            if (accessToken != null) {
                login(accessToken)
            }
        }
    }

    private suspend fun retrieveAccessToken(authorizationResponseUrl: String): String? {
        try {
            loginState.value = RetrievingAccessToken
            val accessTokenResponse = oAuthApiClient.getAccessToken(requireOAuthSession(), authorizationResponseUrl)
            if (accessTokenResponse.grantedScopes?.containsAll(OAUTH2_REQUIRED_SCOPES) == false) {
                loginState.value = LoginError.RequiredPermissionsNotGranted
                return null
            }
            return accessTokenResponse.accessToken
        } catch (e: Exception) {
            if (e is OAuthException && e.error == "access_denied") {
                loginState.value = LoginError.RequiredPermissionsNotGranted
            } else {
                Log.e(TAG, "Error during authorization", e)
                loginState.value = LoginError.CommunicationError
            }
            return null
        }
    }

    private suspend fun login(accessToken: String) {
        userLoginController.logIn(accessToken)
    }

    override fun resetLogin() {
        if (loginState.value is LoginError) {
            oAuthSession = null
            loginState.value = LoggedOut
        }
    }

    companion object {
        private const val TAG = "Login"
    }
}
