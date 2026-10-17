import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.net.URI
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/** Update the traffic sign catalogue of the given countries from a version of the npm package
 *  @osm-traffic-signs/converter (https://github.com/osmberlin/osm-traffic-sign-tool) and convert the
 *  bundled SVGs to Android vector drawable XMLs, which Compose Multiplatform can render on all
 *  platforms (SVG is not supported on Android).
 *
 *  The process is in TrafficSignCatalogueUpdate.kt (tested in buildSrc/src/test): download and verify,
 *  stage, validate, compare with the existing catalogue, write a report. Without -PtrafficSignsApply=true
 *  nothing is written (dry run). A broken catalogue or download changes nothing, a failed write is
 *  rolled back. See app/traffic-signs-update/README.md for the commands.
 *
 *  The catalogue JSON is copied unchanged. The SVGs are converted with Android's own Svg2Vector
 *  (from com.android.tools:sdk-common), which is loaded from [converterClasspath] in an isolated
 *  class loader so that it does not interfere with the Android Gradle plugin. */
open class UpdateTrafficSignsTask : DefaultTask() {
    /** a version like "0.8.0" or "latest" */
    @get:Input lateinit var version: String
    /** SHA-256 of the npm tarball of [version], so that a changed package is not used unnoticed. null:
     *  a new version, only to be looked at (dry run) */
    @get:Input @get:Optional var sha256: String? = null
    @get:Input var countries: List<String> = listOf("DE")
    @get:Input var apply: Boolean = false
    @get:Input var acceptReview: Boolean = false
    /** the osm value parts of the signs of the curated rules, one per line (see TrafficSignRuleCatalogueTest) */
    @get:InputFile lateinit var ruleSignsFile: File
    @get:Classpath lateinit var converterClasspath: FileCollection
    @get:OutputDirectory lateinit var targetDir: File
    @get:Internal lateinit var reportDir: File
    @get:Internal lateinit var backupDir: File
    @get:Internal lateinit var historyDir: File

    init {
        // a manual task with side effects on files in the project: always run when asked
        outputs.upToDateWhen { false }
    }

    @TaskAction fun run() {
        val ruleSigns = ruleSignsFile.readLines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toSet()
        val convert = svg2VectorConverter()
        for (country in countries) {
            val config = TrafficSignUpdateConfig(
                version = version, sha256 = sha256, country = country, targetDir = targetDir,
                backupDir = backupDir, reportDir = reportDir, historyDir = historyDir,
                ruleSigns = ruleSigns, apply = apply, acceptReview = acceptReview,
            )
            try {
                updateTrafficSignCatalogue(
                    config,
                    fetch = { url ->
                        val connection = URI(url).toURL().openConnection()
                        connection.connectTimeout = 30_000
                        connection.readTimeout = 120_000
                        connection.getInputStream().use { it.readBytes() }
                    },
                    convertSvg = { name, svg ->
                        val tmpDir = Files.createTempDirectory("traffic-signs")
                        try {
                            val tmpFile = tmpDir.resolve("$name.svg")
                            Files.write(tmpFile, svg)
                            val output = ByteArrayOutputStream()
                            val errors = convert(tmpFile, output)
                            output.toByteArray() to errors
                        } finally {
                            tmpDir.toFile().deleteRecursively()
                        }
                    },
                    log = { logger.lifecycle(it) },
                )
            } catch (e: TrafficSignUpdateException) {
                throw GradleException(e.message ?: "update failed", e)
            }
        }
    }

    /** returns a function that converts the given SVG file to vector drawable XML written into the
     *  output stream and returns the error log (empty if successful) */
    private fun svg2VectorConverter(): (Path, OutputStream) -> String {
        val classLoader = URLClassLoader(
            converterClasspath.files.map { it.toURI().toURL() }.toTypedArray(),
            ClassLoader.getPlatformClassLoader()
        )
        val svg2Vector = classLoader.loadClass("com.android.ide.common.vectordrawable.Svg2Vector")
        val parseSvgToXml = svg2Vector.getMethod("parseSvgToXml", Path::class.java, OutputStream::class.java)
        return { path, output -> parseSvgToXml.invoke(null, path, output) as String? ?: "" }
    }
}

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

