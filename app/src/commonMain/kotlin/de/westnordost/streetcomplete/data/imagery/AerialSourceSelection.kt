package de.westnordost.streetcomplete.data.imagery

/** Explicitly selected aerial imagery source. Only this selection is active — no automatic priority. */
sealed class AerialSourceSelection {
    data object Esri : AerialSourceSelection()
    data object Auto : AerialSourceSelection()
    data class Eli(val id: String) : AerialSourceSelection()
    data class Manual(val id: String) : AerialSourceSelection()

    fun encode(): String = when (this) {
        Esri -> "esri"
        Auto -> "auto"
        is Eli -> "eli:$id"
        is Manual -> "manual:$id"
    }

    companion object {
        const val DEFAULT_ENCODED = "esri"

        fun decode(value: String?): AerialSourceSelection {
            if (value.isNullOrBlank()) return Esri
            return when {
                value == "esri" -> Esri
                value == "auto" -> Auto
                value.startsWith("eli:") -> {
                    val id = value.removePrefix("eli:")
                    if (id.isBlank()) Esri else Eli(id)
                }
                value.startsWith("manual:") -> {
                    val id = value.removePrefix("manual:")
                    if (id.isBlank()) Esri else Manual(id)
                }
                else -> Esri
            }
        }
    }
}
