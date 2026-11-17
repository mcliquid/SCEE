import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.SAXException
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.math.max

class PresetIconUpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class PresetIconUpdateConfig(
    val versionTag: String,
    val targetDir: File,
    val iconSize: Int,
    val nameTransform: (String) -> String,
    /** Optional path to read/write the resolved iD commit for cross-run reproducibility. */
    val idEditorRevisionLockFile: File? = null,
)

data class PresetIconUpdateResult(
    val iconCount: Int,
    val idEditorCommitSha: String,
    val idEditorResolutionLabel: String,
)

fun updatePresetIcons(
    config: PresetIconUpdateConfig,
    fetchText: (String) -> String,
    fetchBytes: (String) -> ByteArray,
    log: (String) -> Unit = ::println,
): PresetIconUpdateResult {
    val catalog = try {
        resolveIdEditorIconCatalog(
            config.versionTag,
            fetchText,
            revisionLockFile = config.idEditorRevisionLockFile,
            log = log,
        )
    } catch (e: Exception) {
        throw PresetIconUpdateException("Could not resolve icon library versions for ${config.versionTag}", e)
    }
    log("iD sprite base commit (all iD-* icons in this run): ${catalog.idEditorCommitSha} [${catalog.resolutionLabel}]")

    val icons = try {
        readIconNamesFromPresetsRelease(config.versionTag, fetchBytes)
    } catch (e: Exception) {
        throw PresetIconUpdateException(
            "Could not read preset icons from id-tagging-schema release ${config.versionTag}",
            e,
        )
    }
    val namePrefix = config.nameTransform("").lowercase()
    val staging = File(config.targetDir.parentFile, "${config.targetDir.name}.staging-icons-${System.nanoTime()}")
    staging.mkdirs()

    val failures = mutableListOf<String>()

    try {
        for (icon in icons.sorted()) {
            val urls = iconDownloadUrls(icon, catalog)
            if (urls == null) {
                failures.add("$icon has unsupported icon prefix")
                continue
            }

            val fileName = config.nameTransform(icon).lowercase()
            val targetFile = staging.resolve("$fileName.xml")

            var iconConverted = false
            var lastError = ""
            for (url in urls) {
                try {
                    val svgBytes = fetchBytes(url)
                    ByteArrayInputStream(svgBytes).use { input ->
                        val svg = parseSvgStream(input)
                        val drawable = createAndroidDrawableFromSvg(svg, config.iconSize)
                        writeVectorDrawableXml(drawable, targetFile)
                    }
                    iconConverted = true
                    break
                } catch (e: SAXException) {
                    lastError = "$icon ($url): XML/SVG parse error: ${e.message}"
                } catch (e: IOException) {
                    lastError = "$icon ($url): download failed: ${e.message}"
                } catch (e: IllegalArgumentException) {
                    lastError = "$icon ($url): not supported: ${e.message}"
                }
            }
            if (!iconConverted) {
                failures.add(lastError.ifEmpty { "$icon: no source succeeded (${urls.joinToString()})" })
            }
        }

        if (failures.isNotEmpty()) {
            log("Preset icon conversion failures (${failures.size}/${icons.size}):")
            failures.forEach { log("  $it") }
            throw PresetIconUpdateException(
                "Incomplete preset icon update (${failures.size} failures). " +
                    "Existing icons in ${config.targetDir} were not modified."
            )
        }

        replacePresetIconDrawables(config.targetDir, staging, namePrefix)
        return PresetIconUpdateResult(icons.size, catalog.idEditorCommitSha, catalog.resolutionLabel)
    } finally {
        staging.deleteRecursively()
    }
}

fun replacePresetIconDrawables(targetDir: File, stagingDir: File, namePrefix: String) {
    val newFiles = stagingDir.listFiles()?.filter { it.isFile }.orEmpty().associateBy { it.name }
    targetDir.mkdirs()
    val oldPresetIcons = targetDir.listFiles { _, name ->
        name.startsWith(namePrefix) && name.endsWith(".xml")
    }.orEmpty()
    for ((name, file) in newFiles) {
        file.copyTo(targetDir.resolve(name), overwrite = true)
    }
    for (old in oldPresetIcons) {
        if (old.name !in newFiles) {
            old.delete()
        }
    }
}

fun readIconNamesFromPresetsRelease(versionTag: String, fetchBytes: (String) -> ByteArray): Set<String> {
    val zipUrl = idTaggingSchemaReleaseZipUrl(versionTag)
    val dist = readIdTaggingSchemaDistFromZip(fetchBytes(zipUrl))
    val presetsBytes = dist["presets.json"]
        ?: throw PresetIconUpdateException("Release archive for $versionTag has no dist/presets.json")
  return readIconNamesFromPresetsJson(presetsBytes.decodeToString())
}

