package de.westnordost.streetcomplete.quests.roof_colour

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.building_colour.OsmColour
import de.westnordost.streetcomplete.quests.roof_shape.RoofShape

enum class RoofColour(override val osmValue: String, override val androidValue: String?) :
    OsmColour {
    // Top used roof colours
    DARK_GREY("darkgrey", "#a9a9a9"),
    GREY("grey", "#808080"),
    LIGHT_GREY("lightgrey", "#d3d3d3"),
    RED("red", "#ff0000"),
    BROWN("brown", "#a52a2a"),
    MAROON("maroon", "#800000"),
    BLACK("black", "#000000"),
    WHITE("white", "#ffffff"),
    SILVER("silver", "#c0c0c0"),
    BLUE("blue", "#0000ff"),
    SALMON("salmon", "#fa8072"),
    DESERT_SAND("#bbad8e", null),
    MOCHA("#938870", null),

    // Rest of the recommended 3D palette
    OLIVE("olive", "#808000"),
    GREEN("green", "#008000"),
    TEAL("teal", "#008080"),
    NAVY("navy", "#000080"),
    PURPLE("purple", "#800080"),
    YELLOW("yellow", "#ffff00"),
    LIME("lime", "#00ff00"),
    AQUA("aqua", "#00ffff"),
    FUCHSIA("fuchsia", "#ff00ff"),
}

val RoofShape.colorIconResId: Int?
    get() = when (this) {
        RoofShape.GABLED ->            R.drawable.ic_roof_colour_gabled
        RoofShape.HIPPED ->            R.drawable.ic_roof_colour_hipped
        RoofShape.FLAT ->              R.drawable.ic_roof_colour_flat
        RoofShape.PYRAMIDAL ->         R.drawable.ic_roof_colour_pyramidal
        RoofShape.HALF_HIPPED ->       R.drawable.ic_roof_colour_half_hipped
        RoofShape.SKILLION ->          R.drawable.ic_roof_colour_skillion
        RoofShape.GAMBREL ->           R.drawable.ic_roof_colour_gambrel
        RoofShape.ROUND ->             R.drawable.ic_roof_colour_round
        RoofShape.DOUBLE_SALTBOX ->    R.drawable.ic_roof_colour_double_saltbox
        RoofShape.SALTBOX ->           R.drawable.ic_roof_colour_saltbox
        RoofShape.MANSARD ->           R.drawable.ic_roof_colour_mansard
        RoofShape.DOME ->              R.drawable.ic_roof_colour_dome
        RoofShape.QUADRUPLE_SALTBOX -> R.drawable.ic_roof_colour_quadruple_saltbox
        RoofShape.ROUND_GABLED ->      R.drawable.ic_roof_colour_round_gabled
        RoofShape.ONION ->             R.drawable.ic_roof_colour_onion
        RoofShape.CONE ->              R.drawable.ic_roof_colour_cone
        else ->                        null
    }
