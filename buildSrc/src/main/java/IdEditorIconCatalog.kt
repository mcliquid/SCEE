import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import java.io.File

data class IdEditorIconCatalog(
    /** Full Git commit SHA on `openstreetmap/iD` used for every iD-sprite download in this run. */
    val idEditorCommitSha: String,
    /** How this commit was chosen (release tag name, develop tip, or revision lock file). */
    val resolutionLabel: String,
    val idTaggingSchemaVersion: String,
    val makiVersion: String,
    val temakiVersion: String,
    val pinheadVersion: String,
    /** Legacy preset icon id → current pinhead file name (from pinhead `id_upgrades.json`). */
    val pinheadIdUpgrades: Map<String, String>,
    val roentgenVersion: String,
    val fontAwesomeVersion: String,
)

fun loadPinheadIdUpgrades(pinheadVersion: String, fetchText: (String) -> String): Map<String, String> {
    val url = "https://cdn.jsdelivr.net/npm/@waysidemapping/pinhead@$pinheadVersion/dist/id_upgrades.json"
    val json = Parser.default().parse(fetchText(url).reader()) as JsonObject
    return json.entries.associate { (legacy, value) -> legacy to value.toString().removeSurrounding("\"") }
}

private val RELEASE_TAGS_NEWEST_FIRST = listOf(
    "v2.43.0",
    "v2.42.0",
    "v2.41.0",
    "v2.40.0",
    "v2.39.0",
    "v2.38.0",
    "v2.37.0",
    "v2.36.0",
    "v2.35.0",
    "v2.34.0",
    "v2.33.0",
)

private const val ID_REPO = "openstreetmap/iD"

fun idEditorPackageLockUrl(commitSha: String) =
    "https://raw.githubusercontent.com/$ID_REPO/$commitSha/package-lock.json"

fun resolveIdEditorIconCatalog(
    presetsVersionTag: String,
    fetchText: (String) -> String,
    revisionLockFile: File? = null,
    log: (String) -> Unit = {},
): IdEditorIconCatalog {
    val wantedSchemaVersion = presetsVersionTag.removePrefix("v")

    revisionLockFile?.let { lockFile ->
        val record = readIdEditorRevisionRecord(lockFile)
        if (record != null && record.presetsVersionTag == presetsVersionTag) {
            val lockAtRecord = fetchText(idEditorPackageLockUrl(record.idEditorCommitSha))
            val schemaAtRecord = readLockfileSchemaVersion(lockAtRecord)
            if (schemaAtRecord == wantedSchemaVersion) {
                log(
                    "Using recorded iD commit ${record.idEditorCommitSha} from ${lockFile.name} " +
                        "(${record.resolutionLabel})",
                )
                return parseIconCatalogFromLockfile(
                    lockAtRecord,
                    record.idEditorCommitSha,
                    schemaAtRecord,
                    record.resolutionLabel,
                    fetchText,
                )
            }
            log("Ignoring ${lockFile.name}: schema at ${record.idEditorCommitSha} is $schemaAtRecord, wanted $wantedSchemaVersion")
        }
    }

    for (tag in RELEASE_TAGS_NEWEST_FIRST) {
        val sha = resolveGitHubCommitSha(ID_REPO, tag, fetchText) ?: continue
        val lockText = try {
            fetchText(idEditorPackageLockUrl(sha))
        } catch (_: Exception) {
            continue
        }
        val schemaVersion = readLockfileSchemaVersion(lockText) ?: continue
        if (schemaVersion == wantedSchemaVersion) {
            val label = "iD release $tag ($sha)"
            log("Resolved iD commit for schema $wantedSchemaVersion: $sha via release tag $tag")
            val catalog = parseIconCatalogFromLockfile(lockText, sha, schemaVersion, label, fetchText)
            revisionLockFile?.let { writeIdEditorRevisionRecord(it, catalog.toRevisionRecord(presetsVersionTag)) }
            return catalog
        }
    }

    val developSha = resolveGitHubCommitSha(ID_REPO, "develop", fetchText)
        ?: throw IllegalStateException(
            "Could not resolve the current commit SHA of openstreetmap/iD develop " +
                "(GitHub API unreachable?)."
        )
    val developLock = fetchText(idEditorPackageLockUrl(developSha))
    val schemaVersion = readLockfileSchemaVersion(developLock)
        ?: throw IllegalStateException(
            "package-lock.json at iD commit $developSha has no @openstreetmap/id-tagging-schema entry."
        )
    if (schemaVersion != wantedSchemaVersion) {
        throw IllegalStateException(
            "No iD release tag pins id-tagging-schema $wantedSchemaVersion, and develop tip ($developSha) pins " +
                "$schemaVersion. Wait for an iD release or change presetsVersion."
        )
    }
    val label = "iD develop tip ($developSha)"
    log("Resolved iD commit for schema $wantedSchemaVersion: $developSha (develop tip; lock read at same SHA)")
    val catalog = parseIconCatalogFromLockfile(developLock, developSha, schemaVersion, label, fetchText)
    revisionLockFile?.let { writeIdEditorRevisionRecord(it, catalog.toRevisionRecord(presetsVersionTag)) }
    return catalog
}

