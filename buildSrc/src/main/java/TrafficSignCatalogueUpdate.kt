import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/* The update process of the traffic sign catalogue, without Gradle types so that it can be tested
 * (buildSrc/src/test). UpdateTrafficSignsTask is only the wiring.
 *
 * Steps: fetch + verify the npm tarball -> read it -> plan (stage + validate + diff, nothing written)
 * -> only if asked and the plan has no problems (and, for changes that need a look, the review was
 * accepted): apply, with a backup that is restored if anything fails. A failed fetch, a broken
 * catalogue or a failed write leaves the existing catalogue as it was. */

const val TRAFFIC_SIGNS_PACKAGE = "@osm-traffic-signs/converter"
const val TRAFFIC_SIGNS_REGISTRY = "https://registry.npmjs.org/$TRAFFIC_SIGNS_PACKAGE"

class TrafficSignUpdateConfig(
    /** a version like "0.8.0", or "latest" */
    val version: String,
    /** SHA-256 of the npm tarball. Required to apply. If null, the tarball is verified against the
     *  integrity (sha512) of the registry and the SHA-256 to pin is printed */
    val sha256: String?,
    val country: String,
    val targetDir: File,
    val backupDir: File,
    val reportDir: File,
    /** where the history of applied updates is written (one file per update) */
    val historyDir: File?,
    /** osm value parts of the signs the curated rules for tags (WAY_RULES) are made of */
    val ruleSigns: Set<String>,
    /** write the update. false: only plan and report */
    val apply: Boolean,
    /** accept changes that need a manual look (see [CatalogueDiff.reviewReasons]) */
    val acceptReview: Boolean,
)

class TrafficSignUpdateResult(
    val version: String,
    val sha256: String,
    val plan: CatalogueUpdatePlan,
    val applied: Boolean,
    val reportFile: File,
)

class TrafficSignUpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** [fetch]: bytes of the URL, throws if not possible. [convertSvg]: svg file name (without
 *  extension) and its bytes -> vector drawable xml bytes, or null and the error */
