package de.westnordost.streetcomplete.osm.traffic_sign

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString

/** The bundled traffic sign resources, read directly from the source directory (tests run with the
 *  app module as working directory) */
object TrafficSignTestData {
    const val RESOURCES_DIR = "src/commonMain/composeResources"

    val catalogueDE: TrafficSignCatalogue by lazy {
        TrafficSignCatalogue.parse(readText("$RESOURCES_DIR/files/traffic_signs/DE.json"))
    }

    fun readText(path: String): String =
        SystemFileSystem.source(Path(path)).buffered().use { it.readString() }

    fun readBytesOrNull(path: String): ByteArray? {
        val p = Path(path)
        if (!SystemFileSystem.exists(p)) return null
        return SystemFileSystem.source(p).buffered().use { it.readByteArray() }
    }
}

fun parseDE(value: String): TrafficSignValue = parseTrafficSignValue(value, TrafficSignTestData.catalogueDE)