private fun IdEditorIconCatalog.toRevisionRecord(presetsVersionTag: String) =
    IdEditorRevisionRecord(
        presetsVersionTag = presetsVersionTag,
        idEditorCommitSha = idEditorCommitSha,
        lockSchemaVersion = idTaggingSchemaVersion,
        resolutionLabel = resolutionLabel,
    )

/** Resolves a branch or tag name to a commit SHA via the GitHub commits API. */
fun resolveGitHubCommitSha(repo: String, ref: String, fetchText: (String) -> String): String? = try {
    val body = fetchText("https://api.github.com/repos/$repo/commits/$ref")
    val json = Parser.default().parse(body.reader()) as JsonObject
    json.string("sha")?.takeIf { it.matches(Regex("[0-9a-f]{40}")) }
} catch (_: Exception) {
    null
}

fun readLockfileSchemaVersion(lockfileText: String): String? =
    readLockfilePackageVersion(lockfileText, "@openstreetmap/id-tagging-schema")

fun readLockfilePackageVersion(lockfileText: String, npmPackage: String): String? {
    val json = Parser.default().parse(lockfileText.reader()) as JsonObject
    val packages = json.obj("packages") ?: return null
    return packages.obj("node_modules/$npmPackage")?.string("version")
}

private fun parseIconCatalogFromLockfile(
    lockfileText: String,
    idEditorCommitSha: String,
    schemaVersion: String,
    resolutionLabel: String,
    fetchText: (String) -> String,
): IdEditorIconCatalog {
    fun requirePackage(name: String): String =
        readLockfilePackageVersion(lockfileText, name)
            ?: throw IllegalStateException(
                "iD package-lock.json at commit $idEditorCommitSha has no resolved version for $name"
            )

    val pinheadVersion = requirePackage("@waysidemapping/pinhead")
    return IdEditorIconCatalog(
        idEditorCommitSha = idEditorCommitSha,
        resolutionLabel = resolutionLabel,
        idTaggingSchemaVersion = schemaVersion,
        makiVersion = requirePackage("@mapbox/maki"),
        temakiVersion = requirePackage("@rapideditor/temaki"),
        pinheadVersion = pinheadVersion,
        pinheadIdUpgrades = loadPinheadIdUpgrades(pinheadVersion, fetchText),
        roentgenVersion = requirePackage("@enzet/roentgen"),
        fontAwesomeVersion = requirePackage("@fortawesome/free-solid-svg-icons"),
    )
}

/** Download candidates for a preset `icon` value (e.g. `maki-park`). */
fun iconDownloadUrls(icon: String, catalog: IdEditorIconCatalog): List<String>? {
    val prefix = icon.substringBefore('-', "")
    val file = icon.substringAfter('-')
    return when (prefix) {
        "iD" -> listOf(
            "https://raw.githubusercontent.com/openstreetmap/iD/${catalog.idEditorCommitSha}/svg/iD-sprite/presets/$file.svg"
        )
        "maki" -> listOf(
            "https://cdn.jsdelivr.net/npm/@mapbox/maki@${catalog.makiVersion}/icons/$file.svg"
        )
        "temaki" -> listOf(
            "https://cdn.jsdelivr.net/npm/@rapideditor/temaki@${catalog.temakiVersion}/icons/$file.svg"
        )
        "roentgen" -> listOf(
            "https://cdn.jsdelivr.net/npm/@enzet/roentgen@${catalog.roentgenVersion}/icons/$file.svg"
        )
        "fas" -> listOf(
            "https://raw.githubusercontent.com/FortAwesome/Font-Awesome/${catalog.fontAwesomeVersion}/svgs/solid/$file.svg"
        )
        "far" -> listOf(
            "https://raw.githubusercontent.com/FortAwesome/Font-Awesome/${catalog.fontAwesomeVersion}/svgs/regular/$file.svg"
        )
        "fab" -> listOf(
            "https://raw.githubusercontent.com/FortAwesome/Font-Awesome/${catalog.fontAwesomeVersion}/svgs/brands/$file.svg"
        )
        "pinhead" -> buildList {
            val base = "https://cdn.jsdelivr.net/npm/@waysidemapping/pinhead@${catalog.pinheadVersion}/dist/icons"
            add("$base/$file.svg")
            catalog.pinheadIdUpgrades[file]?.let { upgraded -> add("$base/$upgraded.svg") }
        }
        else -> null
    }
}