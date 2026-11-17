import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IdEditorIconCatalogTest {

    private val developLock = """
      {
        "packages": {
          "node_modules/@openstreetmap/id-tagging-schema": { "version": "7.3.0" },
          "node_modules/@mapbox/maki": { "version": "8.2.0" },
          "node_modules/@rapideditor/temaki": { "version": "5.13.0" },
          "node_modules/@waysidemapping/pinhead": { "version": "16.26.0" },
          "node_modules/@enzet/roentgen": { "version": "0.16.0" },
          "node_modules/@fortawesome/free-solid-svg-icons": { "version": "7.2.0" }
        }
      }
      """.trimIndent()

    private val developSha = "e42f8d4fa79e86d42b07a23316a6fcb75d9460a9"

    private fun fakeFetch(developCommitSha: String = developSha): (String) -> String = { url ->
        when {
            url == "https://api.github.com/repos/openstreetmap/iD/commits/develop" ->
                """{"sha":"$developCommitSha"}"""
            url.contains("pinhead@") && url.endsWith("id_upgrades.json") -> "{}"
            url == idEditorPackageLockUrl(developCommitSha) -> developLock
            url.contains("/openstreetmap/iD/") && url.endsWith("package-lock.json") ->
                throw java.io.IOException("no release lock")
            url.startsWith("https://api.github.com/repos/openstreetmap/iD/commits/v") ->
                throw java.io.IOException("no tag")
            else -> error(url)
        }
    }

    @Test
    fun resolvesDevelopTipToCommitShaAndUsesShaInSpriteUrls() {
        val catalog = resolveIdEditorIconCatalog("v7.3.0", fetchText = fakeFetch())
        assertEquals(developSha, catalog.idEditorCommitSha)
        assertEquals("7.3.0", catalog.idTaggingSchemaVersion)
        val urls = iconDownloadUrls("iD-restriction-only-u-turn", catalog)!!
        assertTrue(urls.single().contains("/openstreetmap/iD/$developSha/"))
        assertTrue(!urls.single().contains("/develop/"))
    }

    @Test
    fun failsWhenNoIdLockfileMatchesSchemaVersion() {
        val mismatchLock = developLock.replace("7.3.0", "7.2.0")
        assertFailsWith<IllegalStateException> {
            resolveIdEditorIconCatalog("v7.3.0", fetchText = { url ->
                when {
                    url == "https://api.github.com/repos/openstreetmap/iD/commits/develop" ->
                        """{"sha":"$developSha"}"""
                    url.contains("pinhead@") && url.endsWith("id_upgrades.json") -> "{}"
                    url == idEditorPackageLockUrl(developSha) -> mismatchLock
                    url.contains("/openstreetmap/iD/") && url.endsWith("package-lock.json") ->
                        throw java.io.IOException("skip")
                    else -> error(url)
                }
            })
        }
    }

    @Test
    fun revisionLockFileReusesStoredShaWithoutCallingDevelopApi() {
        val lockDir = Files.createTempDirectory("id-rev-lock").toFile()
        val lockFile = File(lockDir, "id-editor-commit.lock")
        writeIdEditorRevisionRecord(
            lockFile,
            IdEditorRevisionRecord("v7.3.0", developSha, "7.3.0", "recorded test"),
        )
        var developApiCalled = false
        val catalog = resolveIdEditorIconCatalog(
            "v7.3.0",
            fetchText = { url ->
                if (url.contains("/commits/develop")) developApiCalled = true
                fakeFetch()(url)
            },
            revisionLockFile = lockFile,
        )
        assertEquals(developSha, catalog.idEditorCommitSha)
        assertEquals(false, developApiCalled, "develop API should not be needed when lock file is valid")
        lockDir.deleteRecursively()
    }

    @Test
    fun revisionLockFileIgnoredWhenPresetsVersionTagMismatch() {
        val lockDir = Files.createTempDirectory("id-rev-lock-tag").toFile()
        val lockFile = File(lockDir, "id-editor-commit.lock")
        writeIdEditorRevisionRecord(
            lockFile,
            IdEditorRevisionRecord("v7.2.0", developSha, "7.2.0", "stale tag"),
        )
        var developApiCalled = false
        val catalog = resolveIdEditorIconCatalog(
            "v7.3.0",
            fetchText = { url ->
                if (url.contains("/commits/develop")) developApiCalled = true
                fakeFetch()(url)
            },
            revisionLockFile = lockFile,
        )
        assertEquals(developSha, catalog.idEditorCommitSha)
        assertEquals(true, developApiCalled, "stale lock presetsVersionTag must fall through to resolver")
        lockDir.deleteRecursively()
    }

    @Test
    fun resolveGitHubCommitShaParsesApiResponse() {
        val sha = resolveGitHubCommitSha("openstreetmap/iD", "develop") {
            """{"sha":"$developSha","commit":{}}"""
        }
        assertEquals(developSha, sha)
    }
}