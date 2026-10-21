import java.io.File
import java.nio.file.Files
import kotlin.test.*

class GenerateCrossingVectorsTest {
    private val namespace = "http://schemas.android.com/apk/res/android"
    private val vector = """<vector xmlns:android="$namespace" android:width="128dp" android:height="128dp" android:viewportWidth="600" android:viewportHeight="600"><path android:fillColor="#80ff0000" android:pathData="M0,0h10v10z"/></vector>"""

    @Test fun `actual catalogue flattens without changing any path or layer order`() {
        val root = File(System.getProperty("scee.project.root"), "app/src/commonMain/composeResources/drawable")
        val sources = root.listFiles()!!.filter { it.name.startsWith("crossing_markings") }.associateBy { it.nameWithoutExtension }
        val layers = sources.values.filter { crossingXml(it).tagName == "layer-list" }
        assertEquals(14, layers.size)
        for (file in layers) {
            val output = fixture(flattenCrossingVector(file, sources))
            val result = crossingXml(output)
            assertEquals("vector", result.tagName)
            assertEquals("600", result.getAttributeNS(namespace, "viewportWidth"))
            assertTrue(result.getElementsByTagName("path").length >= 3)
            assertEquals(flattenCrossingVector(file, sources), flattenCrossingVector(file, sources))
        }
    }

    @Test fun `inline vectors preserve alpha and exact path data`() {
        val file = fixture("""<layer-list xmlns:android="$namespace"><item>$vector</item></layer-list>""")
        val result = crossingXml(fixture(flattenCrossingVector(file, emptyMap())))
        val path = result.getElementsByTagName("path").item(0) as org.w3c.dom.Element
        assertEquals("#80ff0000", path.getAttributeNS(namespace, "fillColor"))
        assertEquals("M0,0h10v10z", path.getAttributeNS(namespace, "pathData"))
    }

    @Test fun `missing references fail rather than generating incomplete images`() {
        assertFails { flattenCrossingVector(fixture("""<layer-list xmlns:android="$namespace"><item android:drawable="@drawable/missing"/></layer-list>"""), emptyMap()) }
    }

    @Test fun `layer placement requires explicit future support`() {
        assertFails { flattenCrossingVector(fixture("""<layer-list xmlns:android="$namespace"><item android:left="2dp">$vector</item></layer-list>"""), emptyMap()) }
    }

    @Test fun `non-square layers fail rather than distorting silently`() {
        val skewed = vector.replace("viewportWidth=\"600\"", "viewportWidth=\"300\"")
        assertFails { flattenCrossingVector(fixture("""<layer-list xmlns:android="$namespace"><item>$skewed</item></layer-list>"""), emptyMap()) }
    }

    @Test fun `layers with a smaller square viewport are scaled to the first layer`() {
        val second = vector.replace("viewportWidth=\"600\" android:viewportHeight=\"600\"", "viewportWidth=\"300\" android:viewportHeight=\"300\"")
        val file = fixture("""<layer-list xmlns:android="$namespace"><item>$vector</item><item>$second</item></layer-list>""")
        val result = crossingXml(fixture(flattenCrossingVector(file, emptyMap())))
        val group = result.getElementsByTagName("group").item(0) as org.w3c.dom.Element
        assertEquals("2.0", group.getAttributeNS(namespace, "scaleX"))
        assertEquals("2.0", group.getAttributeNS(namespace, "scaleY"))
        assertEquals(2, result.getElementsByTagName("path").length)
        assertEquals("600", result.getAttributeNS(namespace, "viewportWidth"))
    }

    private fun fixture(xml: String): File = File(Files.createTempDirectory("crossing-vector-test").toFile(), "input.xml").apply { writeText(xml) }
}
