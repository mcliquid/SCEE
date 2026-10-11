import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URI

/** Download the name suggestion index (in its own format) and split it by the countries the brands
 *  are in: Instead of one big file, sort those brands that only exist in certain countries into own
 *  files (presets-DE.json etc.).
 *
 *  The items themselves are copied as they are, i.e. the osmfeatures library interprets the
 *  name suggestion index data (location sets, dissolved brands, ...) itself. The files are only
 *  split to not load more than necessary: The name suggestion index JSON is so big (12MB and
 *  growing) that it really slows down startup time. Many brand presets do not actually need to be
 *  loaded at all because they exist only in select countries and the user will only be editing
 *  usually in one country per session anyways. */
open class UpdateNsiPresetsTask : DefaultTask() {
    @get:OutputDirectory lateinit var targetDir: File
    @get:Input lateinit var version: String

    @TaskAction
    fun run() {
        // create / clear target directory
        targetDir.mkdirs()
        targetDir.listFiles()?.forEach { it.delete() }

        val baseUrl = "https://cdn.jsdelivr.net/npm/name-suggestion-index@$version/dist"
        val dissolvedUrl = URI("$baseUrl/wikidata/dissolved.min.json").toURL()
        File(targetDir, "dissolved.json").writeBytes(dissolvedUrl.readBytes())

        val nsiUrl = URI("$baseUrl/json/nsi.min.json").toURL()
        val nsi = (Parser.default().parse(nsiUrl.openStream()) as JsonObject).obj("nsi")!!

        // sort into separate files, each in the same format as nsi.json's "nsi" object
        val byCountryMap = mutableMapOf<String?, JsonObject>()
        for ((path, category) in nsi) {
            val preserveTags = (category as JsonObject).obj("properties")?.array<String>("preserveTags")
            for (item in category.array<JsonObject>("items")!!) {
                for (country in item.shardCountries()) {
                    val shardCategory = byCountryMap.getOrPut(country) { JsonObject() }.getOrPut(path) {
                        JsonObject().apply {
                            if (preserveTags != null) set("properties", JsonObject(mapOf("preserveTags" to preserveTags)))
                            set("items", JsonArray<JsonObject>())
                        }
                    } as JsonObject
                    shardCategory.array<JsonObject>("items")!!.add(item)
                }
            }
        }

        for ((country, jsonObject) in byCountryMap.entries) {
            val name = "presets${ if (country != null) "-${country.uppercase()}" else "" }.json"
            File(targetDir, name).writeText(jsonObject.toJsonString())
        }
    }
}

/** The countries whose file the item belongs to, null for the file with the brands that are loaded
 *  for every country.
 *
 *  Only items that are included in just plain countries and subdivisions go into the files of
 *  these countries. All other items (included everywhere, M49 regions, GeoJSON files, circles,
 *  ...) go into the file that is always loaded. Items that osmfeatures cannot interpret don't hurt
 *  there, as they are dropped anyway.
 *
 *  The pattern must match what the osmfeatures NsiJsonParser takes as country or subdivision. */
private fun JsonObject.shardCountries(): Set<String?> {
    val include = obj("locationSet")?.array<Any?>("include").orEmpty()
    val countries = include.map { (it as? String)?.let { code -> COUNTRY_OR_SUBDIVISION.matchEntire(code)?.groupValues?.get(1) } }
    return if (countries.isEmpty() || countries.any { it == null }) setOf(null) else countries.toSet()
}

private val COUNTRY_OR_SUBDIVISION = Regex("([a-z]{2})(-[a-z0-9]{1,3}(\\.geojson)?)?")
