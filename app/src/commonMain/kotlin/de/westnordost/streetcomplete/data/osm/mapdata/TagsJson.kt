package de.westnordost.streetcomplete.data.osm.mapdata

internal expect fun encodeTagsJson(tags: Map<String, String>): String
internal expect fun decodeTagsJson(json: String): Map<String, String>?
