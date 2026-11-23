package de.westnordost.streetcomplete.osm.traffic_sign

import de.westnordost.streetcomplete.data.traffic_signs.TrafficSignCatalogues
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

    /** All bundled catalogues, as the app reads them (index + catalogue per country) */
    val bundledCatalogues: TrafficSignCatalogues by lazy {
        TrafficSignCatalogues(
            loadIndex = { readBytesOrNull("$RESOURCES_DIR/files/traffic_signs/${TrafficSignCatalogues.INDEX_FILE}")?.decodeToString() },
            loadJson = { country -> readBytesOrNull("$RESOURCES_DIR/files/traffic_signs/$country.json")?.decodeToString() },
        )
    }

    fun readText(path: String): String =
        SystemFileSystem.source(Path(path)).buffered().use { it.readString() }

    /** Like [readText], but normalizes line endings to LF (Windows checkouts may use CRLF). */
    fun readTextLf(path: String): String =
        readText(path).replace("\r\n", "\n").replace("\r", "\n")

    fun readBytesOrNull(path: String): ByteArray? {
        val p = Path(path)
        if (!SystemFileSystem.exists(p)) return null
        return SystemFileSystem.source(p).buffered().use { it.readByteArray() }
    }
}

fun parseDE(value: String): TrafficSignValue = parseTrafficSignValue(value, TrafficSignTestData.catalogueDE)
