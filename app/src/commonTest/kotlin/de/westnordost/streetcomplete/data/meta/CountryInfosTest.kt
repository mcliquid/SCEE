package de.westnordost.streetcomplete.data.meta

import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CountryInfosTest {

    private val files = mapOf(
        "default" to IncompleteCountryInfo(isLeftHandTraffic = false, officialLanguages = listOf("en")),
        "DE" to IncompleteCountryInfo(officialLanguages = listOf("de")),
        "DE-BY" to IncompleteCountryInfo(hasSlowZone = true),
        "GB" to IncompleteCountryInfo(isLeftHandTraffic = true),
    )

    /** loader on [files] that counts how often each region code was loaded and optionally blocks
     *  until [release] is completed */
    private inner class CountingLoader(private val release: CompletableDeferred<Unit>? = null) {
        private val lock = ReentrantLock()
        private val counts = HashMap<String, Int>()

        fun load(regionCode: String): IncompleteCountryInfo? {
            lock.withLock { counts[regionCode] = (counts[regionCode] ?: 0) + 1 }
            if (release != null) runBlocking { release.await() }
            return files[regionCode]
        }

        fun count(regionCode: String): Int = lock.withLock { counts[regionCode] ?: 0 }
    }

    private val regions = listOf(
        listOf("DE"),
        listOf("DE-BY", "DE"),
        listOf("GB"),
        listOf("XX"), // there is no file for this
        listOf("XX-YY", "XX"),
        emptyList(),
    )

    @Test fun uses_hierarchy_and_falls_back_to_default() {
        val infos = CountryInfos(CountingLoader()::load)

        val deBy = infos.get(listOf("DE-BY", "DE"))
        assertEquals("DE", deBy.countryCode)
        assertEquals(listOf("de"), deBy.officialLanguages)
        assertTrue(deBy.hasSlowZone)
        assertFalse(deBy.isLeftHandTraffic)

        assertTrue(infos.get(listOf("GB")).isLeftHandTraffic)
        assertEquals(listOf("en"), infos.get(listOf("GB")).officialLanguages)

        val unknown = infos.get(listOf("XX"))
        assertEquals("XX", unknown.countryCode)
        assertFalse(unknown.isLeftHandTraffic)
        assertEquals(listOf("en"), unknown.officialLanguages)
    }

    @Test fun missing_file_is_cached() {
        val loader = CountingLoader()
        val infos = CountryInfos(loader::load)
        infos.get(listOf("XX"))
        infos.get(listOf("XX"))
        infos.get(listOf("XX-YY", "XX"))
        assertEquals(1, loader.count("XX"))
        assertEquals(1, loader.count("XX-YY"))
    }

    @Test fun same_region_in_parallel_is_loaded_once() {
        val release = CompletableDeferred<Unit>()
        val loader = CountingLoader(release)
        val infos = CountryInfos(loader::load)
        val results = runBlocking {
            val start = CompletableDeferred<Unit>()
            val jobs = (0 until 64).map {
                async(Dispatchers.Default) {
                    start.await()
                    infos.get(listOf("DE-BY", "DE"))
                }
            }
            start.complete(Unit)
            // let all callers request the region while its first load is still blocked
            delay(100)
            release.complete(Unit)
            jobs.awaitAll()
        }
        val expected = CountryInfos(CountingLoader()::load).get(listOf("DE-BY", "DE"))
        assertTrue(results.all { it == expected })
        assertEquals(1, loader.count("DE-BY"))
        assertEquals(1, loader.count("DE"))
        assertEquals(1, loader.count("default"))
    }

    @Test fun different_regions_initialized_in_parallel_give_same_results_as_sequential() {
        val expected = CountryInfos(CountingLoader()::load).let { infos -> regions.associateWith { infos.get(it) } }

        repeat(20) {
            // fresh instance each time, so that all regions are initialized concurrently
            val loader = CountingLoader()
            val infos = CountryInfos(loader::load)
            val results = runBlocking {
                val start = CompletableDeferred<Unit>()
                val jobs = (0 until 300).map { i ->
                    async(Dispatchers.Default) {
                        start.await()
                        val region = regions[i % regions.size]
                        region to infos.get(region)
                    }
                }
                start.complete(Unit)
                jobs.awaitAll()
            }
            for ((region, info) in results) {
                assertEquals(expected.getValue(region), info)
            }
            for (code in listOf("DE", "DE-BY", "GB", "XX", "XX-YY", "default")) {
                assertEquals(1, loader.count(code), "region $code")
            }
        }
    }
}
