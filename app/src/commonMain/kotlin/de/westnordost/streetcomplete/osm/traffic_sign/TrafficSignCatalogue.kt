package de.westnordost.streetcomplete.osm.traffic_sign

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement

/** The traffic signs of one country, with lookups by the parts of a traffic_sign value.
 *
 *  Lookup and redirect resolution follow trafficSignTagToSigns of @osm-traffic-signs/converter,
 *  see https://github.com/osmberlin/osm-traffic-sign-tool/tree/main/packages/traffic-sign-converter */
class TrafficSignCatalogue(
    /** e.g. "DE" */
    val country: String,
    /** version of the catalogue, e.g. "0.8.0" */
    val version: String,
    val signs: List<TrafficSign>,
    val relations: List<TrafficSignRelation>,
) {
    private val byOsmValuePart: Map<String, TrafficSign> = signs.associateBy { it.osmValuePart }
    private val bySignId: Map<String, List<TrafficSign>> = signs.groupBy { it.signId }
    /** lowercase redirect source -> redirect target */
    private val redirects: Map<String, String> = buildMap {
        for (sign in signs) {
            for (redirect in sign.redirects) put(redirect.from.lowercase(), redirect.to)
        }
        for ((from, to) in GENERAL_REDIRECTS) put(from.lowercase(), to)
    }
    private val dashValueSignIds: Set<String> =
        signs.filter { it.redirectDashValues }.mapTo(HashSet()) { it.signId }

    /** the sign with exactly the given osm value part, e.g. "274-30", without resolving redirects */
    fun getByOsmValuePart(osmValuePart: String): TrafficSign? = byOsmValuePart[osmValuePart]

    fun getBySignId(signId: String): List<TrafficSign> = bySignId[signId].orEmpty()

    /** Find the sign for one part of a traffic_sign value (without country prefix), resolving
     *  redirects, e.g. "274[30]" -> "274-30", "241" -> "241-30", "262-7.5" -> "262[7.5]".
     *  Returns null if the part is not a sign of this catalogue (this includes the named values
     *  like "city_limit", see [NAMED_TRAFFIC_SIGN_VALUES]). */
    fun resolve(osmValuePart: String): ResolvedTrafficSign? {
        val redirected = redirects[osmValuePart.lowercase()]
            ?: if (osmValuePart !in byOsmValuePart) dashValueRedirect(osmValuePart) else null
        if (redirected != null) {
            val resolved = resolveWithoutRedirect(redirected)
            if (resolved != null) return resolved
        }
        return resolveWithoutRedirect(osmValuePart)
    }

    private fun resolveWithoutRedirect(osmValuePart: String): ResolvedTrafficSign? {
        val (signId, value) = splitSignIdAndValue(osmValuePart)
        val sign = byOsmValuePart[osmValuePart]?.takeIf { value == null || it.signValueMatches(value) }
            ?: getBySignIdAndValue(signId, value)
            ?: return null
        val normalized = when {
            value != null -> combineSignIdAndValue(signId, value)
            // e.g. "274" for the variable sign "274[47]": the value is missing, keep it like that
            sign.hasVariableValue && sign.osmValuePart != osmValuePart -> signId
            else -> sign.osmValuePart
        }
        return ResolvedTrafficSign(sign, normalized, value)
    }

    /** like getSignBySignIdAndCheckValue of the converter */
    private fun getBySignIdAndValue(signId: String, value: String?): TrafficSign? {
        val matches = getBySignId(signId)
        if (matches.size == 1) return matches.single()
        if (matches.isEmpty()) return null
        return if (value == null) {
            matches.find { it.defaultValue == null }
        } else {
            matches.find { it.defaultValue != null }
        }
    }

    /** "262-7.5" -> "262[7.5]" for signs with redirectDashValues */
    private fun dashValueRedirect(osmValuePart: String): String? {
        val match = DASH_VALUE_REGEX.matchEntire(osmValuePart) ?: return null
        val signId = match.groupValues[1]
        if (signId !in dashValueSignIds) return null
        return "$signId[${match.groupValues[2]}]"
    }

    private fun TrafficSign.signValueMatches(value: String): Boolean =
        defaultValue == value || !hasVariableValue

    companion object {
        /** Parse the catalogue from the JSON as published in @osm-traffic-signs/converter under
         *  dist/data/{country}.json. Strict: throws an [IllegalArgumentException] naming all
         *  problems if any sign or relation can not be read (use this for checking data).
         *  See [parseLenient] for the run time variant. */
        fun parse(json: String): TrafficSignCatalogue {
            val result = parseLenient(json)
            val errors = result.problems.filter { it.isError }
            require(result.catalogue != null && errors.isEmpty()) { describeProblems(errors) }
            return result.catalogue
        }

        /** Parse the catalogue, but isolate broken entries: a sign or relation that can not be
         *  read (missing required field, unknown kind, wrong structure, duplicate osm value part)
         *  is left out and reported as a problem, all others stay usable. The catalogue is null if
         *  the file as a whole can not be used (no JSON object, no country, version or list of
         *  signs, no readable sign at all). Never throws because of the content. */
        fun parseLenient(json: String): CatalogueParseResult = try {
            parseCatalogue(json)
        } catch (e: Exception) {
            CatalogueParseResult(null, listOf(CatalogueProblem(null, null, "unexpected error: $e", true)))
        }

        /** Check all rules of the catalogue data, for the build and tests, not for the app:
         *  everything of [parseLenient] plus: redirects to something that is not in the catalogue,
         *  contradicting redirects, signs that would share an image, and (if [hasImage] is given)
         *  signs without image. An empty list means that the data is fine. */
        fun validate(json: String, hasImage: ((TrafficSign) -> Boolean)? = null): List<CatalogueProblem> {
            val result = parseLenient(json)
            val catalogue = result.catalogue ?: return result.problems
            val problems = ArrayList(result.problems)
            val knownTargets = catalogue.signs.flatMapTo(HashSet()) { listOf(it.osmValuePart, it.signId) } + NAMED_TRAFFIC_SIGN_VALUES
            val redirectTargets = HashMap<String, String>()
            for (sign in catalogue.signs) {
                for (redirect in sign.redirects) {
                    if (redirect.to !in knownTargets && splitSignIdAndValue(redirect.to).first !in knownTargets) {
                        problems.add(CatalogueProblem(null, sign.osmValuePart, "redirect \"${redirect.from}\" leads to \"${redirect.to}\", which is not in the catalogue", true))
                    }
                    val previous = redirectTargets.put(redirect.from.lowercase(), redirect.to)
                    if (previous != null && previous != redirect.to) {
                        problems.add(CatalogueProblem(null, sign.osmValuePart, "redirect \"${redirect.from}\" leads to \"$previous\" and to \"${redirect.to}\"", true))
                    }
                }
            }
            val byImageName = catalogue.signs.groupBy { trafficSignImageName(catalogue.country, it.osmValuePart) }
            for ((name, signs) in byImageName) {
                if (signs.size > 1) {
                    problems.add(CatalogueProblem(null, null, "image name $name is used by ${signs.joinToString { it.osmValuePart }}", true))
                }
            }
            if (hasImage != null) {
                for (sign in catalogue.signs) {
                    if (!hasImage(sign)) problems.add(CatalogueProblem(null, sign.osmValuePart, "no image", false))
                }
            }
            return problems
        }
    }
}