fun updateTrafficSignCatalogue(
    config: TrafficSignUpdateConfig,
    fetch: (url: String) -> ByteArray,
    convertSvg: (name: String, svg: ByteArray) -> Pair<ByteArray?, String>,
    log: (String) -> Unit = ::println,
): TrafficSignUpdateResult {
    // 1. resolve the version and fetch: nothing is touched if this fails
    val metadata = try {
        parseJson(fetch("$TRAFFIC_SIGNS_REGISTRY/${config.version}").decodeToString())
    } catch (e: Exception) {
        throw TrafficSignUpdateException("Could not read the registry data of $TRAFFIC_SIGNS_PACKAGE@${config.version}: ${e.message}", e)
    }
    val version = (metadata["version"] as? JsonPrimitive)?.content
        ?: throw TrafficSignUpdateException("The registry data has no version")
    val integrity = ((metadata["dist"] as? JsonObject)?.get("integrity") as? JsonPrimitive)?.content
    val url = "$TRAFFIC_SIGNS_REGISTRY/-/converter-$version.tgz"
    val tarball = try {
        fetch(url)
    } catch (e: Exception) {
        throw TrafficSignUpdateException("Download of $url failed, the existing catalogue is unchanged: ${e.message}", e)
    }
    val actualSha256 = sha256Hex(tarball)
    if (config.sha256 != null) {
        if (actualSha256 != config.sha256) throw TrafficSignUpdateException("SHA-256 of $url is $actualSha256 but expected ${config.sha256}")
    } else {
        // no pinned hash (a new version): the registry's own integrity must match
        val expected = integrity?.takeIf { it.startsWith("sha512-") }?.removePrefix("sha512-")
            ?: throw TrafficSignUpdateException("No pinned SHA-256 and no sha512 integrity in the registry data")
        val actual = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-512").digest(tarball))
        if (actual != expected) throw TrafficSignUpdateException("The tarball does not match the integrity of the registry")
        log("Pin this version with -PtrafficSignsSha256=$actualSha256")
    }
    val packageFiles = try {
        readTarGz(tarball)
    } catch (e: Exception) {
        throw TrafficSignUpdateException("The tarball can not be read: ${e.message}", e)
    }

    // 2. plan: stage, validate and compare. Nothing is written.
    val existing = readCatalogueFiles(config.targetDir, config.country)
    val plan = planCatalogueUpdate(config, version, actualSha256, packageFiles, existing, convertSvg)
    config.reportDir.mkdirs()
    val reportFile = File(config.reportDir, "traffic-signs-update-$version.md")
    reportFile.writeText(plan.report(version, actualSha256, config))
    log(plan.summary(version))
    log("Report: $reportFile")

    if (plan.problems.isNotEmpty()) {
        throw TrafficSignUpdateException("The catalogue is not valid, nothing was changed:\n" + plan.problems.joinToString("\n") { "  $it" })
    }
    if (!config.apply) {
        log("Dry run: nothing was changed. Use -PtrafficSignsApply=true (and -PtrafficSignsSha256=$actualSha256) to apply.")
        return TrafficSignUpdateResult(version, actualSha256, plan, false, reportFile)
    }
    if (config.sha256 == null) {
        throw TrafficSignUpdateException("To apply, give the SHA-256 that was printed: -PtrafficSignsSha256=$actualSha256")
    }
    if (plan.diff.reviewReasons.isNotEmpty() && !config.acceptReview) {
        throw TrafficSignUpdateException(
            "These changes need a manual review, nothing was changed (accept with -PtrafficSignsAcceptReview=true after reading the report):\n" +
                plan.diff.reviewReasons.joinToString("\n") { "  $it" }
        )
    }
    if (plan.identical) {
        log("The catalogue is already $version, nothing to do.")
        return TrafficSignUpdateResult(version, actualSha256, plan, false, reportFile)
    }

    // 3. apply with backup
    applyCatalogueUpdate(config.targetDir, plan, config.backupDir)
    config.historyDir?.let {
        it.mkdirs()
        File(it, "${plan.oldVersion ?: "none"}-to-$version.md").writeText(plan.report(version, actualSha256, config))
    }
    log("Applied. Backup of the previous files: ${config.backupDir}. Now run the checks listed in the report.")
    return TrafficSignUpdateResult(version, actualSha256, plan, true, reportFile)
}

/* ------------------------------------------------ plan ------------------------------------------------ */

