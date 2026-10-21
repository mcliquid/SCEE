package de.westnordost.streetcomplete.data.osm.mapdata

import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals

class TagsJsonConcurrencyTest {
    @Test fun `shared Moshi adapter keeps concurrent tag maps independent`() {
        val pool = Executors.newFixedThreadPool(4)
        try {
            val futures = (0..99).map { index ->
                pool.submit<Boolean> {
                    val tags = mapOf("name" to "ä $index", "other" to "[]")
                    tags == decodeTagsJson(encodeTagsJson(tags))
                }
            }
            assertEquals(List(100) { true }, futures.map { it.get() })
        } finally { pool.shutdownNow() }
    }
}
