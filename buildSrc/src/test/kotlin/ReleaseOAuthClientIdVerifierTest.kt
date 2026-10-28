import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReleaseOAuthClientIdVerifierTest {

    @Test
    fun `accepts non-blank live client id of sufficient length`() {
        val length = ReleaseOAuthClientIdVerifier.verifyBuildConfigText(
            """
            public final class BuildConfig {
              public static final String OAUTH2_CLIENT_ID_LIVE = "abcdefgh";
              public static final String OAUTH2_CLIENT_ID_TEST = "";
            }
            """.trimIndent()
        )
        assertEquals(8, length)
    }

    @Test
    fun `rejects blank live client id`() {
        val error = assertFailsWith<IllegalArgumentException> {
            ReleaseOAuthClientIdVerifier.verifyBuildConfigText(
                """public static final String OAUTH2_CLIENT_ID_LIVE = "";"""
            )
        }
        assertTrue(error.message!!.contains("blank"))
        assertTrue(!error.message!!.contains("abcdefgh"))
    }

    @Test
    fun `rejects whitespace-only live client id`() {
        assertFailsWith<IllegalArgumentException> {
            ReleaseOAuthClientIdVerifier.verifyBuildConfigText(
                """public static final String OAUTH2_CLIENT_ID_LIVE = "   ";"""
            )
        }
    }

    @Test
    fun `rejects too-short live client id`() {
        val error = assertFailsWith<IllegalArgumentException> {
            ReleaseOAuthClientIdVerifier.verifyBuildConfigText(
                """public static final String OAUTH2_CLIENT_ID_LIVE = "short";"""
            )
        }
        assertTrue(error.message!!.contains("too short"))
    }

    @Test
    fun `rejects missing live client id field`() {
        assertFailsWith<IllegalStateException> {
            ReleaseOAuthClientIdVerifier.verifyBuildConfigText(
                """public static final String OAUTH2_CLIENT_ID_TEST = "abcdefgh";"""
            )
        }
    }
}
