package de.westnordost.streetcomplete.osm.traffic_sign

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.io.buffered
import kotlinx.io.writeString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Compares the parsing and formatting of traffic_sign values of SCEE with the reference
 *  implementation, @osm-traffic-signs/converter (version and hash: see header of reference.jsonl).
 *
 *  The expected values come from the converter only (tools/traffic-sign-parity/reference.mjs ran it
 *  and wrote tools/traffic-sign-parity/reference.jsonl), not from SCEE. To run it again:
 *
 *      cd tools/traffic-sign-parity && npm ci && node reference.mjs --check
 *      ./gradlew :app:testAndroidHostTest --tests "*TrafficSignConverterParityTest"
 *
 *  Each case ends up as A (identical), B (deliberate, documented difference, [DEVIATIONS]) or C
 *  (unexplained). The test fails if there is any C. A summary is written to
 *  app/build/traffic-sign-parity/parity-results.json. */
class TrafficSignConverterParityTest {

    private val catalogue = TrafficSignTestData.catalogueDE
    private val prettyJson = Json { prettyPrint = true }

    /** the result of one side: unparseable values are given back unchanged by SCEE */
    private data class Result(val unparseable: Boolean, val parts: List<Part>, val value: String)
    private data class Part(val text: String, val kind: String, val recognized: Boolean)

    private class Case(val category: String, val input: String, val expected: Result)

    private fun scee(input: String): Result {
        val v = parseTrafficSignValue(input, catalogue)
        if (v.isUnparseable) return Result(true, emptyList(), v.toOsmValue())
        return Result(
            false,
            v.parts.map { Part(it.text, it.kind.name.lowercase(), it.isRecognized) },
            v.toOsmValue(),
        )
    }

    private fun readReference(): Pair<JsonObject, List<Case>> {
        val lines = TrafficSignTestData.readText("../tools/traffic-sign-parity/reference.jsonl").lines().filter { it.isNotBlank() }
        val header = Json.parseToJsonElement(lines.first()).jsonObject
        val cases = lines.drop(1).map { line ->
            val o = Json.parseToJsonElement(line).jsonObject
            Case(
                o["c"]!!.jsonPrimitive.content,
                o["i"]!!.jsonPrimitive.content,
                Result(
                    false,
                    o["p"]!!.jsonArray.map { p -> Part(p.jsonArray[0].jsonPrimitive.content, p.jsonArray[1].jsonPrimitive.content, p.jsonArray[2].jsonPrimitive.int == 1) },
                    o["o"]!!.jsonPrimitive.content,
                ),
            )
        }
        return header to cases
    }

    /** the main sign x additional sign product, like reference.mjs generates it */
    private fun productPairs(): List<Pair<TrafficSign, TrafficSign>> {
        val mains = catalogue.signs.filter { it.kind == TrafficSignKind.TRAFFIC_SIGN }
        val modifiers = catalogue.signs.filter { it.kind != TrafficSignKind.TRAFFIC_SIGN }
        return mains.filter { it.canReceiveModifiers != false }.flatMap { m ->
            modifiers.filter { mod -> mod.osmValuePart !in m.incompatibleModifiers && mod.signId !in m.incompatibleModifiers }.map { m to it }
        }
    }

    private class Deviation(val id: String, val reason: String)

