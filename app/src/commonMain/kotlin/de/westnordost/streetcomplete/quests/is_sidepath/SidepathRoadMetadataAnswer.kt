package de.westnordost.streetcomplete.quests.is_sidepath

sealed interface SidepathRoadMetadataAnswer {
    data class RoadAssociation(
        val ofName: String?,
        val ofHighway: String?,
        val ofRef: String? = null,
    ) : SidepathRoadMetadataAnswer

    /** Way was incorrectly classified as a sidepath; set is_sidepath=no and drop road association. */
    data object NotSidepath : SidepathRoadMetadataAnswer
}
