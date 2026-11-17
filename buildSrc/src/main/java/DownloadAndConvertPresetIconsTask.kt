import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.net.URI

/** Download the SVG preset icons referred to by the iD presets and convert them to Android
 *  drawables. */
open class DownloadAndConvertPresetIconsTask : DefaultTask() {
    @get:OutputDirectory lateinit var targetDir: java.io.File
    @get:Input lateinit var version: String
    @get:Input var iconSize: Int = 14
    @get:Input var transformName: (String) -> String = { it }

    /** Current lock file contents (empty when the file does not exist yet) — invalidates the task when the lock changes. */
    @get:Input
    val revisionLockState: Property<String> = project.objects.property(String::class.java).convention("")

    @get:OutputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val idEditorRevisionLockFile: RegularFileProperty = project.objects.fileProperty()

    @TaskAction fun run() {
        val revisionLock = idEditorRevisionLockFile.orNull?.asFile
        val result = updatePresetIcons(
            PresetIconUpdateConfig(
                versionTag = version,
                targetDir = targetDir,
                iconSize = iconSize,
                nameTransform = transformName,
                idEditorRevisionLockFile = revisionLock,
            ),
            fetchText = { url -> URI(url).toURL().openStream().bufferedReader().readText() },
            fetchBytes = { url -> URI(url).toURL().openStream().readBytes() },
            log = { message -> println(message) },
        )
        logger.lifecycle(
            "Converted ${result.iconCount} preset icons (iD commit ${result.idEditorCommitSha}, " +
                "${result.idEditorResolutionLabel}, schema $version)"
        )
    }
}