    private val DEVIATIONS = listOf(
        Deviation("NO_COUNTRY_PREFIX", "A sign without country prefix (e.g. 250) is not interpreted as a sign of Germany, the value is kept as it is. The converter assumes the country."),
        Deviation("TIME_RESTRICTION_NORMALIZED", "The converter rewrites time restrictions (opening_hours syntax, e.g. 16:00-18:00 -> 16-18) and with that existing values. SCEE keeps the value as written."),
        Deviation("MISSING_VALUE_KEPT", "A sign with variable value but no value (274) stays as it is. The converter adds the default value (274[47]), which was never tagged."),
        Deviation("KEY_ONLY_AT_START", "A key like traffic_sign= is only removed at the very start. The converter cuts at the first = anywhere if traffic_sign stands somewhere before it (free text \"traffic_sign=x\" loses its beginning)."),
        Deviation("COUNTRY_PREFIX_ONLY_PER_PART", "The country prefix is only removed in front of a part. The converter removes DE: anywhere, also inside quoted free text."),
        Deviation("UNREADABLE_KEPT", "Values that are not clearly readable (other country, unbalanced brackets or quotes, empty, only a prefix) are kept unchanged and not interpreted. The converter splits them heuristically."),
        Deviation("NAMED_VALUE_IS_A_SIGN", "Named values (city_limit, none, yes, ...) are always independent signs (separator ;). The converter does not recognize them as signs and guesses a kind, which can keep a , in front of them."),
        Deviation("CASE_INSENSITIVE_PREFIX", "A country prefix in any case (De:250) is read as DE. The converter only knows DE: and de: and makes an unknown fragment out of it."),
        Deviation("MALFORMED_BRACKET_VALUE_KEPT", "A bracket value that is not a plain value (274[[30]], 274[], 274[30]x) is kept as written. The converter drops it, uses the default value or changes the text (274[30x])."),
        Deviation("DOUBLE_PREFIX_KEPT", "A doubled country prefix (DE:DE:250) is not read as a sign and kept as written. The converter removes every DE:."),
        Deviation("UNKNOWN_SIGN_KIND", "Kind of an unknown fragment is given by its separator (, = additional sign, ; = sign), like it was written. The converter guesses from the whole text."),
    )

    @Test fun `SCEE and the converter agree on all cases or differ with documented reason`() {
        val (header, cases) = readReference()
        assertEquals("@osm-traffic-signs/converter@0.8.0", header["converter"]!!.jsonPrimitive.content)
        val versionFile = TrafficSignTestData.readText("src/commonMain/composeResources/files/traffic_signs/VERSION")
        assertTrue(versionFile.startsWith(header["converter"]!!.jsonPrimitive.content + "\n"))

        val identical = ArrayList<Case>()
        val deviating = ArrayList<Pair<Case, String>>()
        val unexplained = ArrayList<Triple<Case, Result, String>>()
        fun classify(case: Case) {
            val actual = scee(case.input)
            if (!actual.unparseable && actual == case.expected) {
                identical.add(case)
                return
            }
            val rule = explain(case, actual)
            if (rule != null) deviating.add(case to rule) else unexplained.add(Triple(case, actual, "differs"))
        }
        cases.forEach(::classify)

        // the product of main signs and additional signs: the converter leaves such values as they are
        val explicit = cases.filter { it.category == "main_with_modifier" }.map { it.input }.toSet()
        val pairs = productPairs()
        assertEquals(header["mainWithModifierProduct"]!!.jsonPrimitive.int, pairs.size, "same product as in reference.mjs")
        var productCount = 0
        for ((main, mod) in pairs) {
            val input = "DE:${main.osmValuePart},${mod.osmValuePart}"
            productCount++
            if (input in explicit) continue
            classify(Case("main_with_modifier", input, Result(false, listOf(Part(main.osmValuePart, main.kind.name.lowercase(), true), Part(mod.osmValuePart, mod.kind.name.lowercase(), true)), input)))
        }
        val total = cases.size - explicit.size + productCount
        assertEquals(header["count"]!!.jsonPrimitive.int, total, "number of cases")

        writeResults(header, total, identical, deviating, unexplained)

        assertTrue(
            unexplained.isEmpty(),
            "${unexplained.size} of $total cases differ without documented reason:\n" +
                unexplained.take(40).joinToString("\n") { (c, a, _) -> "[${c.category}] ${c.input}\n    converter: ${c.expected}\n    scee:      $a" }
        )
    }

