package de.westnordost.streetcomplete.data.osmnotes.edits

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osmtracks.Trackpoint
import kotlinx.io.Buffer
import kotlinx.io.writeString
import nl.adaptivity.xmlutil.EventType.END_ELEMENT
import nl.adaptivity.xmlutil.EventType.ENTITY_REF
import nl.adaptivity.xmlutil.EventType.START_ELEMENT
import nl.adaptivity.xmlutil.EventType.TEXT
import nl.adaptivity.xmlutil.EventType.CDSECT
import nl.adaptivity.xmlutil.core.kxio.newReader
import nl.adaptivity.xmlutil.xmlStreaming
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.time.Instant

class GpxNoteSerializerTest {

    private val serializer = GpxNoteSerializer("SCEE test")

    @Test fun `waypoint GPX has required metadata and safely escaped text`() {
        val specialText = "Survey & note <open> > close \"quoted\" 'apostrophe'"
        val first = serializer.appendWaypoint(null, LatLon(1.25, 2.5), specialText)
        val result = serializer.appendWaypoint(first, LatLon(-3.75, 4.0), "second")

        val root = parse(result)
        assertRootMetadata(root)
        assertEquals(listOf("wpt", "wpt"), root.children.map { it.name })
        assertEquals("1.25", root.children[0].attributes["lat"])
        assertEquals("2.5", root.children[0].attributes["lon"])
        assertEquals(specialText, root.children[0].child("name").text)
        assertEquals("second", root.children[1].child("name").text)
        assertFalse(result.contains("Survey & note"))
    }

    @Test fun `track GPX uses GPX 1_1 order and typed values`() {
        val points = listOf(
            Trackpoint(
                LatLon(12.34, 56.78),
                Instant.parse("2024-06-05T09:51:14.123Z").toEpochMilliseconds(),
                accuracy = 4.56f,
                elevation = 1.23f,
            ),
            Trackpoint(
                LatLon(12.35, 56.79),
                Instant.parse("2024-06-05T09:51:15Z").toEpochMilliseconds(),
                accuracy = 4.57f,
                elevation = 1.24f,
            ),
        )

        val result = serializer.serializeTrack("track & <one>", points)
        val root = parse(result)
        assertRootMetadata(root)
        val track = root.child("trk")
        assertEquals("track & <one>", track.child("name").text)
        val trackPoints = track.child("trkseg").children
        assertEquals(2, trackPoints.size)
        assertEquals(listOf("ele", "time", "hdop"), trackPoints[0].children.map { it.name })
        assertEquals("1.23", trackPoints[0].child("ele").text)
        assertEquals("2024-06-05T09:51:14.123Z", trackPoints[0].child("time").text)
        assertEquals("4.56", trackPoints[0].child("hdop").text)
        assertFalse(result.contains("<ele>\""))
        assertFalse(result.contains("<hdop>\""))
    }

    @Test fun `waypoint without name does not inherit previous waypoint name`() {
        val legacyGpx = """
            <gpx xmlns="http://www.topografix.com/GPX/1/1">
              <wpt lat="1.0" lon="2.0"><name>first</name></wpt>
              <wpt lat="3.0" lon="4.0" />
            </gpx>
        """.trimIndent()

        val root = parse(serializer.appendWaypoint(legacyGpx, LatLon(5.0, 6.0), "new"))

        assertEquals("first", root.children[0].child("name").text)
        assertEquals("", root.children[1].child("name").text)
        assertEquals("new", root.children[2].child("name").text)
    }

    @Test fun `legacy SCEE notes GPX roundtrips into corrected envelope`() {
        val legacyGpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx
             xmlns="http://www.topografix.com/GPX/1/1"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">
             <wpt lon="2.5" lat="1.25">
              <name>first &amp; survey &lt;note&gt;</name>
             </wpt>
             <wpt lon="4.0" lat="-3.75">
              <name>second &quot;quoted&quot; note</name>
             </wpt>
            </gpx>
        """.trimIndent()

        val result = serializer.appendWaypoint(
            legacyGpx,
            LatLon(5.5, 6.75),
            "new & <waypoint>",
        )
        val root = parse(result)

        assertRootMetadata(root)
        assertEquals(3, root.children.size)
        assertEquals("1.25", root.children[0].attributes["lat"])
        assertEquals("2.5", root.children[0].attributes["lon"])
        assertEquals("first & survey <note>", root.children[0].child("name").text)
        assertEquals("-3.75", root.children[1].attributes["lat"])
        assertEquals("4.0", root.children[1].attributes["lon"])
        assertEquals("second \"quoted\" note", root.children[1].child("name").text)
        assertEquals("5.5", root.children[2].attributes["lat"])
        assertEquals("6.75", root.children[2].attributes["lon"])
        assertEquals("new & <waypoint>", root.children[2].child("name").text)
        assertFalse(result.contains("new & <waypoint>"))
    }

    @Test fun `zero elevation omits elevation and hdop`() {
        val result = serializer.serializeTrack(
            "zero elevation",
            listOf(Trackpoint(LatLon(1.0, 2.0), 0L, accuracy = 3.0f, elevation = 0.0f)),
        )

        val trackPoint = parse(result).child("trk").child("trkseg").child("trkpt")
        assertEquals(listOf("time"), trackPoint.children.map { it.name })
    }

    @Test fun `negative elevation emits elevation and hdop`() {
        val result = serializer.serializeTrack(
            "below sea level",
            listOf(Trackpoint(LatLon(1.0, 2.0), 0L, accuracy = 3.5f, elevation = -12.25f)),
        )

        val trackPoint = parse(result).child("trk").child("trkseg").child("trkpt")
        assertEquals(listOf("ele", "time", "hdop"), trackPoint.children.map { it.name })
        assertEquals("-12.25", trackPoint.child("ele").text)
        assertEquals("3.5", trackPoint.child("hdop").text)
    }

    private fun assertRootMetadata(root: Element) {
        assertEquals("gpx", root.name)
        assertEquals(GPX_1_1_NAMESPACE, root.namespace)
        assertEquals("1.1", root.attributes["version"])
        assertEquals("SCEE test", root.attributes["creator"])
    }
}

private data class Element(
    val name: String,
    val namespace: String,
    val attributes: Map<String, String>,
    val children: MutableList<Element> = mutableListOf(),
    var text: String = "",
) {
    fun child(name: String): Element = assertNotNull(children.singleOrNull { it.name == name })
}

private fun parse(xml: String): Element {
    val source = Buffer().apply { writeString(xml) }
    val reader = xmlStreaming.newReader(source)
    val stack = mutableListOf<Element>()
    var root: Element? = null
    reader.forEach { event ->
        when (event) {
            START_ELEMENT -> {
                val attributes = buildMap {
                    for (name in listOf("version", "creator", "lat", "lon")) {
                        reader.getAttributeValue(null, name)?.let { put(name, it) }
                    }
                }
                val element = Element(reader.localName, reader.namespaceURI, attributes)
                stack.lastOrNull()?.children?.add(element) ?: run { root = element }
                stack += element
            }
            TEXT, ENTITY_REF, CDSECT -> if (stack.isNotEmpty()) stack.last().text += reader.text
            END_ELEMENT -> stack.removeAt(stack.lastIndex)
            else -> Unit
        }
    }
    return assertNotNull(root)
}

private const val GPX_1_1_NAMESPACE = "http://www.topografix.com/GPX/1/1"
