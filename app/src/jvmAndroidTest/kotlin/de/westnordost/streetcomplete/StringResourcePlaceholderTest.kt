package de.westnordost.streetcomplete

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Compose resources only substitute positional placeholders like `%1$s`. A plain `%s` or `%d`
 *  would be shown to the user verbatim. */
class StringResourcePlaceholderTest {

    private val projectDir: File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "app/src/commonMain/composeResources").exists() }

    private val resourceDirs = listOf(
        "app/src/commonMain/composeResources",
        "desktopApp/src/main/composeResources",
    ).map { File(projectDir, it) }.filter { it.exists() }

    private val nonPositional = Regex("%[sd]")

    @Test fun `string resources use only positional placeholders`() {
        val files = resourceDirs.flatMap { dir ->
            dir.listFiles { f -> f.isDirectory && f.name.startsWith("values") }!!.flatMap { it.listFiles()!!.toList() }
        }.filter { it.extension == "xml" }
        assertTrue(files.isNotEmpty())

        val offending = files.flatMap { file ->
            file.readLines().filter { nonPositional.containsMatchIn(it) }.map { "${file.parentFile.name}/${file.name}: ${it.trim()}" }
        }
        assertTrue(offending.isEmpty(), "non-positional placeholders:\n" + offending.joinToString("\n"))
    }
}
