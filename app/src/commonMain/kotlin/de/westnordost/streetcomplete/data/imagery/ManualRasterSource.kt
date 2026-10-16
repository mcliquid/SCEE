package de.westnordost.streetcomplete.data.imagery

import kotlinx.serialization.Serializable

@Serializable
data class ManualRasterSource(
    val id: String,
    val name: String,
    val url: String,
    val maxZoom: Int,
    val hideLabels: Boolean = false,
    /** When true and [coverage] contains the map center, Auto prefers this source over ELI. */
    val preferInAuto: Boolean = false,
    /** Optional geographic coverage; without it the source is never auto-selected worldwide. */
    val coverage: ManualRasterCoverage? = null,
)
