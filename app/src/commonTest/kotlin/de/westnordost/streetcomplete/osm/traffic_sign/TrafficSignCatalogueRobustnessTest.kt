package de.westnordost.streetcomplete.osm.traffic_sign

import de.westnordost.streetcomplete.data.traffic_signs.TrafficSignCatalogues
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignTestData.RESOURCES_DIR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** F8: a broken entry of the catalogue must not make the whole catalogue unusable at run time, and
 *  the data that is shipped must be checked completely */
class TrafficSignCatalogueRobustnessTest {

    private fun sign(
        osmValuePart: String? = "1", signId: String? = "1", name: String? = "Zeichen 1", kind: String? = "traffic_sign",
        category: String? = "traffic_sign", tagRecommendations: String? = "\"none\"", redirects: String = "[]",
    ) = buildString {
        append("{")
        val fields = listOfNotNull(
            osmValuePart?.let { "\"osmValuePart\": ${quote(it)}" },
            signId?.let { "\"signId\": ${quote(it)}" },
            name?.let { "\"name\": ${quote(it)}" },
            kind?.let { "\"kind\": ${quote(it)}" },
            category?.let { "\"catalogue\": {\"signCategory\": ${quote(it)}}" },
            tagRecommendations?.let { "\"tagRecommendationsByGeometry\": $it" },
            "\"redirects\": $redirects",
        )
        append(fields.joinToString(", "))
        append("}")
    }

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun catalogue(vararg signs: String, relations: String = "[]") =
        "{\"country\": \"DE\", \"version\": \"0.0.0\", \"signs\": [${signs.joinToString(",")}], \"relations\": $relations}"

    private val good1 = sign("1", "1")
    private val good3 = sign("3", "3", kind = "exception_modifier")

    /* ------------------------------------- run time: one broken entry ------------------------------------- */

    private val brokenEntries = mapOf(
        "missing osmValuePart" to sign(osmValuePart = null),
        "missing signId" to sign(signId = null),
        "missing name" to sign(name = null),
        "missing kind" to sign(kind = null),
        "unknown kind" to sign(kind = "something_new"),
        "missing catalogue" to sign(category = null),
        "missing tag recommendations" to sign(tagRecommendations = null),
        "tag recommendations of wrong structure" to sign(tagRecommendations = "\"something\""),
        "tag recommendations of wrong type" to sign(tagRecommendations = "42"),
        "empty osmValuePart" to sign(osmValuePart = ""),
        "empty signId" to sign(signId = ""),
        "entry is a number" to "42",
        "entry is null" to "null",
        "entry is a string" to "\"1\"",
        "name has the wrong type" to "{\"osmValuePart\": \"2\", \"signId\": \"2\", \"name\": [1], \"kind\": \"traffic_sign\", \"catalogue\": {\"signCategory\": \"x\"}, \"tagRecommendationsByGeometry\": \"none\"}",
    )

    @Test fun `a broken entry between valid entries is left out, the others stay usable`() {
        for ((what, broken) in brokenEntries) {
            val json = catalogue(good1, broken, good3)
            val result = TrafficSignCatalogue.parseLenient(json)
            val catalogue = assertNotNull(result.catalogue, what)
            assertEquals(listOf("1", "3"), catalogue.signs.map { it.osmValuePart }, what)
            assertNotNull(catalogue.resolve("1"), what)
            assertNotNull(catalogue.resolve("3"), what)
            assertEquals(1, result.problems.count { it.index == 1 }, "$what: ${result.problems}")
            assertTrue(result.problems.all { it.isError }, what)
        }
    }

    @Test fun `the problem names the position, the id and the cause`() {
        val result = TrafficSignCatalogue.parseLenient(catalogue(good1, sign(osmValuePart = "274-30", kind = "something_new")))
        val problem = result.problems.single()
        assertEquals(1, problem.index)
        assertEquals("274-30", problem.id)
        assertTrue("something_new" in problem.message, problem.message)
        assertTrue("#1 274-30" in problem.toString(), problem.toString())
    }

