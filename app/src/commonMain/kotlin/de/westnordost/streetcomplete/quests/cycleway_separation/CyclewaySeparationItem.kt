package de.westnordost.streetcomplete.quests.cycleway_separation

import de.westnordost.streetcomplete.osm.cycleway_separate.CyclewaySeparation
import de.westnordost.streetcomplete.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

val CyclewaySeparation.title: StringResource get() = when (this) {
    CyclewaySeparation.NO -> Res.string.quest_cycleway_separation_no
    CyclewaySeparation.KERB -> Res.string.quest_cycleway_separation_kerb
    CyclewaySeparation.BOLLARD -> Res.string.quest_cycleway_separation_bollard
    CyclewaySeparation.FLEX_POST -> Res.string.quest_cycleway_separation_flex_post
    CyclewaySeparation.VERTICAL_PANEL -> Res.string.quest_cycleway_separation_vertical_panel
    CyclewaySeparation.BUMP -> Res.string.quest_cycleway_separation_bump
    CyclewaySeparation.STUDS -> Res.string.quest_cycleway_separation_studs
    CyclewaySeparation.PLANTER -> Res.string.quest_cycleway_separation_planter
    CyclewaySeparation.GREENERY -> Res.string.quest_cycleway_separation_greenery
    CyclewaySeparation.HEDGE -> Res.string.quest_cycleway_separation_hedge
    CyclewaySeparation.TREE_ROW -> Res.string.quest_cycleway_separation_tree_row
    CyclewaySeparation.FENCE -> Res.string.quest_cycleway_separation_fence
    CyclewaySeparation.GUARD_RAIL -> Res.string.quest_cycleway_separation_guard_rail
    CyclewaySeparation.JERSEY_BARRIER -> Res.string.quest_cycleway_separation_jersey_barrier
    CyclewaySeparation.DITCH -> Res.string.quest_cycleway_separation_ditch
}

val CyclewaySeparation.icon: DrawableResource get() = when (this) {
    CyclewaySeparation.NO -> Res.drawable.cycleway_separation_no
    CyclewaySeparation.KERB -> Res.drawable.cycleway_separation_kerb
    CyclewaySeparation.BOLLARD -> Res.drawable.cycleway_separation_bollard
    CyclewaySeparation.FLEX_POST -> Res.drawable.cycleway_separation_flex_post
    CyclewaySeparation.VERTICAL_PANEL -> Res.drawable.cycleway_separation_vertical_panel
    CyclewaySeparation.BUMP -> Res.drawable.cycleway_separation_bump
    CyclewaySeparation.STUDS -> Res.drawable.cycleway_separation_studs
    CyclewaySeparation.PLANTER -> Res.drawable.cycleway_separation_planter
    CyclewaySeparation.GREENERY -> Res.drawable.cycleway_separation_greenery
    CyclewaySeparation.HEDGE -> Res.drawable.cycleway_separation_hedge
    CyclewaySeparation.TREE_ROW -> Res.drawable.cycleway_separation_tree_row
    CyclewaySeparation.FENCE -> Res.drawable.cycleway_separation_fence
    CyclewaySeparation.GUARD_RAIL -> Res.drawable.cycleway_separation_guard_rail
    CyclewaySeparation.JERSEY_BARRIER -> Res.drawable.cycleway_separation_jersey_barrier
    CyclewaySeparation.DITCH -> Res.drawable.cycleway_separation_ditch
}
