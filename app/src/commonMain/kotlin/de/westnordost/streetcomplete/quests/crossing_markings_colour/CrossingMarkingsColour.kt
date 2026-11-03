package de.westnordost.streetcomplete.quests.crossing_markings_colour

import de.westnordost.streetcomplete.quests.building_colour.OsmColour
import de.westnordost.streetcomplete.resources.*
import org.jetbrains.compose.resources.StringResource

enum class CrossingMarkingsColour(
    override val osmValue: String,
    override val hexValue: String?,
    val titleRes: StringResource,
) : OsmColour {
    RED("red", "#ff0000", Res.string.quest_crossing_markings_colour_red),
    WHITE("white", "#ffffff", Res.string.quest_crossing_markings_colour_white),
    YELLOW("yellow", "#ffff00", Res.string.quest_crossing_markings_colour_yellow),
    GREEN("green", "#008000", Res.string.quest_crossing_markings_colour_green),
    BLUE("blue", "#0000ff", Res.string.quest_crossing_markings_colour_blue),
    ;

    companion object {
        private val byOsmValue = entries.associateBy { it.osmValue }

        fun fromOsmValue(value: String): CrossingMarkingsColour? = byOsmValue[value]
    }
}

data class CrossingMarkingsColourAnswer(
    val standard: Set<CrossingMarkingsColour>,
    val other: Set<String> = emptySet(),
) {
    fun toTagValue(): String =
        (standard.map { it.osmValue } + other)
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
            .joinToString(";")
}

fun parseCrossingMarkingsColourAnswer(tags: Map<String, String>): CrossingMarkingsColourAnswer {
    val raw = tags["crossing:markings:colour"] ?: return CrossingMarkingsColourAnswer(emptySet())
    val values = raw.split(';').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    val standard = values.mapNotNull { CrossingMarkingsColour.fromOsmValue(it) }.toSet()
    val other = values.filter { CrossingMarkingsColour.fromOsmValue(it) == null }.toSet()
    return CrossingMarkingsColourAnswer(standard, other)
}

fun normalizeOtherCrossingMarkingsColour(input: String): String? {
    val value = input.trim().lowercase()
    if (value.isEmpty() || value == "none") return null
    if (CrossingMarkingsColour.fromOsmValue(value) != null) return null
    return value
}
