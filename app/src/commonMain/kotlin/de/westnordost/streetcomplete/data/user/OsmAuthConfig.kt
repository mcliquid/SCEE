package de.westnordost.streetcomplete.data.user

/**
 * Mutable OSM OAuth 2 settings.
 *
 * Defaults match the upstream SCEE / StreetComplete OAuth application (Desktop/iOS and any
 * non-Android code paths). The Android `androidApp` process overrides client id and callback
 * scheme at startup from [de.westnordost.streetcomplete.app.BuildConfig] so SCEE-mc uses
 * `sceemc://oauth` and its own OSM application.
 *
 * Do not invent client IDs: register an application at
 * https://www.openstreetmap.org/oauth2/applications and set
 * `app.sceemc.oauth2ClientId` / `app.sceemc.oauth2ClientIdTest` in `~/.gradle/gradle.properties`
 * (or CI secrets). Redirect URI must be `sceemc://oauth`. Enable scopes
 * `read_prefs`, `write_api`, `write_notes`, and `write_gpx`.
 */
object OsmAuthConfig {
    const val DEFAULT_CLIENT_ID_LIVE = "Yyk4PmTopczrr3BWZYvLK_M-KBloCQwXgPGEzqUYTc8"
    const val DEFAULT_CLIENT_ID_TEST = "ObZ7yPf4lfs4XJ3NWysI3ukJMN0SHey1oPnNQnLmvw8"
    const val DEFAULT_CALLBACK_SCHEME = "streetcomplete"
    const val CALLBACK_HOST = "oauth"

    val DEFAULT_REQUESTED_SCOPES = listOf(
        "read_prefs",
        "write_api",
        "write_notes",
        "write_gpx",
    )

    var clientIdLive: String = DEFAULT_CLIENT_ID_LIVE
    var clientIdTest: String = DEFAULT_CLIENT_ID_TEST
    var callbackScheme: String = DEFAULT_CALLBACK_SCHEME
    /** Scopes sent in the authorization request (SCEE and SCEE-mc use the same list). */
    var requestedScopes: List<String> = DEFAULT_REQUESTED_SCOPES
}