class CatalogueUpdatePlan(
    /** relative path (DE.json, DE/<name>.xml, VERSION) -> content of everything the catalogue consists of */
    val newFiles: Map<String, ByteArray>,
    /** same for the existing catalogue */
    val oldFiles: Map<String, ByteArray>,
    val oldVersion: String?,
    val diff: CatalogueDiff,
    val problems: List<String>,
) {
    val addedFiles get() = newFiles.keys.filter { it !in oldFiles }
    val removedFiles get() = oldFiles.keys.filter { it !in newFiles }
    val changedFiles get() = newFiles.keys.filter { it in oldFiles && !oldFiles.getValue(it).contentEquals(newFiles.getValue(it)) }
    val identical get() = addedFiles.isEmpty() && removedFiles.isEmpty() && changedFiles.isEmpty()

    fun summary(version: String) = buildString {
        append("Traffic sign catalogue ${oldVersion ?: "(none)"} -> $version: ")
        append("${diff.added.size} signs added, ${diff.removed.size} removed, ${diff.changed.size} changed, ${diff.imagesChanged.size} images changed")
        if (identical) append(" (identical to the existing files)")
        if (diff.reviewReasons.isNotEmpty()) append("; ${diff.reviewReasons.size} reasons for a manual review")
        if (problems.isNotEmpty()) append("; ${problems.size} PROBLEMS")
    }

    fun report(version: String, sha256: String, config: TrafficSignUpdateConfig) = buildString {
        appendLine("# Traffic sign catalogue update ${oldVersion ?: "(none)"} -> $version")
        appendLine()
        appendLine("- source: $TRAFFIC_SIGNS_REGISTRY/-/converter-$version.tgz")
        appendLine("- sha256: $sha256")
        appendLine("- country: ${config.country}")
        appendLine("- files: ${addedFiles.size} added, ${changedFiles.size} changed, ${removedFiles.size} removed")
        appendLine()
        if (problems.isNotEmpty()) {
            appendLine("## Problems (the update is not applied)")
            problems.forEach { appendLine("- $it") }
            appendLine()
        }
        if (diff.reviewReasons.isNotEmpty()) {
            appendLine("## Needs a manual review")
            diff.reviewReasons.forEach { appendLine("- $it") }
            appendLine()
        }
        appendLine("## Signs")
        appendLine("- added (${diff.added.size}): ${diff.added.joinToString().ifEmpty { "-" }}")
        appendLine("- removed (${diff.removed.size}): ${diff.removed.joinToString().ifEmpty { "-" }}")
        appendLine("- changed (${diff.changed.size}):")
        diff.changed.forEach { (sign, fields) -> appendLine("  - $sign: ${fields.joinToString()}") }
        appendLine("- images changed (${diff.imagesChanged.size}): ${diff.imagesChanged.take(50).joinToString().ifEmpty { "-" }}")
        appendLine()
        appendLine("## Structure of the entries")
        appendLine("- fields added: ${diff.addedFields.joinToString().ifEmpty { "-" }}")
        appendLine("- fields removed: ${diff.removedFields.joinToString().ifEmpty { "-" }}")
        appendLine()
        appendLine("## Curated rules (WAY_RULES)")
        appendLine("Signs of the rules: ${config.ruleSigns.sorted().joinToString()}")
        appendLine(if (diff.ruleChanges.isEmpty()) "- none of them changed" else diff.ruleChanges.joinToString("\n") { "- $it" })
        appendLine()
        appendLine("## After applying")
        appendLine("1. ./gradlew :app:testAndroidHostTest (catalogue tests, rule check, parity)")
        appendLine("2. update the expected values in TrafficSignCatalogueTest (version, number of signs) after reading this report")
        appendLine("3. converter parity: cd tools/traffic-sign-parity; npm install --save-exact $TRAFFIC_SIGNS_PACKAGE@$version; node reference.mjs; read the differences before committing reference.jsonl")
    }
}

class CatalogueDiff(
    val added: List<String>,
    val removed: List<String>,
    /** osm value part -> names of the fields that changed */
    val changed: Map<String, List<String>>,
    val imagesChanged: List<String>,
    val addedFields: Set<String>,
    val removedFields: Set<String>,
    /** changes of the signs the rules are made of, and of redirects that touch them */
    val ruleChanges: List<String>,
) {
    /** changes that must not be taken over without a look: they could change what the curated tag rules
     *  do or what existing values mean */
    val reviewReasons: List<String> get() = buildList {
        if (ruleChanges.isNotEmpty()) addAll(ruleChanges.map { "rule sign: $it" })
        if (addedFields.isNotEmpty() || removedFields.isNotEmpty()) add("the structure of the entries changed (fields +${addedFields} -${removedFields})")
        if (removed.isNotEmpty()) add("${removed.size} signs were removed: existing values with them stay as unknown signs (${removed.take(10).joinToString()})")
        changed.filterValues { f -> f.any { it == "kind" || it == "signId" || it == "redirects" || it == "redirectDashValues" || it == "signValue" } }
            .forEach { (sign, fields) -> add("$sign changed ${fields.filter { it == "kind" || it == "signId" || it == "redirects" || it == "redirectDashValues" || it == "signValue" }}: this can change how values are read") }
    }
}

