import com.esotericsoftware.yamlbeans.YamlReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CountryMetadataYamlTest {
    @Test fun `parses valid country metadata shapes`() {
        val parsed = parseCountryMetadataYaml(
            """
            DE: [soccer, tennis]
            default: []
            AT:
              - apple
            hasLanes: true
            mobileCountryCode: 262
            edgeLineStyle: white
            """.trimIndent(),
        )
        assertEquals(listOf("soccer", "tennis"), parsed["DE"])
        assertEquals(emptyList<Any>(), parsed["default"])
        assertEquals(listOf("apple"), parsed["AT"])
        assertEquals(true, parsed["hasLanes"])
        assertEquals(262, parsed["mobileCountryCode"])
        assertEquals("white", parsed["edgeLineStyle"])
    }

    @Test fun `duplicate keys keep last scalar value`() {
        val parsed = parseCountryMetadataYaml(
            """
            LU: true
            LT: true
            LU: false
            """.trimIndent(),
        )
        assertEquals(false, parsed["LU"])
        assertEquals(true, parsed["LT"])
    }

    @Test fun `duplicate keys with identical values`() {
        val parsed = parseCountryMetadataYaml(
            """
            LU: true
            LT: true
            LU: true
            """.trimIndent(),
        )
        assertEquals(true, parsed["LU"])
    }

    @Test fun `parses frozen duplicate LU fixture`() {
        val text = CountryMetadataTestFixtures.readProperty("hasAdvisorySpeedLimitSign.yml")
        val parsed = parseCountryMetadataYaml(text)
        assertEquals(true, parsed["LU"])
        assertEquals(true, parsed["LT"])
    }

    @Test fun `matches yamlbeans for frozen duplicate LU fixture`() {
        val text = CountryMetadataTestFixtures.readProperty("hasAdvisorySpeedLimitSign.yml")
        val old = YamlReader(text).read() as Map<String, Any>
        val neu = parseCountryMetadataYaml(text)
        assertMapsContentEqual(normalizeMetadataMap(old), normalizeMetadataMap(neu))
    }

    @Test fun `matches yamlbeans for block lists with inline comments`() {
        val text = CountryMetadataTestFixtures.readProperty("atmOperators.yml")
        val old = YamlReader(text).read() as Map<String, Any>
        val neu = parseCountryMetadataYaml(text)
        assertMapsContentEqual(normalizeMetadataMap(old), normalizeMetadataMap(neu))
    }

    @Test fun `rejects java-bean style type tags`() {
        assertFailsWith<Exception> { parseCountryMetadataYaml("x: !!javax.script.ScriptEngineManager {}") }
    }

    @Test fun `rejects nested mappings as values`() {
        assertFailsWith<IllegalArgumentException> {
            parseCountryMetadataYaml("DE:\n  nested:\n    key: value")
        }
    }

    @Test fun `rejects non-mapping root`() {
        assertFailsWith<IllegalArgumentException> { parseCountryMetadataYaml("[a, b]") }
    }

    @Test fun `rejects recursive alias references`() {
        assertFailsWith<Throwable> { parseCountryMetadataYaml("a: &a [*a]") }
    }

    @Test fun `keeps Norway country code as string key`() {
        val parsed = parseCountryMetadataYaml(CountryMetadataTestFixtures.readProperty("domesticSocketTypes.yml"))
        assertTrue(parsed.containsKey("NO"))
        assertEquals(listOf("typef", "typec"), parsed["NO"])
    }

    @Test fun `parses frozen orchardProduces fixture`() {
        val parsed = parseCountryMetadataYaml(CountryMetadataTestFixtures.readProperty("orchardProduces.yml"))
        assertTrue(parsed.containsKey("DE"))
        assertTrue(parsed["DE"] is List<*>)
    }
}

internal fun normalizeMetadataMap(map: Map<String, Any>): Map<String, Any> =
    map.entries
        .sortedBy { it.key }
        .associate { (key, value) -> key to (normalizeMetadataValue(value) as Any) }

private fun normalizeMetadataValue(value: Any?): Any? = when (value) {
    null -> emptyList<Any>()
    is Boolean -> value
    is String -> when {
        value.equals("true", ignoreCase = true) -> true
        value.equals("false", ignoreCase = true) -> false
        else -> value.toLongOrNull()?.let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it } ?: value
    }
    is Number -> {
        val d = value.toDouble()
        when {
            d == d.toLong().toDouble() && d in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() -> d.toInt()
            d == d.toLong().toDouble() -> d.toLong()
            else -> d
        }
    }
    is List<*> -> value.map { normalizeMetadataValue(it) }
    is Map<*, *> -> throw IllegalArgumentException("unexpected nested map in metadata compare")
    else -> throw IllegalArgumentException("unsupported type ${value::class.java.name}")
}

internal fun assertMapsContentEqual(expected: Map<String, Any>, actual: Map<String, Any>, property: String? = null) {
    assertEquals(expected.keys, actual.keys, property?.let { "Key mismatch in $it" })
    for (key in expected.keys) {
        assertEquals(
            expected[key],
            actual[key],
            property?.let { "Mismatch in $it at $key" } ?: "Mismatch at key $key",
        )
    }
}