    @Test fun `strict parsing names every problem`() {
        val e = assertFailsWith<IllegalArgumentException> { TrafficSignCatalogue.parse(catalogue(good1, sign(kind = "x", osmValuePart = "7"), sign(name = null, osmValuePart = "8"))) }
        assertTrue("2 problems" in e.message!!, e.message)
        assertTrue("#1 7" in e.message!! && "#2 8" in e.message!!, e.message)
    }

    @Test fun `a broken relation is left out`() {
        val json = catalogue(good1, relations = "[{\"kind\": \"a\", \"topic\": \"b\", \"start\": \"1\"}, {\"nothing\": 1}, 5]")
        val result = TrafficSignCatalogue.parseLenient(json)
        assertEquals(1, result.catalogue!!.relations.size)
        assertEquals(2, result.problems.size)
    }

    @Test fun `duplicate ids keep the first entry and are reported`() {
        val json = catalogue(sign("1", "1", name = "first"), sign("1", "1", name = "second"))
        val result = TrafficSignCatalogue.parseLenient(json)
        assertEquals(listOf("first"), result.catalogue!!.signs.map { it.name })
        assertEquals("duplicate osmValuePart", result.problems.single().message)
        assertTrue(TrafficSignCatalogue.validate(json).isNotEmpty())
        assertFailsWith<IllegalArgumentException> { TrafficSignCatalogue.parse(json) }
    }

    /* ----------------------------------- run time: nothing usable ----------------------------------- */

    @Test fun `a catalogue that is broken as a whole is not usable and does not throw`() {
        for (json in listOf(
            "", "not json", "[]", "42", "null", "{}", "{\"country\": \"DE\"}", "{\"country\": \"DE\", \"version\": \"1\"}",
            "{\"country\": \"DE\", \"version\": \"1\", \"signs\": []}", "{\"country\": \"DE\", \"version\": \"1\", \"signs\": {}}",
            "{\"country\": 1, \"version\": \"1\", \"signs\": []}", "{\"version\": \"1\", \"signs\": [${good1}]}",
            catalogue(sign(kind = "x"), sign(name = null, osmValuePart = "2")),
            "{\"country\": \"DE\", \"version\": \"1\", \"signs\": [", "\u0000",
        )) {
            val result = TrafficSignCatalogue.parseLenient(json)
            assertNull(result.catalogue, json)
            assertTrue(result.problems.isNotEmpty(), json)
            assertFailsWith<IllegalArgumentException> { TrafficSignCatalogue.parse(json) }
        }
    }

    @Test fun `the catalogue provider uses the valid entries and never throws`() {
        val catalogues = TrafficSignCatalogues { country -> if (country == "DE") catalogue(good1, brokenEntries.getValue("unknown kind"), good3) else null }
        val catalogue = assertNotNull(catalogues.get("DE"))
        assertEquals(2, catalogue.signs.size)
        assertNull(catalogues.get("AT"))
        // a catalogue that is unusable as a whole, a loader that fails, binary garbage
        assertNull(TrafficSignCatalogues { "garbage" }.get("DE"))
        assertNull(TrafficSignCatalogues { error("no resource") }.get("DE"))
        assertNull(TrafficSignCatalogues { catalogue(sign(kind = "x")) }.get("DE"))
    }

    @Test fun `redirects to nothing do not break the lookup`() {
        val json = catalogue(sign("1", "1", redirects = "[{\"from\": \"one\", \"to\": \"999\"}]"), good3)
        val catalogue = TrafficSignCatalogue.parseLenient(json).catalogue!!
        assertNull(catalogue.resolve("one"))
        assertNotNull(catalogue.resolve("1"))
        val value = parseTrafficSignValue("DE:1,one", catalogue)
        assertEquals("DE:1,one", value.toOsmValue())
    }

    /* ------------------------------------- validation of the data ------------------------------------- */