fun planCatalogueUpdate(
    config: TrafficSignUpdateConfig,
    version: String,
    sha256: String,
    packageFiles: Map<String, ByteArray>,
    existing: Map<String, ByteArray>,
    convertSvg: (name: String, svg: ByteArray) -> Pair<ByteArray?, String>,
): CatalogueUpdatePlan {
    val country = config.country
    val problems = ArrayList<String>()
    val json = packageFiles["package/dist/data/$country.json"]
    if (json == null) {
        return CatalogueUpdatePlan(emptyMap(), existing, existingVersion(existing), emptyDiff(), listOf("$country.json is not in the package"))
    }
    val text = json.decodeToString()
    val rootVersion = try {
        ((parseJson(text))["version"] as? JsonPrimitive)?.content
    } catch (e: Exception) {
        null
    }
    if (rootVersion != version) problems.add("$country.json is of version $rootVersion, not $version")

    val svgPrefix = "package/dist/data-svgs/$country/svgs/"
    val svgs = packageFiles.filterKeys { it.startsWith(svgPrefix) && it.endsWith(".svg") }
        .mapKeys { it.key.removePrefix(svgPrefix).removeSuffix(".svg") }
    problems.addAll(validateTrafficSignCatalogue(text, country, svgs.keys))

    val files = LinkedHashMap<String, ByteArray>()
    files["$country.json"] = json
    for ((name, svg) in svgs.toSortedMap()) {
        val (xml, error) = try {
            convertSvg(name, svg)
        } catch (e: Exception) {
            null to e.toString()
        }
        if (xml == null || xml.isEmpty() || error.isNotBlank()) problems.add("SVG $name: ${error.replace('\n', ' ').ifBlank { "no output" }}") else files["$country/$name.xml"] = xml
    }
    files["VERSION"] = versionFileText(version, sha256).encodeToByteArray()

    val oldJson = existing["$country.json"]?.decodeToString()
    val diff = try {
        diffCatalogues(oldJson, text, config.ruleSigns, existing, files, country)
    } catch (e: Exception) {
        problems.add("The catalogues can not be compared: $e")
        emptyDiff()
    }
    return CatalogueUpdatePlan(files, existing, existingVersion(existing), diff, problems)
}

private fun existingVersion(existing: Map<String, ByteArray>): String? =
    existing["VERSION"]?.decodeToString()?.lineSequence()?.firstOrNull()?.substringAfter('@', "")?.substringAfter('@', "")?.ifBlank { null }

fun versionFileText(version: String, sha256: String) =
    "$TRAFFIC_SIGNS_PACKAGE@$version\n" +
        "sha256 $sha256\n" +
        "source $TRAFFIC_SIGNS_REGISTRY/-/converter-$version.tgz\n" +
        "licence of catalogue data: AGPL-3.0-or-later (https://github.com/osmberlin/osm-traffic-sign-tool)\n" +
        "licence of sign images: as stated per sign in the catalogue (\"Public Domain\" for DE)\n"

private fun emptyDiff() = CatalogueDiff(emptyList(), emptyList(), emptyMap(), emptyList(), emptySet(), emptySet(), emptyList())

/* ------------------------------------------------ diff ------------------------------------------------ */

private val json = Json

internal fun parseJson(text: String): JsonObject =
    json.parseToJsonElement(text) as? JsonObject ?: throw IllegalArgumentException("not a JSON object")