/** Minimal reader for (pax) ustar archives as published by npm. Returns file path to content */
internal fun readTarGz(bytes: ByteArray): Map<String, ByteArray> {
    val tar = GZIPInputStream(ByteArrayInputStream(bytes)).readBytes()
    val result = LinkedHashMap<String, ByteArray>()
    var offset = 0
    var paxPath: String? = null
    while (offset + 512 <= tar.size) {
        val header = tar.copyOfRange(offset, offset + 512)
        if (header.all { it == 0.toByte() }) break
        val name = header.readString(0, 100)
        val size = header.readString(124, 12).trim().ifEmpty { "0" }.toLong(8).toInt()
        val type = header[156].toInt().toChar()
        val prefix = header.readString(345, 155)
        val contentStart = offset + 512
        val content = tar.copyOfRange(contentStart, contentStart + size)
        when (type) {
            'x' -> {
                paxPath = content.decodeToString().lineSequence()
                    .map { it.substringAfter(' ') }
                    .firstOrNull { it.startsWith("path=") }
                    ?.removePrefix("path=")
            }
            '0', '\u0000' -> {
                val path = paxPath ?: if (prefix.isEmpty()) name else "$prefix/$name"
                result[path] = content
                paxPath = null
            }
            else -> paxPath = null
        }
        offset = contentStart + (size + 511) / 512 * 512
    }
    return result
}

private fun ByteArray.readString(start: Int, length: Int): String {
    val end = (start until start + length).firstOrNull { this[it] == 0.toByte() } ?: (start + length)
    return copyOfRange(start, end).decodeToString()
}

private val validKinds = setOf("traffic_sign", "exception_modifier", "condition_modifier")
private val namedValues = setOf(
    "city_limit", "maxspeed", "none", "destination", "yes", "variable", "hazard", "signals",
    "give_way", "stop", "variable_message",
)

/** Name of the image of a sign, same as trafficSignImageName in the app (TrafficSignImages.kt) and
 *  createSvgImportname of the converter. Keep in sync. */
private fun imageName(country: String, osmValuePart: String) =
    "${country}_" + osmValuePart.replace("[", "__").replace("]", "__").replace(Regex("[^a-zA-Z0-9_]"), "_")

/** Strict check of a catalogue JSON, returns the problems with position and id of the sign: required
 *  fields, unknown kind, empty or duplicate ids, redirects to nothing or contradicting each other,
 *  signs that would share an image name, signs without SVG */
internal fun validateTrafficSignCatalogue(json: String, country: String, svgNames: Set<String>): List<String> {
    val problems = ArrayList<String>()
    val root = try {
        kotlinx.serialization.json.Json.parseToJsonElement(json) as? kotlinx.serialization.json.JsonObject
    } catch (e: Exception) {
        null
    } ?: return listOf("not a JSON object")
    fun kotlinx.serialization.json.JsonObject.str(key: String) =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
    if (root.str("country") != country) problems.add("country is not $country")
    if (root.str("version").isNullOrBlank()) problems.add("version is missing")
    val signs = root["signs"] as? kotlinx.serialization.json.JsonArray ?: return problems + "list of signs is missing"

    val ids = HashSet<String>()
    val signIds = HashSet<String>()
    val imageNames = HashMap<String, String>()
    val redirects = ArrayList<Triple<String, String, String>>() // sign, from, to
    signs.forEachIndexed { index, element ->
        val sign = element as? kotlinx.serialization.json.JsonObject
        if (sign == null) { problems.add("#$index: not an object"); return@forEachIndexed }
        val id = sign.str("osmValuePart")
        val label = "#$index ${id ?: ""}".trim()
        if (id.isNullOrBlank()) problems.add("$label: osmValuePart is missing or empty")
        if (sign.str("signId").isNullOrBlank()) problems.add("$label: signId is missing or empty")
        if (sign.str("name") == null) problems.add("$label: name is missing")
        val kind = sign.str("kind")
        if (kind !in validKinds) problems.add("$label: unknown kind $kind")
        val category = (sign["catalogue"] as? kotlinx.serialization.json.JsonObject)?.str("signCategory")
        if (category == null) problems.add("$label: catalogue.signCategory is missing")
        if (sign["tagRecommendationsByGeometry"] == null) problems.add("$label: tagRecommendationsByGeometry is missing")
        if (id != null) {
            if (!ids.add(id)) problems.add("$label: duplicate osmValuePart")
            sign.str("signId")?.let { signIds.add(it) }
            val image = imageName(country, id)
            imageNames.put(image, id)?.let { problems.add("$label: image name $image is also used by $it") }
            if (image !in svgNames) problems.add("$label: no SVG $image")
            (sign["redirects"] as? kotlinx.serialization.json.JsonArray)?.forEach { r ->
                val from = (r as? kotlinx.serialization.json.JsonObject)?.str("from")
                val to = (r as? kotlinx.serialization.json.JsonObject)?.str("to")
                if (from == null || to == null) problems.add("$label: redirect without from/to") else redirects.add(Triple(id, from, to))
            }
        }
    }
    val targets = HashMap<String, String>()
    for ((id, from, to) in redirects) {
        if (to !in ids && to !in signIds && to !in namedValues && to.substringBefore('[') !in signIds) problems.add("$id: redirect \"$from\" leads to \"$to\", which is not in the catalogue")
        val previous = targets.put(from.lowercase(), to)
        if (previous != null && previous != to) problems.add("$id: redirect \"$from\" leads to \"$previous\" and \"$to\"")
    }
    return problems
}