/** A problem found in the data of a catalogue. [isError]: the entry was left out (or, for checks
 *  of [TrafficSignCatalogue.validate], the data must not be released like this) */
data class CatalogueProblem(
    /** position of the sign in the list of signs, if known */
    val index: Int?,
    /** osm value part of the sign, if known */
    val id: String?,
    val message: String,
    val isError: Boolean,
) {
    override fun toString() = "${listOfNotNull(index?.let { "#$it" }, id).joinToString(" ")}: $message"
}

class CatalogueParseResult(val catalogue: TrafficSignCatalogue?, val problems: List<CatalogueProblem>)

private fun describeProblems(problems: List<CatalogueProblem>): String =
    "${problems.size} problems in the traffic sign catalogue: ${problems.take(10).joinToString("; ")}" +
        if (problems.size > 10) "; ..." else ""

private fun parseCatalogue(json: String): CatalogueParseResult {
    val root = try {
        jsonParser.parseToJsonElement(json) as? JsonObject
    } catch (e: SerializationException) {
        null
    } ?: return CatalogueParseResult(null, listOf(CatalogueProblem(null, null, "not a JSON object", true)))

    fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    val country = root.string("country")?.takeIf { it.isNotBlank() }
    val version = root.string("version")
    val signsJson = root["signs"] as? JsonArray
    if (country == null || version == null || signsJson == null) {
        return CatalogueParseResult(null, listOf(CatalogueProblem(null, null, "country, version or list of signs is missing", true)))
    }

    val problems = ArrayList<CatalogueProblem>()
    val signs = ArrayList<TrafficSign>()
    val seen = HashSet<String>()
    signsJson.forEachIndexed { index, element ->
        val id = (element as? JsonObject)?.string("osmValuePart")
        try {
            val sign = jsonParser.decodeFromJsonElement<SignJson>(element).toTrafficSign()
            if (sign.osmValuePart.isBlank() || sign.signId.isBlank()) {
                problems.add(CatalogueProblem(index, id, "osmValuePart or signId is empty", true))
            } else if (!seen.add(sign.osmValuePart)) {
                problems.add(CatalogueProblem(index, id, "duplicate osmValuePart", true))
            } else {
                signs.add(sign)
            }
        } catch (e: Exception) {
            problems.add(CatalogueProblem(index, id, e.message ?: e.toString(), true))
        }
    }

    val relations = ArrayList<TrafficSignRelation>()
    ((root["relations"] as? JsonArray) ?: JsonArray(emptyList())).forEachIndexed { index, element ->
        try {
            val r = jsonParser.decodeFromJsonElement<RelationJson>(element)
            relations.add(TrafficSignRelation(r.kind, r.topic, r.start, r.end))
        } catch (e: Exception) {
            problems.add(CatalogueProblem(null, "relation #$index", e.message ?: e.toString(), true))
        }
    }

    if (signs.isEmpty()) {
        problems.add(CatalogueProblem(null, null, "no readable sign", true))
        return CatalogueParseResult(null, problems)
    }
    return CatalogueParseResult(TrafficSignCatalogue(country, version, signs, relations), problems)
}

