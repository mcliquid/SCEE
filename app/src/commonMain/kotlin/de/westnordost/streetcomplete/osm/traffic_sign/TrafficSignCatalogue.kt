package de.westnordost.streetcomplete.osm.traffic_sign

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
        /** parse the catalogue from the JSON as published in @osm-traffic-signs/converter under
         *  dist/data/{country}.json */
        fun parse(json: String): TrafficSignCatalogue =
            jsonParser.decodeFromString<CatalogueJson>(json).toCatalogue()
    }
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

private fun CatalogueJson.toCatalogue() = TrafficSignCatalogue(
    country = country,
    version = version,
    signs = signs.map { it.toTrafficSign() },
    relations = relations.map { TrafficSignRelation(it.kind, it.topic, it.start, it.end) },
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
