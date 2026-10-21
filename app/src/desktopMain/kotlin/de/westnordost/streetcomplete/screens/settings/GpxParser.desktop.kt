package de.westnordost.streetcomplete.screens.settings

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import java.io.StringReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

internal actual fun parseGpxTrackPoints(gpx: String): List<LatLon> {
    val factory = XMLInputFactory.newFactory().apply {
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    }
    val reader = factory.createXMLStreamReader(StringReader(gpx))
    val points = ArrayList<LatLon>()
    var depth = 0
    var trkDepth = -1
    try {
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    depth++
                    when (reader.localName) {
                        "trk" -> if (trkDepth < 0) trkDepth = depth
                        "trkpt" -> if (trkDepth >= 0) {
                            val lat = requireNotNull(reader.getAttributeValue(null, "lat")) { "trkpt without lat" }
                            val lon = requireNotNull(reader.getAttributeValue(null, "lon")) { "trkpt without lon" }
                            points.add(LatLon(lat.toDouble(), lon.toDouble()))
                        }
                    }
                }
                XMLStreamConstants.END_ELEMENT -> {
                    if (depth == trkDepth) trkDepth = -1
                    depth--
                }
            }
        }
    } finally {
        reader.close()
    }
    return points
}
