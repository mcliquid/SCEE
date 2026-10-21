package de.westnordost.streetcomplete.data.osm.mapdata

import kotlin.test.*

class TagsJsonTest {
    @Test fun `empty and JSON null preserve previous representation`() {
        assertEquals("{}", encodeTagsJson(emptyMap()))
        assertEquals(emptyMap(), decodeTagsJson("{}"))
        assertNull(decodeTagsJson("null"))
    }

    @Test fun `historical tags unicode escapes and opaque nested JSON strings roundtrip`() {
        val json = """{"name":"Café 漢字 🌍","note":"a\nb\t\"&<>","unknown:historic":"{\"list\":[null,1]}","empty":""}"""
        val tags = decodeTagsJson(json)!!
        assertEquals("Café 漢字 🌍", tags["name"])
        assertEquals("a\nb\t\"&<>", tags["note"])
        assertEquals("{\"list\":[null,1]}", tags["unknown:historic"])
        assertEquals(tags, decodeTagsJson(encodeTagsJson(tags)))
    }

    @Test fun `tag order and persisted field names are unchanged`() {
        assertEquals("{\"z\":\"last\",\"a\":\"first\"}", encodeTagsJson(linkedMapOf("z" to "last", "a" to "first")))
        assertEquals(listOf("z", "a"), decodeTagsJson("""{"z":"last","a":"first"}""")!!.keys.toList())
    }

    @Test fun `native null map values and numeric coercion retain Moshi behavior`() {
        assertNull((decodeTagsJson("""{"k":null}""") as Map<*, *>)["k"])
        assertEquals(mapOf("k" to "123"), decodeTagsJson("""{"k":123}"""))
    }

    @Test fun `malformed nested arrays objects duplicate fields and non maps are rejected`() {
        for (json in listOf("", "{", "[]", """{"k":[]} """, """{"k":{}}""", """{"k":"a","k":"b"}""")) {
            assertFails("Expected rejection: $json") { decodeTagsJson(json) }
        }
    }

    @Test fun `repeated conversions do not retain previous tags`() {
        repeat(100) {
            val tags = mapOf("sequence" to it.toString())
            assertEquals(tags, decodeTagsJson(encodeTagsJson(tags)))
        }
    }
}
