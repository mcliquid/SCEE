package de.westnordost.streetcomplete.quests.smoothness

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Every smoothness example photo must be credited in the in-app authors list. */
class SmoothnessPhotoAttributionTest {

    private val appDir: File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .flatMap { sequenceOf(it, File(it, "app")) }
        .first { File(it, "src/commonMain/composeResources/files/authors.txt").exists() }

    private val resourcesDir = File(appDir, "src/commonMain/composeResources")

    private val smoothnessPhoto = Regex(
        "surface_.+_(excellent|good|intermediate|bad|very_bad|horrible|very_horrible|impassable)\\.jpg"
    )

    @Test fun `all smoothness photos are credited`() {
        val photos = resourcesDir.listFiles { f -> f.isDirectory && f.name.startsWith("drawable") }!!
            .flatMap { it.listFiles()!!.map { f -> f.name } }
            .filter { smoothnessPhoto.matches(it) }
            .toSet()
        assertTrue(photos.size >= 50, "expected smoothness photos, found ${photos.size}")

        val credited = File(resourcesDir, "files/authors.txt").readLines()
            .map { it.substringBefore(' ') }
            .filter { it.startsWith("surface_") }
        fun entriesFor(name: String) = credited.filter {
            it == name || (it.endsWith("...") && name.startsWith(it.removeSuffix("...")))
        }

        val missing = photos.filter { entriesFor(it).isEmpty() }
        assertTrue(missing.isEmpty(), "not credited in authors.txt: $missing")

        // a (shortened) entry must not be the credit for more than one file, and no file may have
        // several credits
        val ambiguous = photos.filter { entriesFor(it).size > 1 }
        assertTrue(ambiguous.isEmpty(), "several credits in authors.txt: $ambiguous")
        val allSurfaceFiles = resourcesDir.listFiles { f -> f.isDirectory && f.name.startsWith("drawable") }!!
            .flatMap { it.listFiles()!!.map { f -> f.name } }
            .filter { it.startsWith("surface_") }
            .toSet()
        val sharedEntries = credited.filter { entry ->
            allSurfaceFiles.count { it == entry || (entry.endsWith("...") && it.startsWith(entry.removeSuffix("..."))) } > 1
        }
        assertTrue(sharedEntries.isEmpty(), "entries matching several files: $sharedEntries")
        val duplicates = credited.groupBy { it }.filterValues { it.size > 1 }.keys
        assertTrue(duplicates.isEmpty(), "duplicate entries: $duplicates")
    }
}
