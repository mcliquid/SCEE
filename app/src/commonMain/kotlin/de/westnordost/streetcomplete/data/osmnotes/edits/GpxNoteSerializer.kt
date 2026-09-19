package de.westnordost.streetcomplete.data.osmnotes.edits

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osmtracks.Trackpoint
import de.westnordost.streetcomplete.util.ktx.attribute
import de.westnordost.streetcomplete.util.ktx.endTag
import de.westnordost.streetcomplete.util.ktx.startTag
import kotlinx.io.Buffer
import kotlinx.io.writeString
import nl.adaptivity.xmlutil.EventType.END_ELEMENT
import nl.adaptivity.xmlutil.EventType.ENTITY_REF
import nl.adaptivity.xmlutil.EventType.START_ELEMENT
import nl.adaptivity.xmlutil.EventType.TEXT
import nl.adaptivity.xmlutil.EventType.CDSECT
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.XmlWriter
import nl.adaptivity.xmlutil.core.kxio.newReader
import nl.adaptivity.xmlutil.newWriter
import nl.adaptivity.xmlutil.xmlStreaming
import kotlin.time.Instant

internal class GpxNoteSerializer(private val creator: String) {

    fun appendWaypoint(gpx: String?, position: LatLon, name: String): String {
        val waypoints = gpx?.let(::parseWaypoints).orEmpty() + Waypoint(position, name)
        return writeGpx { writer -> waypoints.forEach { writer.write(it) } }
    }

    fun serializeTrack(name: String, trackpoints: List<Trackpoint>): String = writeGpx { writer ->
        writer.startTag("trk")
        writer.element("name", name)
        writer.startTag("trkseg")
        for (trackpoint in trackpoints) writer.write(trackpoint)
        writer.endTag("trkseg")
        writer.endTag("trk")
    }

    private fun writeGpx(content: (XmlWriter) -> Unit): String {
        val result = StringBuilder()
        val writer = xmlStreaming.newWriter(result)
        writer.startTag("gpx")
        writer.attribute("xmlns", GPX_NAMESPACE)
        writer.attribute("version", "1.1")
        writer.attribute("creator", creator)
        content(writer)
        writer.endTag("gpx")
        writer.close()
        return result.toString()
    }
}

private data class Waypoint(val position: LatLon, val name: String)

private fun parseWaypoints(gpx: String): List<Waypoint> {
    val source = Buffer().apply { writeString(gpx) }
    val reader = xmlStreaming.newReader(source)
    val result = mutableListOf<Waypoint>()
    var latitude: Double? = null
    var longitude: Double? = null
    var name = StringBuilder()
    var inName = false
    reader.forEach { event ->
        when (event) {
            START_ELEMENT -> when (reader.localName) {
                "wpt" -> {
                    latitude = reader.getAttributeValue(null, "lat")?.toDouble()
                    longitude = reader.getAttributeValue(null, "lon")?.toDouble()
                    name = StringBuilder()
                }
                "name" -> if (latitude != null) {
                    name = StringBuilder()
                    inName = true
                }
            }
            TEXT, ENTITY_REF, CDSECT -> if (inName) name.append(reader.text)
            END_ELEMENT -> when (reader.localName) {
                "name" -> inName = false
                "wpt" -> {
                    result += Waypoint(LatLon(latitude!!, longitude!!), name.toString())
                    latitude = null
                    longitude = null
                }
            }
            else -> Unit
        }
    }
    return result
}

private fun XmlWriter.write(waypoint: Waypoint) {
    startTag("wpt")
    attribute("lat", waypoint.position.latitude.toString())
    attribute("lon", waypoint.position.longitude.toString())
    element("name", waypoint.name)
    endTag("wpt")
}

private fun XmlWriter.write(trackpoint: Trackpoint) {
    startTag("trkpt")
    attribute("lat", trackpoint.position.latitude.toString())
    attribute("lon", trackpoint.position.longitude.toString())
    if (trackpoint.elevation != 0.0f) element("ele", trackpoint.elevation.toString())
    element("time", Instant.fromEpochMilliseconds(trackpoint.time).toString())
    if (trackpoint.elevation != 0.0f) element("hdop", trackpoint.accuracy.toString())
    endTag("trkpt")
}

private fun XmlWriter.element(name: String, value: String) {
    startTag(name)
    text(value)
    endTag(name)
}

private const val GPX_NAMESPACE = "http://www.topografix.com/GPX/1/1"