    /** the id of the deviation that explains the difference, or null. Each rule checks more than
     *  just "something differs", so that a real bug does not hide behind it. */
    private fun explain(case: Case, actual: Result): String? {
        val input = case.input
        val expected = case.expected

        // value without country prefix
        if (actual.unparseable && !hasAnyCountryPrefix(input) && actual.value == input) return "NO_COUNTRY_PREFIX"

        // time restrictions: the converter keeps the parts as written, but rewrites the content of the
        // brackets of time restriction signs when it writes the value
        if (!actual.unparseable && actual.parts == expected.parts && actual.value != expected.value &&
            maskTimeRestrictions(actual.value) == maskTimeRestrictions(expected.value)
        ) return "TIME_RESTRICTION_NORMALIZED"

        // named values (city_limit, none, ...): not catalogue signs for the converter, which guesses their kind
        // from the text; for SCEE always an independent sign, so the separator in front of them is ";"
        if (!actual.unparseable && actual.parts.size == expected.parts.size &&
            actual.parts.zip(expected.parts).all { (a, e) -> a == e || (a.text in NAMED_TRAFFIC_SIGN_VALUES && a.text == e.text && a.kind == "traffic_sign") } &&
            actual.value == semicolonBeforeNamedValues(expected.value)
        ) return "NAMED_VALUE_IS_A_SIGN"

        // values that are not clearly readable are kept unchanged: unbalanced brackets or quotes, nothing
        // after the prefix, signs of another country
        if (actual.unparseable && actual.value == input && isUnreadableSyntax(input)) return "UNREADABLE_KEPT"

        // a key is only removed at the start: the converter cuts at the first = if traffic_sign is before it
        if (!actual.unparseable && converterRemoveKeys(input) != input && !LEADING_KEY.containsMatchIn(input.trim()) &&
            "traffic_sign=" in actual.value && actual.value == input
        ) return "KEY_ONLY_AT_START"

        // a country prefix is only removed in front of a part: SCEE keeps de: inside of quotation marks
        val quoted = Regex("\"[^\"]*\"").findAll(input).map { it.value }.filter { it.contains("de:", ignoreCase = true) }.toList()
        if (!actual.unparseable && quoted.isNotEmpty() && quoted.all { it in actual.value }) return "COUNTRY_PREFIX_ONLY_PER_PART"

        // a country prefix in a different case
        if (!actual.unparseable && Regex("(^|[,;])\\s*(De|dE):").containsMatchIn(input) && actual.parts.all { it.recognized }) return "CASE_INSENSITIVE_PREFIX"

        // a bracket value that is not a plain value is kept as written
        if (!actual.unparseable && Regex("""\[\[|\[\]|\][^,;\]\s]""").containsMatchIn(input) &&
            actual.value == input.replaceFirst(Regex("^(?i)de:"), "DE:")
        ) return "MALFORMED_BRACKET_VALUE_KEPT"

        // a doubled country prefix is not removed (it is kept as it was written)
        if (!actual.unparseable && Regex("""^(?i)de:\s*de:""").containsMatchIn(input.trim()) && actual.value == input.trim().replaceFirst(Regex("""^(?i)de:\s*"""), "DE:")) return "DOUBLE_PREFIX_KEPT"

        // the kind of an unknown fragment: by its separator, not guessed
        if (!actual.unparseable && actual.value == expected.value && actual.parts.size == expected.parts.size &&
            actual.parts.zip(expected.parts).all { (a, e) -> a == e || (!a.recognized && a.text == e.text && !e.recognized) } &&
            actual.parts != expected.parts
        ) return "UNKNOWN_SIGN_KIND"

        // sign with variable value, but no value given: SCEE keeps it, the converter adds the default value
        if (!actual.unparseable && actual.parts.size == expected.parts.size &&
            actual.parts.zip(expected.parts).all { (a, e) -> a == e || isMissingValueRewrite(a, e) } &&
            actual.parts.zip(expected.parts).any { (a, e) -> a != e }
        ) return "MISSING_VALUE_KEPT"

        return null
    }

    private fun semicolonBeforeNamedValues(value: String) =
        Regex(",(" + NAMED_TRAFFIC_SIGN_VALUES.joinToString("|") + ")(?=[,;]|$)").replace(value) { ";" + it.groupValues[1] }

    /** the key removal of the converter (removeKeys): everything up to the first = if traffic_sign is in it */
    private fun converterRemoveKeys(input: String): String {
        val split = input.split('=')
        return if ("traffic_sign" in split[0]) split.drop(1).joinToString("=") else input
    }

