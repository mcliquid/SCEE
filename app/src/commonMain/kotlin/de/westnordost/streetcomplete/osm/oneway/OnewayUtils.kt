package de.westnordost.streetcomplete.osm.oneway

fun isForwardOneway(tags: Map<String, String>): Boolean =
    when (tags["oneway"]) {
        "yes", "1", "true" -> true
        null -> tags["junction"] == "roundabout"
        else -> false
    }

fun isReversedOneway(tags: Map<String, String>): Boolean =
    tags["oneway"] == "-1"

fun isOneway(tags: Map<String, String>): Boolean =
    isForwardOneway(tags) || isReversedOneway(tags)

fun isNotOnewayForCyclists(tags: Map<String, String>, isLeftHandTraffic: Boolean): Boolean =
    tags["oneway:bicycle"] == "no"
    || tags["cycleway"]?.startsWith("opposite") == true
    // any unambiguous opposite tagging implies oneway:bicycle = no
    || tags[
        if (isLeftHandTraffic xor isReversedOneway(tags)) "cycleway:right" else "cycleway:left"
    ]?.startsWith("opposite") == true
