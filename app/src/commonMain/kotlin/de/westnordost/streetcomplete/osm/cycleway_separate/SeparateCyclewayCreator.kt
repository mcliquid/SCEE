package de.westnordost.streetcomplete.osm.cycleway_separate

import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.osm.any
import de.westnordost.streetcomplete.osm.cycleway_separate.SeparateCycleway.*
import de.westnordost.streetcomplete.osm.hasCheckDateForKey
import de.westnordost.streetcomplete.osm.sidewalk.KNOWN_SIDEWALK_KEYS
import de.westnordost.streetcomplete.osm.sidewalk.Sidewalk
import de.westnordost.streetcomplete.osm.sidewalk.parseSidewalkSides
import de.westnordost.streetcomplete.osm.updateCheckDateForKey

fun SeparateCycleway.applyTo(tags: Tags) {
    val isCycleway = tags["highway"] == "cycleway"
    val isFootway = tags["highway"] == "footway"

    // tag bicycle=*, foot=* and retag highway=* if necessary
    when (this) {
        PATH -> {
            // Only a designation is contradicted by this answer, not signs for access restrictions.
            val wasDesignated = tags["bicycle"] == "designated" || isCycleway && !tags.containsKey("bicycle")
            tags["highway"] = "path"

            // Removing designation does not confirm that access restrictions have been lifted.
            if (tags["foot"] == "designated") {
                tags["foot"] = "yes"
            }
            if (tags["bicycle"] == "designated") {
                tags["bicycle"] = "yes"
            }
            if (wasDesignated && tags["bicycle:signed"] == "yes") {
                tags.remove("bicycle:signed")
            }
        }
        NOT_ALLOWED -> {
            if (tags["bicycle"] !in noCycling) tags["bicycle"] = "no"
            if (isCycleway) {
                tags["highway"] = if (tags["foot"] == "designated") "footway" else "path"
            }
            tags["bicycle:signed"] = "yes"
        }
        ALLOWED_ON_FOOTWAY, NON_DESIGNATED_ON_FOOTWAY -> {
            tags["highway"] = "footway"
            if (tags.containsKey("foot")) tags["foot"] = "designated"

            if (this == ALLOWED_ON_FOOTWAY) {
                tags["bicycle"] = "yes"
                tags["bicycle:signed"] = "yes"
            } else {
                if (tags["bicycle"] == "designated") tags.remove("bicycle")
                tags.remove("bicycle:signed")
            }
        }
        NON_SEGREGATED, SEGREGATED -> {
            if (!isCycleway || tags.containsKey("bicycle")) {
                tags["bicycle"] = "designated"
            }
            if (!isFootway || tags.containsKey("foot")) {
                tags["foot"] = "designated"
            }
        }
        EXCLUSIVE, EXCLUSIVE_WITH_SIDEWALK -> {
            // retag if it is an exclusive cycleway
            tags["highway"] = "cycleway"
            if (tags.containsKey("bicycle")) tags["bicycle"] = "designated"
            // if bicycle:signed is explicitly no, set it to yes
            if (tags["bicycle:signed"] == "no") tags["bicycle:signed"] = "yes"
            if (this == EXCLUSIVE) {
                if (tags["foot"] == "designated") tags.remove("foot")
            } else {
                // follow the same pattern as for roads here: It is uncommon for roads to have foot
                // tagged at all when such roads have sidewalks
                tags.remove("foot")
            }
        }
    }

    // tag or remove sidewalk
    val hasSidewalk = parseSidewalkSides(tags)?.any { it == Sidewalk.YES } == true || tags["sidewalk"] == "yes"
    when (this) {
        EXCLUSIVE_WITH_SIDEWALK -> {
            if (!hasSidewalk) {
                KNOWN_SIDEWALK_KEYS.forEach { tags.remove(it) }
                tags["sidewalk"] = "yes" // cannot be more specific here
            }
        }
        else -> {
            if (hasSidewalk) {
                KNOWN_SIDEWALK_KEYS.forEach { tags.remove(it) }
            }
        }
    }

    // tag segregated
    when (this) {
        PATH, NOT_ALLOWED, ALLOWED_ON_FOOTWAY, NON_DESIGNATED_ON_FOOTWAY, EXCLUSIVE, EXCLUSIVE_WITH_SIDEWALK -> {
            tags.remove("segregated")
        }
        NON_SEGREGATED -> {
            tags["segregated"] = "no"
        }
        SEGREGATED -> {
            tags["segregated"] = "yes"
        }
    }

    // update check date
    if (!tags.hasChanges || tags.hasCheckDateForKey("bicycle")) {
        tags.updateCheckDateForKey("bicycle")
    }
}

private val noCycling = setOf(
    "no", "dismount"
)
