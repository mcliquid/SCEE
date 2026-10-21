import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import org.w3c.dom.Element
import java.io.File
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/** Flatten the existing layer lists; preserve path data and normalize square viewport scales. */
abstract class GenerateCrossingVectors : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction fun generate() {
        val sources = sourceFiles.files.associateBy { it.nameWithoutExtension }
        val output = outputDirectory.dir("files/crossing").get().asFile.apply { mkdirs() }
        for ((name, file) in sources.toSortedMap()) {
            if (crossingXml(file).tagName == "layer-list") {
                File(output, "$name.xml").writeText(flattenCrossingVector(file, sources), Charsets.UTF_8)
            }
        }
    }
}

private const val ANDROID_XML = "http://schemas.android.com/apk/res/android"

internal fun crossingXml(file: File): Element {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }
    return factory.newDocumentBuilder().parse(file).documentElement
}

internal fun flattenCrossingVector(file: File, sources: Map<String, File>): String {
    val layers = crossingXml(file)
    require(layers.tagName == "layer-list")
    fun children(element: Element) = (0 until element.childNodes.length)
        .mapNotNull { element.childNodes.item(it) as? Element }
    val vectors = children(layers).map { item ->
        require(item.tagName == "item")
        val reference = item.getAttributeNS(ANDROID_XML, "drawable")
        if (reference.isEmpty()) {
            require(item.attributes.length == 0) { "Unsupported layer placement" }
            children(item).single()
        } else {
            require(item.attributes.length == 1 && reference.startsWith("@drawable/")) { "Unsupported layer placement" }
            crossingXml(requireNotNull(sources[reference.removePrefix("@drawable/")]) { "Missing crossing layer: $reference" })
        }
    }
    require(vectors.isNotEmpty())
    val dimensions = listOf("width", "height", "viewportWidth", "viewportHeight")
    val first = vectors.first()
    for (vector in vectors) {
        require(vector.tagName == "vector") { "Expected vector layer" }
        require(vector.getAttributeNS(ANDROID_XML, "viewportWidth") == vector.getAttributeNS(ANDROID_XML, "viewportHeight")
            && vector.getAttributeNS(ANDROID_XML, "width") == vector.getAttributeNS(ANDROID_XML, "height")) { "Expected square crossing layer" }
        for (i in 0 until vector.attributes.length) {
            val attribute = vector.attributes.item(i)
            require(attribute.nodeName.startsWith("xmlns:") || attribute.namespaceURI == "http://schemas.android.com/tools"
                || attribute.localName in dimensions) { "Unsupported vector attribute: ${attribute.nodeName}" }
        }
    }
    val document = first.ownerDocument
    val result = first.cloneNode(false) as Element
    for (vector in vectors) {
        val scale = first.getAttributeNS(ANDROID_XML, "viewportWidth").toFloat() / vector.getAttributeNS(ANDROID_XML, "viewportWidth").toFloat()
        val parent = if (scale == 1f) result else document.createElement("group").also {
            it.setAttributeNS(ANDROID_XML, "android:scaleX", scale.toString())
            it.setAttributeNS(ANDROID_XML, "android:scaleY", scale.toString())
            result.appendChild(it)
        }
        for (child in children(vector)) {
            require(child.tagName in listOf("path", "group", "clip-path")) { "Unsupported vector child" }
            parent.appendChild(document.importNode(child, true))
        }
    }
    val writer = StringWriter()
    TransformerFactory.newInstance().newTransformer().transform(DOMSource(result), StreamResult(writer))
    return writer.toString()
}