private fun signsOf(root: JsonObject): Map<String, JsonObject> =
    (root["signs"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        .mapNotNull { s -> (s["osmValuePart"] as? JsonPrimitive)?.content?.let { it to s } }.toMap()

/** The fields of the signs that decide what a sign means for the rules and the reading of values */
private val RULE_RELEVANT_FIELDS = listOf("kind", "signId", "tagRecommendationsByGeometry", "compatibility", "redirects", "redirectDashValues", "signValue", "valuePrompt")

fun diffCatalogues(
    oldJson: String?,
    newJson: String,
    ruleSigns: Set<String>,
    oldFiles: Map<String, ByteArray>,
    newFiles: Map<String, ByteArray>,
    country: String,
): CatalogueDiff {
    val new = signsOf(parseJson(newJson))
    val old = oldJson?.let { signsOf(parseJson(it)) } ?: emptyMap()
    val added = new.keys.filter { it !in old }.sorted()
    val removed = old.keys.filter { it !in new }.sorted()
    val changed = LinkedHashMap<String, List<String>>()
    for ((id, sign) in new) {
        val before = old[id] ?: continue
        val fields = (sign.keys + before.keys).filter { sign[it] != before[it] }.sorted()
        if (fields.isNotEmpty()) changed[id] = fields
    }
    val imagesChanged = (newFiles.keys + oldFiles.keys).filter { it.startsWith("$country/") }
        .filter { !(oldFiles[it]?.contentEquals(newFiles[it] ?: ByteArray(0)) ?: false) || it !in oldFiles || it !in newFiles }
        .map { it.removePrefix("$country/").removeSuffix(".xml") }.sorted()
    val newFields = new.values.flatMapTo(sortedSetOf()) { it.keys }
    val oldFields = old.values.flatMapTo(sortedSetOf()) { it.keys }
    val addedFields = if (oldJson == null) emptySet() else newFields - oldFields
    val removedFields = if (oldJson == null) emptySet() else oldFields - newFields

    val ruleChanges = ArrayList<String>()
    val ruleSignIds = ruleSigns.mapTo(HashSet()) { it.substringBefore('[') }
    for (id in ruleSigns.sorted()) {
        val n = new[id]
        val o = old[id]
        when {
            oldJson == null -> {}
            n == null -> ruleChanges.add("$id is not in the new catalogue")
            o == null -> ruleChanges.add("$id was not in the old catalogue")
            else -> for (f in RULE_RELEVANT_FIELDS) if (n[f] != o[f]) ruleChanges.add("$id: $f changed")
        }
    }
    // redirects that start or end at a sign of the rules (or use a sign id of them) change which sign a value is
    fun redirects(signs: Map<String, JsonObject>) = signs.values.flatMap { s ->
        (s["redirects"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map {
            Triple((it["from"] as? JsonPrimitive)?.content.orEmpty(), (it["to"] as? JsonPrimitive)?.content.orEmpty(), (s["osmValuePart"] as? JsonPrimitive)?.content.orEmpty())
        }
    }.toSet()
    if (oldJson != null) {
        val touching = { r: Triple<String, String, String> -> r.first in ruleSigns || r.second in ruleSigns || r.first.substringBefore('[') in ruleSignIds || r.second.substringBefore('[') in ruleSignIds }
        val before = redirects(old).filter(touching).toSet()
        val after = redirects(new).filter(touching).toSet()
        (after - before).forEach { ruleChanges.add("redirect added: \"${it.first}\" -> \"${it.second}\" (at ${it.third})") }
        (before - after).forEach { ruleChanges.add("redirect removed: \"${it.first}\" -> \"${it.second}\" (at ${it.third})") }
    }
    return CatalogueDiff(added, removed, changed, imagesChanged, addedFields, removedFields, ruleChanges)
}

/* ------------------------------------------------ files ------------------------------------------------ */

/** Everything the catalogue consists of in [dir]: DE.json, VERSION and the DE/<name>.xml images */
fun readCatalogueFiles(dir: File, country: String): Map<String, ByteArray> {
    val result = LinkedHashMap<String, ByteArray>()
    listOf("$country.json", "VERSION").forEach { name -> File(dir, name).takeIf { it.isFile }?.let { result[name] = it.readBytes() } }
    File(dir, country).listFiles { f -> f.isFile && f.extension == "xml" }?.sortedBy { it.name }?.forEach { result["$country/${it.name}"] = it.readBytes() }
    return result
}

/** Writes only what differs and removes what is gone. The previous files are copied to [backupDir]
 *  first; if anything fails the previous state is restored and the exception is thrown again.
 *  [beforeWrite] is called before every file (for tests). */
fun applyCatalogueUpdate(targetDir: File, plan: CatalogueUpdatePlan, backupDir: File, beforeWrite: (String) -> Unit = {}) {
    backupDir.deleteRecursively()
    for ((path, content) in plan.oldFiles) File(backupDir, path).also { it.parentFile.mkdirs() }.writeBytes(content)
    try {
        for (path in plan.removedFiles) {
            beforeWrite(path)
            File(targetDir, path).delete()
        }
        for (path in plan.addedFiles + plan.changedFiles) {
            beforeWrite(path)
            File(targetDir, path).also { it.parentFile.mkdirs() }.writeBytes(plan.newFiles.getValue(path))
        }
    } catch (e: Throwable) {
        // restore: everything that was there, and remove what is new
        for (path in plan.addedFiles) File(targetDir, path).delete()
        for ((path, content) in plan.oldFiles) File(targetDir, path).also { it.parentFile.mkdirs() }.writeBytes(content)
        throw TrafficSignUpdateException("Writing the catalogue failed, the previous files were restored: $e", e)
    }
}

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
