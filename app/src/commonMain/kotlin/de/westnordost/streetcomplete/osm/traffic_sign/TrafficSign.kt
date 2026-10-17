package de.westnordost.streetcomplete.osm.traffic_sign

/** A traffic sign as defined in the traffic sign catalogue of one country, see
 *  https://github.com/osmberlin/osm-traffic-sign-tool */
data class TrafficSign(
    /** the part of the traffic_sign value that identifies this sign, without country prefix,
     *  e.g. "274-30", "274[47]" (a sign with variable value, here with its default value) or
     *  "\"Kfz-Verkehr frei\"" (free text sign) */
    val osmValuePart: String,
    /** id of the sign without variable value, e.g. "274" for "274[47]" */
    val signId: String,
    /** official name of the sign, e.g. "Zeichen 237" */
    val name: String,
    /** human-readable name in the language of the catalogue, e.g. "Radweg" */
    val descriptiveName: String,
    val kind: TrafficSignKind,
    /** category within the catalogue, e.g. "traffic_sign", "speed", "hazard_sign" */
    val category: String,
    /** default value of a sign with variable value, e.g. "47" for "274[47]" */
    val defaultValue: String?,
    /** how the variable value of this sign is to be entered, null if the sign has no variable value */
    val valuePrompt: TrafficSignValuePrompt?,
    /** alternative spellings of this sign in traffic_sign values that should be read as this sign */
    val redirects: List<TrafficSignRedirect>,
    /** whether e.g. "262-7.5" should be read as "262[7.5]" */
    val redirectDashValues: Boolean,
    /** tag recommendations of the catalogue. null if the catalogue explicitly defines that there
     *  are none. These are informative only, see [deriveWayTags] for what is actually tagged. */
    val tagRecommendations: List<TrafficSignTagRecommendation>?,
    /** null if not specified */
    val canReceiveModifiers: Boolean?,
    val incompatibleModifiers: List<String>,
) {
    val isModifier: Boolean get() = kind != TrafficSignKind.TRAFFIC_SIGN
    /** whether the value of this sign is written in brackets, e.g. "274[30]". (A few signs like
     *  "274.2" have a value prompt, but the value is implied by the sign itself) */
    val hasVariableValue: Boolean get() = valuePrompt != null && osmValuePart.endsWith(']')
}

enum class TrafficSignKind {
    /** a main sign */
    TRAFFIC_SIGN,
    /** additional sign that grants an exception, e.g. "Anlieger frei" */
    EXCEPTION_MODIFIER,
    /** additional sign that restricts the main sign to a condition, e.g. a time span */
    CONDITION_MODIFIER,
}

data class TrafficSignValuePrompt(
    val prompt: String,
    /** e.g. "integer", "float", "time_restriction", "opening_hours" */
    val format: String,
    val defaultValue: String,
)

data class TrafficSignRedirect(val from: String, val to: String)

data class TrafficSignTagRecommendation(
    /** e.g. "node", "way", "area" */
    val geometries: Set<String>,
    val highwayValues: List<String>,
    val uniqueTags: List<Pair<String, String>>,
    val accessTags: List<Pair<String, String>>,
    val conditionalTags: List<Pair<String, String>>,
    val modifierValue: String?,
)

/** Signs that belong together, e.g. start and end of a zone ("274.1" and "274.2") */
data class TrafficSignRelation(
    /** "zone", "section", "place" or "incline" */
    val kind: String,
    val topic: String,
    val start: String?,
    val end: String?,
)