    private val LEADING_KEY = Regex("^(?:[a-z_]+:)*traffic_sign(?::[a-z_]+)*\\s*=")

    private fun isUnreadableSyntax(input: String): Boolean {
        var depth = 0
        var inQuotes = false
        for (c in input) {
            when {
                c == '"' -> inQuotes = !inQuotes
                inQuotes -> {}
                c == '[' -> depth++
                c == ']' -> { depth--; if (depth < 0) return true }
            }
        }
        if (depth != 0 || inQuotes) return true
        // nothing after the prefix, or a prefix of another country
        val parts = input.split(Regex("[,;]"))
        return parts.any { Regex("^\\s*[A-Za-z]{2}:\\s*$").matches(it) } ||
            Regex("(^|[,;])\\s*(?!(?i:de):)[A-Za-z]{2}:").containsMatchIn(input)
    }

    private fun hasAnyCountryPrefix(input: String) = Regex("(^|[,;])\\s*[A-Za-z]{2}:").containsMatchIn(input)

    /** signs whose variable value is a time restriction: the converter normalizes it */
    private val timeRestrictionIds = catalogue.signs
        .filter { it.valuePrompt?.format in setOf("opening_hours", "time_restriction") }.map { it.signId }.toSet()

    private fun maskTimeRestrictions(value: String): String =
        timeRestrictionIds.fold(value) { v, id -> v.replace(Regex(Regex.escape(id) + "\\[[^\\]]*\\]"), id + "[...]") }

    private fun isMissingValueRewrite(actual: Part, expected: Part): Boolean {
        if (actual.kind != expected.kind || actual.recognized != expected.recognized || '[' in actual.text) return false
        val sign = catalogue.getBySignId(actual.text).firstOrNull { it.hasVariableValue } ?: return false
        return expected.text == "${actual.text}[${sign.defaultValue}]"
    }

    private fun writeResults(
        header: JsonObject, total: Int,
        identical: List<Case>, deviating: List<Pair<Case, String>>, unexplained: List<Triple<Case, Result, String>>,
    ) {
        val byCategory = (identical.map { it.category to "A" } + deviating.map { it.first.category to "B" } + unexplained.map { it.first.category to "C" })
            .groupBy { it.first }.toSortedMap()
        val json = buildJsonObject {
            put("converter", header["converter"]!!.jsonPrimitive.content)
            put("catalogueSha256", header["catalogueSha256"]!!.jsonPrimitive.content)
            put("cases", total)
            put("identical", identical.size)
            put("deliberateDifferences", deviating.size)
            put("unexplained", unexplained.size)
            put("categories", buildJsonObject {
                for ((category, results) in byCategory) put(category, buildJsonObject {
                    put("cases", results.size)
                    put("A", results.count { it.second == "A" })
                    put("B", results.count { it.second == "B" })
                    put("C", results.count { it.second == "C" })
                })
            })
            put("deviations", buildJsonArray {
                for (d in DEVIATIONS) add(buildJsonObject {
                    put("id", d.id)
                    put("reason", d.reason)
                    put("cases", deviating.count { it.second == d.id })
                    put("examples", buildJsonArray { deviating.filter { it.second == d.id }.take(5).forEach { add(JsonPrimitive(it.first.input)) } })
                })
            })
            put("unexplainedCases", buildJsonArray {
                for ((c, a, _) in unexplained) add(buildJsonObject {
                    put("category", c.category)
                    put("input", c.input)
                    put("converter", "${c.expected.parts.map { it.text }} -> ${c.expected.value}")
                    put("scee", if (a.unparseable) "unreadable: ${a.value}" else "${a.parts.map { it.text }} -> ${a.value}")
                })
            })
        }
        val dir = Path("build/traffic-sign-parity")
        SystemFileSystem.createDirectories(dir)
        SystemFileSystem.sink(Path(dir, "parity-results.json")).buffered().use {
            it.writeString(prettyJson.encodeToString(JsonObject.serializer(), json))
        }
        println("PARITY: $total cases, ${identical.size} identical (A), ${deviating.size} deliberate differences (B), ${unexplained.size} unexplained (C)")
    }
}
