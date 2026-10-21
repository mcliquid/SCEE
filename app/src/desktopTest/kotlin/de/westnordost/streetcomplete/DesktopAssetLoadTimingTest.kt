package de.westnordost.streetcomplete

import de.westnordost.streetcomplete.util.readBundledLines
import de.westnordost.streetcomplete.util.readBundledText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The quest forms read these assets synchronously on the UI thread when they are first shown (review O3).
 * This measures the real work of that first use; the bound is generous (a frame is ~16 ms, the budget here is
 * what a user would still not notice as a freeze), the measured values are printed for the report.
 */
class DesktopAssetLoadTimingTest {
    @Test fun `first use of the suggestion assets stays well below a noticeable freeze`() {
        val timings = linkedMapOf<String, Double>()
        fun measure(name: String, block: suspend () -> Unit) {
            timings[name] = measureNanoTime { runBlocking { block() } } / 1e6
        }
        // cold: the first read also initializes the resource machinery
        measure("brewery lines") { readBundledLines("brewery/brewerySuggestions.txt") }
        measure("cuisine lines") { readBundledLines("cuisine/cuisineSuggestions.txt") }
        measure("tree lines (4 files)") {
            for (f in listOf("otherDataGenus", "otherDataSpecies", "osmGenus", "osmSpecies")) readBundledLines("tree/$f.txt")
        }
        var keys = ""
        var values = ""
        measure("tag editor read (2 json files)") {
            keys = readBundledText("tag_editor/keySuggestionsForFeature.json")
            values = readBundledText("tag_editor/valueSuggestionsByKey.json")
        }
        measure("tag editor decode") {
            Json.decodeFromString<Map<String, Pair<List<String>?, List<String>?>>>(keys)
            Json.decodeFromString<Map<String, List<String>>>(values)
        }
        measure("tag editor decode again (warm)") {
            Json.decodeFromString<Map<String, Pair<List<String>?, List<String>?>>>(keys)
            Json.decodeFromString<Map<String, List<String>>>(values)
        }
        println("ASSET-TIMING-MS " + timings.entries.joinToString("; ") { "${it.key}=${"%.1f".format(it.value)}" })
        val total = timings.filterKeys { !it.contains("again") }.values.sum()
        assertTrue(total < 2_000, "first use took ${"%.0f".format(total)} ms: $timings")
    }
}
