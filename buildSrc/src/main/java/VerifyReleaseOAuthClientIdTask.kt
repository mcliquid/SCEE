import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Fails the build when release [BuildConfig] would embed a blank or too-short OSM OAuth client id.
 * Configuration-cache safe: only declared file inputs, no Project/script captures in the action.
 */
@DisableCachingByDefault(because = "Reads generated BuildConfig; cheap verification, not worth caching")
abstract class VerifyReleaseOAuthClientIdTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val buildConfigFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val file = buildConfigFile.get().asFile
        require(file.isFile) {
            "Missing generated release BuildConfig at ${file.path}"
        }
        val length = ReleaseOAuthClientIdVerifier.verifyBuildConfigText(file.readText())
        // Length-only — never log the client id value.
        logger.lifecycle(
            "Verified release BuildConfig OAuth client id (length=$length, value not logged)"
        )
    }
}

/** Pure verification used by the task and unit tests. Never returns or logs the client id. */
object ReleaseOAuthClientIdVerifier {
    private val liveClientIdPattern =
        Regex("""OAUTH2_CLIENT_ID_LIVE\s*=\s*"([^"]*)"""")

    /**
     * @return length of the embedded live client id
     * @throws IllegalArgumentException / IllegalStateException when missing, blank, or too short
     */
    fun verifyBuildConfigText(text: String): Int {
        val match = liveClientIdPattern.find(text)
            ?: error("OAUTH2_CLIENT_ID_LIVE not found in release BuildConfig")
        val embeddedId = match.groupValues[1].trim()
        require(embeddedId.isNotEmpty()) {
            "Release BuildConfig.OAUTH2_CLIENT_ID_LIVE is blank. " +
                "Set app.sceemc.oauth2ClientId (gradle.properties / -P / CI secret) before assembleRelease."
        }
        require(embeddedId.length >= 8) {
            "Release OAuth client id is too short to be a valid OSM client id"
        }
        return embeddedId.length
    }
}
