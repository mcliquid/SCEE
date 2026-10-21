package de.westnordost.streetcomplete.data.osm.mapdata

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

private val tagsAdapter: JsonAdapter<Map<String, String>> = Moshi.Builder().build()
    .adapter(Types.newParameterizedType(Map::class.java, String::class.java, String::class.java))

internal actual fun encodeTagsJson(tags: Map<String, String>): String = tagsAdapter.toJson(tags)
internal actual fun decodeTagsJson(json: String): Map<String, String>? = tagsAdapter.fromJson(json)
