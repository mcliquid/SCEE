package de.westnordost.streetcomplete.quests.paving_stones_material

import de.westnordost.streetcomplete.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

enum class PavingStonesMaterial(
    val osmValue: String
) {
    BRICK("brick"),
    CONCRETE("concrete"),
    STONE("stone");

    companion object {
        val selectableValues = entries
    }
}

val PavingStonesMaterial.icon: DrawableResource
    get() = when (this) {
        PavingStonesMaterial.BRICK -> Res.drawable.paving_stones_brick
        PavingStonesMaterial.CONCRETE -> Res.drawable.paving_stones_concrete
        PavingStonesMaterial.STONE -> Res.drawable.paving_stones_stone
    }

val PavingStonesMaterial.title: StringResource
    get() = when (this) {
        PavingStonesMaterial.BRICK -> Res.string.quest_material_brick
        PavingStonesMaterial.CONCRETE -> Res.string.quest_material_concrete
        PavingStonesMaterial.STONE -> Res.string.quest_material_stone
    }