    @Test fun `validation finds redirects to nothing and contradicting redirects`() {
        val missing = catalogue(sign("1", "1", redirects = "[{\"from\": \"one\", \"to\": \"999\"}]"))
        assertTrue(TrafficSignCatalogue.validate(missing).any { "999" in it.message })
        // a redirect to a sign id or to a named value is fine
        val fine = catalogue(sign("1", "1", redirects = "[{\"from\": \"one\", \"to\": \"1\"}, {\"from\": \"no\", \"to\": \"none\"}]"))
        assertEquals(emptyList(), TrafficSignCatalogue.validate(fine))
        val contradicting = catalogue(
            sign("1", "1", redirects = "[{\"from\": \"x\", \"to\": \"1\"}]"),
            sign("3", "3", redirects = "[{\"from\": \"X\", \"to\": \"3\"}]"),
        )
        assertTrue(TrafficSignCatalogue.validate(contradicting).any { "leads to" in it.message && "and to" in it.message })
    }

    @Test fun `validation finds signs that would share an image`() {
        // 274-30 and 274_30 both become DE_274_30
        val json = catalogue(sign("274-30", "274-30"), sign("274_30", "274_30"))
        val problems = TrafficSignCatalogue.validate(json)
        assertEquals(1, problems.size)
        assertTrue("DE_274_30" in problems.single().message && "274-30" in problems.single().message && "274_30" in problems.single().message, problems.toString())
        // "274[30]" and "274__30__" collide too
        assertEquals(1, TrafficSignCatalogue.validate(catalogue(sign("274[30]", "274"), sign("274__30__", "274__30__"))).size)
        // the lenient parser keeps both: it is not a reason to drop a sign
        assertEquals(2, TrafficSignCatalogue.parseLenient(json).catalogue!!.signs.size)
    }

    @Test fun `validation reports missing images`() {
        val json = catalogue(good1, good3)
        assertEquals(emptyList(), TrafficSignCatalogue.validate(json) { true })
        val problems = TrafficSignCatalogue.validate(json) { it.osmValuePart != "3" }
        assertEquals("no image", problems.single().message)
        assertEquals("3", problems.single().id)
        assertFalse(problems.single().isError)
    }

    @Test fun `validation reports entry problems too`() {
        val problems = TrafficSignCatalogue.validate(catalogue(good1, sign(kind = "x", osmValuePart = "9")))
        assertEquals(1, problems.size)
        assertEquals("9", problems.single().id)
    }

    /* ------------------------------------------- the real data ------------------------------------------- */

    private val json = TrafficSignTestData.readText("$RESOURCES_DIR/files/traffic_signs/DE.json")

    @Test fun `all 290 signs of the bundled catalogue are valid, have an image and no problems at all`() {
        val problems = TrafficSignCatalogue.validate(json) { sign ->
            TrafficSignTestData.readBytesOrNull("$RESOURCES_DIR/${sign.imagePath("DE")}") != null
        }
        assertEquals(emptyList(), problems)
        val result = TrafficSignCatalogue.parseLenient(json)
        assertEquals(emptyList(), result.problems)
        assertEquals(290, result.catalogue!!.signs.size)
    }

    @Test fun `the bundled catalogue is read the same by the lenient and the strict parser`() {
        val lenient = TrafficSignCatalogue.parseLenient(json).catalogue!!
        val strict = TrafficSignCatalogue.parse(json)
        assertEquals(strict.signs, lenient.signs)
        assertEquals(strict.relations, lenient.relations)
        assertEquals(strict.version, lenient.version)
        assertEquals(290, strict.signs.size)
        assertEquals(45, strict.relations.size)
        // the provider gives the same
        val provided = TrafficSignCatalogues { json }.get("DE")!!
        assertEquals(strict.signs, provided.signs)
    }

    @Test fun `a broken sign in the real catalogue only takes that sign away`() {
        // replace the kind of one of the 290 signs
        val broken = json.replaceFirst("\"kind\": \"exception_modifier\"", "\"kind\": \"unknown_kind\"")
        assertTrue(broken != json)
        val result = TrafficSignCatalogue.parseLenient(broken)
        assertEquals(289, result.catalogue!!.signs.size)
        assertEquals(1, result.problems.size)
        assertTrue("unknown_kind" in result.problems.single().message)
        assertFailsWith<IllegalArgumentException> { TrafficSignCatalogue.parse(broken) }
        // values of the other signs are read as before
        assertEquals("DE:250,1020-30", parseTrafficSignValue("DE:250,1020-30", result.catalogue!!).toOsmValue())
    }
}
