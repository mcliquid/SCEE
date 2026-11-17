import java.io.File

/** Records the iD commit used for preset sprite downloads so later runs stay on the same revision until
 *  [presetsVersionTag] changes or the lock file is deleted. Not a manual version pin: content is written only
 *  by the resolver after a successful match. */
data class IdEditorRevisionRecord(
    val presetsVersionTag: String,
    val idEditorCommitSha: String,
    val lockSchemaVersion: String,
    val resolutionLabel: String,
)

fun readIdEditorRevisionRecord(file: File): IdEditorRevisionRecord? {
    if (!file.isFile) return null
    val values = mutableMapOf<String, String>()
    file.readLines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
        val idx = trimmed.indexOf('=')
        if (idx <= 0) return@forEach
        values[trimmed.substring(0, idx).trim()] = trimmed.substring(idx + 1).trim()
    }
    val tag = values["presetsVersionTag"] ?: return null
    val sha = values["idEditorCommitSha"] ?: return null
    val schema = values["lockSchemaVersion"] ?: return null
    val label = values["resolutionLabel"] ?: "recorded lock"
    if (!sha.matches(Regex("[0-9a-f]{40}"))) return null
    return IdEditorRevisionRecord(tag, sha, schema, label)
}

fun writeIdEditorRevisionRecord(file: File, record: IdEditorRevisionRecord) {
    file.parentFile?.mkdirs()
    file.writeText(
        """
        # Written by downloadAndConvertPresetIcons / resolveIdEditorIconCatalog. Safe to commit for reproducibility.
        presetsVersionTag=${record.presetsVersionTag}
        idEditorCommitSha=${record.idEditorCommitSha}
        lockSchemaVersion=${record.lockSchemaVersion}
        resolutionLabel=${record.resolutionLabel}
        """.trimIndent() + "\n",
    )
}