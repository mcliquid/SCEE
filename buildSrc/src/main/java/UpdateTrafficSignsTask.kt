import org.gradle.api.DefaultTask
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

/** Download the traffic sign catalogue of the given countries from the pinned version of the npm
 *  package @osm-traffic-signs/converter (https://github.com/osmberlin/osm-traffic-sign-tool) and
 *  convert the bundled SVGs to Android vector drawable XMLs, which Compose Multiplatform can render
 *  on all platforms (SVG is not supported on Android).
 *
 *  The catalogue JSON is copied unchanged. The SVGs are converted with Android's own Svg2Vector
 *  (from com.android.tools:sdk-common), which is loaded from [converterClasspath] in an isolated
 *  class loader so that it does not interfere with the Android Gradle plugin. */
open class UpdateTrafficSignsTask : DefaultTask() {
    @get:Input lateinit var version: String
    /** SHA-256 of the npm tarball of [version], so that a changed package is not used unnoticed */
    @get:Input lateinit var sha256: String
    @get:Input var countries: List<String> = listOf("DE")
    @get:Classpath lateinit var converterClasspath: FileCollection
    @get:OutputDirectory lateinit var targetDir: File

    @TaskAction fun run() {
        val url = "https://registry.npmjs.org/@osm-traffic-signs/converter/-/converter-$version.tgz"
        val tarball = URI(url).toURL().readBytes()
        val actualSha256 = tarball.sha256()
        check(actualSha256 == sha256) {
            "SHA-256 of $url is $actualSha256 but expected $sha256"
        }
        val files = readTarGz(tarball)

        val convert = svg2VectorConverter()

        for (country in countries) {
            val json = files["package/dist/data/$country.json"]
                ?: error("$country.json not found in package")
            check(json.decodeToString().contains("\"version\": \"$version\"")) {
                "$country.json is not of version $version"
            }
            targetDir.resolve("$country.json").writeBytes(json)

            val svgPrefix = "package/dist/data-svgs/$country/svgs/"
            val vectorDir = targetDir.resolve(country)
            vectorDir.mkdirs()
            vectorDir.listFiles { f -> f.extension == "xml" }?.forEach { it.delete() }

            val failed = ArrayList<String>()
            var converted = 0
            val tmpDir = Files.createTempDirectory("traffic-signs")
            for ((path, svg) in files) {
                if (!path.startsWith(svgPrefix) || !path.endsWith(".svg")) continue
                val name = path.removePrefix(svgPrefix).removeSuffix(".svg")
                val tmpFile = tmpDir.resolve("$name.svg")
                Files.write(tmpFile, svg)
                val output = ByteArrayOutputStream()
                val errors = convert(tmpFile, output)
                Files.delete(tmpFile)
                if (output.size() == 0 || errors.isNotBlank()) {
                    failed.add("$name: ${errors.replace('\n', ' ')}")
                    continue
                }
                vectorDir.resolve("$name.xml").writeBytes(output.toByteArray())
                converted++
            }
            Files.delete(tmpDir)
            println("$country: converted $converted SVGs, ${failed.size} not convertible")
            failed.forEach { println("  $it") }
        }

        targetDir.resolve("VERSION").writeText(
            "@osm-traffic-signs/converter@$version\n" +
            "sha256 $sha256\n" +
            "source $url\n" +
            "licence of catalogue data: AGPL-3.0-or-later (https://github.com/osmberlin/osm-traffic-sign-tool)\n" +
            "licence of sign images: as stated per sign in the catalogue (\"Public Domain\" for DE)\n"
        )
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
private fun readTarGz(bytes: ByteArray): Map<String, ByteArray> {
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
