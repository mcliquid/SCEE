import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import java.io.File
import java.io.StringWriter
import java.util.Locale

class PresetDataUpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class PresetDataUpdateConfig(
    val versionTag: String,
    val languageCodes: Collection<String>,
    val targetDir: File,
)

fun updatePresetData(
    config: PresetDataUpdateConfig,
    fetchBytes: (String) -> ByteArray,
    log: (String) -> Unit = ::println,
) {
    val staging = File(config.targetDir.parentFile, "${config.targetDir.name}.staging-${System.nanoTime()}")
    try {
        staging.mkdirs()
        val zipUrl = idTaggingSchemaReleaseZipUrl(config.versionTag)
        val distFiles = try {
            readIdTaggingSchemaDistFromZip(fetchBytes(zipUrl))
        } catch (e: Exception) {
            throw PresetDataUpdateException("Could not download id-tagging-schema release $zipUrl", e)
        }

        val presetsBytes = distFiles["presets.json"]
            ?: throw PresetDataUpdateException("Release archive for ${config.versionTag} has no dist/presets.json")
        val reducedPresets = reducePresetsJson(presetsBytes.decodeToString())
        File(staging, "presets.json").writeText(reducedPresets)

        val exportLanguages = config.languageCodes.map { Locale.of(Locale.forLanguageTag(it).language) }
        val translationFiles = distFiles.filterKeys { it.startsWith("translations/") && it.endsWith(".json") }
        if (translationFiles.isEmpty()) {
            throw PresetDataUpdateException("Release archive for ${config.versionTag} has no dist/translations/*.json")
        }

        for ((path, bytes) in translationFiles) {
            val filename = path.removePrefix("translations/").removeSuffix(".json")
            val locale = Locale.forLanguageTag(filename.replace('@', '-'))
            val languageLocale = Locale.of(locale.language)
            if (exportLanguages.none { it == languageLocale }) continue

            val javaLanguageTag = locale.toLanguageTag()
            log(javaLanguageTag)
            val content = reducePresetsLocalizationJson(bytes.decodeToString())
            File(staging, "$javaLanguageTag.json").writeText(content)
        }

        if ("no" in config.languageCodes) {
            val noFile = staging.resolve("no.json")
            if (noFile.exists()) {
                noFile.copyTo(staging.resolve("nb.json"), overwrite = true)
            }
        }

        if (!staging.resolve("presets.json").exists()) {
            throw PresetDataUpdateException("Staging is missing presets.json after processing")
        }

        replaceFlatDirectoryContents(config.targetDir, staging)
    } catch (e: PresetDataUpdateException) {
        throw e
    } catch (e: Exception) {
        throw PresetDataUpdateException("Preset update failed, existing files in ${config.targetDir} were left unchanged", e)
    } finally {
        staging.deleteRecursively()
    }
}

/** Replace all files in [target] with files from [staging]; both directories are flat (no sub-folders). */
fun replaceFlatDirectoryContents(target: File, staging: File) {
    target.mkdirs()
    val staged = staging.listFiles()?.filter { it.isFile }.orEmpty().associateBy { it.name }
    target.listFiles()?.forEach { existing ->
        if (existing.isFile && existing.name !in staged) {
            existing.delete()
        }
    }
    for ((name, file) in staged) {
        file.copyTo(target.resolve(name), overwrite = true)
    }
}

internal fun reducePresetsJson(presetsJson: String): String {
    val json = Parser.default().parse(presetsJson.reader()) as JsonObject
    json.entries.removeAll { (key, value) ->
        val include = (value as JsonObject).obj("locationSet")?.array<String>("include")
        val includesOnlyPlanet = include != null && include.size == 1 &&
            (include.single() == "Planet" || include.single() == "001")

        key.startsWith("@templates")
            || include != null && !includesOnlyPlanet
            || key.startsWith("disused/")
    }
    for (value in json.values) {
        val preset = value as JsonObject
        preset.remove("fields")
        preset.remove("moreFields")
        preset.remove("reference")
        preset.remove("locationSet")
    }
    return json.toJsonString(prettyPrint = true)
}

internal fun reducePresetsLocalizationJson(localizationJson: String): String {
    val json = Parser.default().parse(localizationJson.reader()) as JsonObject
    for (value in json.values) {
        val language = value as JsonObject
        val presets = language.obj("presets")
        presets?.remove("categories")
        presets?.remove("fields")
    }
    return json.toJsonString(prettyPrint = true).unescapeUnicode()
}

private fun String.unescapeUnicode(): String {
    val out = StringWriter(length)
    val unicode = StringBuilder(4)
    var hadSlash = false
    var inUnicode = false

    for (ch in this) {
        if (inUnicode) {
            unicode.append(ch)
            if (unicode.length == 4) {
                val unicodeChar = unicode.toString().toInt(16).toChar()
                out.write(unicodeChar.toString())
                unicode.setLength(0)
                inUnicode = false
                hadSlash = false
            }
        } else if (hadSlash) {
            hadSlash = false
            if (ch == 'u') {
                inUnicode = true
            } else {
                out.write(92)
                out.write(ch.toString())
            }
        } else if (ch == '\\') {
            hadSlash = true
        } else {
            out.write(ch.toString())
        }
    }

    if (hadSlash) out.write(92)
    return out.toString()
}