data class ResolvedTrafficSign(
    val sign: TrafficSign,
    /** normalized osm value part, e.g. "274-30" for "274[30]", or "274[33]" for a variable sign */
    val osmValuePart: String,
    /** variable value, e.g. "33" for "274[33]" */
    val value: String?,
)

/** Values of traffic_sign that are no reference to a sign in a catalogue and are thus written
 *  without country prefix, see namedTrafficSignValues of the converter */
val NAMED_TRAFFIC_SIGN_VALUES = setOf(
    "city_limit", "maxspeed", "none", "destination", "yes", "variable", "hazard", "signals",
    "give_way", "stop", "variable_message",
)

/** redirects that apply to all countries, see generalRedirects of the converter */
internal val GENERAL_REDIRECTS = mapOf("no" to "none")

private val DASH_VALUE_REGEX = Regex("^(.+)-(\\d+(?:\\.\\d+)?)$")

/** "274[30]" -> ("274", "30"), "\"Kfz-Verkehr frei\"" -> ("\"Kfz-Verkehr frei\"", null) */
internal fun splitSignIdAndValue(osmValuePart: String): Pair<String, String?> {
    if (osmValuePart.length >= 2 && osmValuePart.startsWith('"') && osmValuePart.endsWith('"')) {
        return osmValuePart to null
    }
    val bracket = osmValuePart.indexOf('[')
    if (bracket < 0 || !osmValuePart.endsWith(']')) return osmValuePart to null
    return osmValuePart.substring(0, bracket) to osmValuePart.substring(bracket + 1, osmValuePart.length - 1)
}

internal fun combineSignIdAndValue(signId: String, value: String?): String =
    if (value == null || signId.startsWith('"')) signId else "$signId[$value]"

