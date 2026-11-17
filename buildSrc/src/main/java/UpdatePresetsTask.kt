import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URI

/** Update the presets metadata and its translations for use with the de.westnordost:osmfeatures
 *  library */
open class UpdatePresetsTask : DefaultTask() {
    @get:Input lateinit var languageCodes: Collection<String>
    @get:OutputDirectory lateinit var targetDir: File
    @get:Input lateinit var version: String

    @TaskAction fun run() {
        updatePresetData(
            PresetDataUpdateConfig(
                versionTag = version,
                languageCodes = languageCodes,
                targetDir = targetDir,
            ),
            fetchBytes = { url -> URI(url).toURL().openStream().readBytes() },
            log = { message -> println(message) },
        )
    }
}