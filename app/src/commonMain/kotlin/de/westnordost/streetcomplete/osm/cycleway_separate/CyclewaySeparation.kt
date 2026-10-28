package de.westnordost.streetcomplete.osm.cycleway_separate

/** Documented physical separation values from the separation proposal (surveyable on site). */
enum class CyclewaySeparation(val osmValue: String) {
    NO("no"),
    KERB("kerb"),
    BOLLARD("bollard"),
    FLEX_POST("flex_post"),
    VERTICAL_PANEL("vertical_panel"),
    BUMP("bump"),
    STUDS("studs"),
    PLANTER("planter"),
    GREENERY("greenery"),
    HEDGE("hedge"),
    TREE_ROW("tree_row"),
    FENCE("fence"),
    GUARD_RAIL("guard_rail"),
    JERSEY_BARRIER("jersey_barrier"),
    DITCH("ditch"),
    ;

    companion object {
        private val byOsmValue = entries.associateBy { it.osmValue }

        fun fromSingleOsmValue(value: String): CyclewaySeparation? = byOsmValue[value]
    }
}

/** Values offered in the quest UI (physical separation only; no paint or vague placeholders). */
val SURVEYABLE_CYCLEWAY_SEPARATIONS: List<CyclewaySeparation> = CyclewaySeparation.entries
