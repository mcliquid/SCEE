package de.westnordost.streetcomplete.osm.traffic_sign

/** Path of the image (vector drawable XML converted from the SVG of the catalogue, see
 *  UpdateTrafficSignsTask) of the given sign within the compose resources */
fun TrafficSign.imagePath(country: String): String =
    "files/traffic_signs/$country/${trafficSignImageName(country, osmValuePart)}.xml"

/** Name of the image of a sign, like createSvgImportname of @osm-traffic-signs/converter, e.g.
 *  "274-30" -> "DE_274_30", "274[47]" -> "DE_274__47__", "\"Kfz-Verkehr frei\"" ->
 *  "DE__Kfz_Verkehr_frei_" */
fun trafficSignImageName(country: String, osmValuePart: String): String {
    val withDoubleUnderscores = osmValuePart.replace("[", "__").replace("]", "__")
    val validIdentifier = withDoubleUnderscores.replace(NON_IDENTIFIER_REGEX, "_")
    return "${country}_$validIdentifier"
}

private val NON_IDENTIFIER_REGEX = Regex("[^a-zA-Z0-9_]")