fun readIconNamesFromPresetsJson(presetsJson: String): Set<String> {
    val presetsJsonObject = Parser.default().parse(presetsJson.reader()) as JsonObject
    val icons = HashSet<String>()
    for (value in presetsJsonObject.values) {
        val preset = value as? JsonObject ?: continue
        val icon = preset["icon"] as? String ?: continue
        icons.add(icon)
    }
    return icons
}

fun createAndroidDrawableFromSvg(svg: Document, iconSize: Int): Document {
    val drawable = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()

    var root: Element? = null
    for (i in 0 until svg.childNodes.length) {
        val node = svg.childNodes.item(i)
        if (node is Element) {
            root = node
            break
        }
    }

    require(root != null) { "No root node found" }
    require(root.tagName == "svg") { "Root must be <svg>" }

    var width = root.getAttribute("width")
    var height = root.getAttribute("height")

    val viewBox = root.getAttribute("viewBox")
    if (viewBox.isNotEmpty()) {
        val rect = viewBox.split(' ')

        require(rect.size == 4) { "Expected viewBox to have 4 values" }
        val viewBoxWidth = rect[2]
        val viewBoxHeight = rect[3]
        width = viewBoxWidth
        height = viewBoxHeight
    } else {
        require(width.isNotEmpty()) { "missing width or viewBox" }
        require(height.isNotEmpty()) { "missing height or viewBox" }
    }

    val vector = drawable.createElement("vector")
    vector.setAttribute("xmlns:android", "http://schemas.android.com/apk/res/android")
    val widthF = width.toFloat()
    val heightF = height.toFloat()
    val size = max(widthF, heightF)
    val iconWidth = iconSize * widthF / size
    val iconHeight = iconSize * heightF / size
    vector.setAttribute("android:width", "${iconWidth}dp")
    vector.setAttribute("android:height", "${iconHeight}dp")
    vector.setAttribute("android:viewportWidth", width)
    vector.setAttribute("android:viewportHeight", height)
    drawable.appendChild(vector)

    for (pathElement in collectSvgPathElements(root)) {
        for (a in 0 until pathElement.attributes.length) {
            val attr = pathElement.attributes.item(a) as Attr
            require(attr.name in supportedPathAttributes) { "path attribute '${attr.name}' not supported" }
        }
        val d = pathElement.getAttribute("d")
        require(d != "") { "no path defined" }

        val path = drawable.createElement("path")
        path.setAttribute("android:fillColor", "#000")
        path.setAttribute("android:pathData", makePathCompatible(d))
        vector.appendChild(path)
    }

    return drawable
}

private val supportedPathAttributes = setOf(
    "d",
    "id",
    "fill",
    "fill-opacity",
    "stroke",
    "stroke-width",
    "stroke-dasharray",
    "stroke-opacity",
    "opacity",
)

private fun collectSvgPathElements(root: Element): List<Element> {
    val result = ArrayList<Element>()
    fun walk(node: org.w3c.dom.Node) {
        if (node is Element) {
            when (node.tagName) {
                "defs", "title", "desc", "metadata" -> return
                "path" -> result.add(node)
                else -> {
                    for (i in 0 until node.childNodes.length) {
                        walk(node.childNodes.item(i))
                    }
                }
            }
        }
    }
    for (i in 0 until root.childNodes.length) {
        walk(root.childNodes.item(i))
    }
    require(result.isNotEmpty()) { "No paths found in SVG" }
    return result
}

private fun makePathCompatible(path: String): String {
    val scientificNotation = Regex("\\d*\\.\\d+e-\\d+")
    var result = scientificNotation.replace(path, "0")

    val zeroBeforeDot = Regex("(?<before>[- ,a-zA-Z])\\.")
    result = zeroBeforeDot.replace(result, "\${before}0.")

    val spaceAfterDecimal = Regex("(\\d+\\.\\d+)\\.")
    var i = 0
    var previousPath: String
    do {
        if (i++ > 3) throw IllegalStateException()
        previousPath = result
        result = spaceAfterDecimal.replace(previousPath, "\$1 0.")
    } while (result != previousPath)
    return result
}

private fun writeVectorDrawableXml(xml: Document, targetFile: File) {
    targetFile.parentFile.mkdirs()
    FileOutputStream(targetFile).use { output ->
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.transform(DOMSource(xml), StreamResult(output))
    }
}