/* ------------------------------------------ JSON ------------------------------------------ */

private val jsonParser = Json { ignoreUnknownKeys = true }

@Serializable
private data class CatalogueJson(
    val country: String,
    val version: String,
    val signs: List<SignJson>,
    val relations: List<RelationJson> = emptyList(),
)

@Serializable
private data class SignJson(
    val osmValuePart: String,
    val signId: String,
    val name: String,
    val descriptiveName: String? = null,
    val kind: String,
    val catalogue: SignCatalogueJson,
    val signValue: JsonPrimitive? = null,
    val valuePrompt: ValuePromptJson? = null,
    val redirects: List<RedirectJson> = emptyList(),
    val redirectDashValues: Boolean = false,
    /** either "none" or a list of [TagRecommendationJson] */
    val tagRecommendationsByGeometry: JsonElement,
    val compatibility: CompatibilityJson? = null,
)

@Serializable private data class SignCatalogueJson(val signCategory: String)

@Serializable private data class ValuePromptJson(val prompt: String, val format: String, val defaultValue: String)

@Serializable private data class RedirectJson(val from: String, val to: String)

@Serializable private data class TagJson(val key: String, val value: String? = null, val valueTemplate: String? = null)

@Serializable
private data class TagRecommendationJson(
    val geometries: List<String>,
    val highwayValues: List<String>? = null,
    val uniqueTags: List<TagJson>? = null,
    val accessTags: List<TagJson>? = null,
    val conditionalTags: List<TagJson>? = null,
    val modifierValue: String? = null,
)

@Serializable
private data class CompatibilityJson(
    val canReceiveModifiers: Boolean? = null,
    val incompatibleModifiers: List<String> = emptyList(),
)

@Serializable
private data class RelationJson(
    val kind: String,
    val topic: String,
    val start: String? = null,
    val end: String? = null,
)

private fun SignJson.toTrafficSign() = TrafficSign(
    osmValuePart = osmValuePart,
    signId = signId,
    name = name,
    descriptiveName = descriptiveName ?: name,
    kind = when (kind) {
        "traffic_sign" -> TrafficSignKind.TRAFFIC_SIGN
        "exception_modifier" -> TrafficSignKind.EXCEPTION_MODIFIER
        "condition_modifier" -> TrafficSignKind.CONDITION_MODIFIER
        else -> throw IllegalArgumentException("Unknown kind \"$kind\" of sign $osmValuePart")
    },
    category = catalogue.signCategory,
    defaultValue = signValue?.contentOrNull,
    valuePrompt = valuePrompt?.let { TrafficSignValuePrompt(it.prompt, it.format, it.defaultValue) },
    redirects = redirects.map { TrafficSignRedirect(it.from, it.to) },
    redirectDashValues = redirectDashValues,
    tagRecommendations = parseTagRecommendations(tagRecommendationsByGeometry),
    canReceiveModifiers = compatibility?.canReceiveModifiers,
    incompatibleModifiers = compatibility?.incompatibleModifiers.orEmpty(),
)

private fun SignJson.parseTagRecommendations(element: JsonElement): List<TrafficSignTagRecommendation>? =
    when {
        element is JsonPrimitive && element.contentOrNull == "none" -> null
        element is JsonArray -> jsonParser
            .decodeFromJsonElement<List<TagRecommendationJson>>(element)
            .map { it.toTagRecommendation() }
        else -> throw IllegalArgumentException("Unexpected tagRecommendationsByGeometry of sign $osmValuePart")
    }

private fun TagRecommendationJson.toTagRecommendation() = TrafficSignTagRecommendation(
    geometries = geometries.toSet(),
    highwayValues = highwayValues.orEmpty(),
    uniqueTags = uniqueTags.orEmpty().map { it.toPair() },
    accessTags = accessTags.orEmpty().map { it.toPair() },
    conditionalTags = conditionalTags.orEmpty().map { it.toPair() },
    modifierValue = modifierValue,
)

private fun TagJson.toPair(): Pair<String, String> = key to (value ?: valueTemplate ?: "")
