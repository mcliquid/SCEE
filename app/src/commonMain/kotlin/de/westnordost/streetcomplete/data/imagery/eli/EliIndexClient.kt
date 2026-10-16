package de.westnordost.streetcomplete.data.imagery.eli

import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.util.Mockable
import de.westnordost.streetcomplete.util.ktx.nowAsEpochMilliseconds
import de.westnordost.streetcomplete.util.logs.Log
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readString
import kotlinx.io.writeString

@Mockable
class EliIndexClient(
    private val httpClient: HttpClient,
    private val prefs: Preferences,
    private val fileSystem: FileSystem,
    private val cacheDir: Path,
) {
    private val mutex = Mutex()
    private var cachedIndex: EliIndex? = null

    suspend fun getIndex(forceRefresh: Boolean = false): EliIndex = mutex.withLock {
        cachedIndex?.takeIf { !forceRefresh }?.let { return it }
        val fromDisk = withContext(Dispatchers.IO) { loadFromDiskIfFresh(forceRefresh) }
        if (fromDisk != null) {
            cachedIndex = fromDisk
            return fromDisk
        }
        val downloaded = downloadAndStore()
        if (downloaded != null) {
            cachedIndex = downloaded
            return downloaded
        }
        val stale = withContext(Dispatchers.IO) { loadFromDiskIgnoringAge() }
        if (stale != null) {
            cachedIndex = stale
            return stale
        }
        return EliIndex(emptyList())
    }

    private suspend fun downloadAndStore(): EliIndex? = withContext(Dispatchers.IO) {
        val url = prefs.getString(Prefs.ELI_INDEX_URL, ApplicationConstants.ELI_DEFAULT_INDEX_URL)
        try {
            val response = httpClient.get(url) { expectSuccess = true }
            val text = response.bodyAsText()
            val layers = EliIndexParser.parse(text)
            ensureCacheDir()
            fileSystem.sink(cacheFile()).buffered().use { it.writeString(text) }
            prefs.putLong(Prefs.ELI_INDEX_CACHED_AT, nowAsEpochMilliseconds())
            Log.i(TAG, "Downloaded ELI index (${layers.size} features)")
            EliIndex(layers)
        } catch (e: Throwable) {
            // Includes ExceptionInInitializerError / linkage Errors from URL helpers.
            Log.w(TAG, "Failed to download ELI index: ${e.message}")
            null
        }
    }

    private fun loadFromDiskIfFresh(forceRefresh: Boolean): EliIndex? {
        if (forceRefresh) return null
        val cachedAt = prefs.getLong(Prefs.ELI_INDEX_CACHED_AT, 0L)
        if (cachedAt == 0L) return null
        if (nowAsEpochMilliseconds() - cachedAt > CACHE_TTL_MS) return null
        return loadFromDiskIgnoringAge()
    }

    private fun loadFromDiskIgnoringAge(): EliIndex? {
        val file = cacheFile()
        if (!fileSystem.exists(file)) return null
        return try {
            val text = fileSystem.source(file).buffered().use { it.readString() }
            EliIndex(EliIndexParser.parse(text))
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to read cached ELI index: ${e.message}")
            null
        }
    }

    private fun ensureCacheDir() {
        try {
            fileSystem.createDirectories(cacheDir, mustCreate = false)
        } catch (_: Exception) { /* ignore */ }
    }

    private fun cacheFile(): Path = Path(cacheDir, CACHE_FILE_NAME)

    companion object {
        private const val TAG = "EliIndexClient"
        const val CACHE_FILE_NAME = "imagery.geojson"
        const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000
    }
}
