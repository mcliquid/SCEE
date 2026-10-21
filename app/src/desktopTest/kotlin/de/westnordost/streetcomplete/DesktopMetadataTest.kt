package de.westnordost.streetcomplete

import de.westnordost.osmfeatures.FeatureDictionary
import de.westnordost.streetcomplete.util.readBundledAsset
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.files.SystemFileSystem
import java.io.File
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.*

class DesktopMetadataTest {
    @Test fun `country boundaries and feature dictionary load without checkout property`() = runBlocking<Unit> {
        assertNull(System.getProperty("streetcomplete.resources"))
        Buffer().apply { write(readBundledAsset("boundaries.ser")) }.use {
            de.westnordost.countryboundaries.CountryBoundaries.deserializeFrom(it)
        }
        val cache = Files.createTempDirectory("desktop-metadata-test").toFile()
        try {
            val directory = desktopMetadataDirectory(cache)
            val dictionary = FeatureDictionary.create(SystemFileSystem, File(directory, "default").path, File(directory, "brands").path)
            assertNotNull(dictionary.getById("amenity/bench", listOf("en", null)))
        } finally { cache.deleteRecursively() }
    }

    @Test fun `jar extraction rejects traversal and leaves source and cache intact`() {
        val root = Files.createTempDirectory("metadata-jar-test").toFile()
        try {
            val jar = File(root, "resources.jar")
            JarOutputStream(jar.outputStream()).use {
                for (name in listOf("files/osmfeatures/default/presets.json", "files/osmfeatures/../../../outside")) {
                    it.putNextEntry(JarEntry(name)); it.write("{}".toByteArray()); it.closeEntry()
                }
            }
            val resource = java.net.URI("jar:${jar.toURI()}!/files/osmfeatures/default/presets.json").toURL()
            assertFailsWith<IllegalArgumentException> { desktopMetadataDirectory(File(root, "cache"), resource) }
            assertTrue(jar.isFile)
            assertFalse(File(root, "outside").exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun `incomplete published cache is preserved and rebuilt from the packaged jar`() {
        val root = Files.createTempDirectory("metadata-recovery-test").toFile()
        try {
            val jar = File(root, "resources.jar")
            JarOutputStream(jar.outputStream()).use {
                for (name in listOf("files/osmfeatures/default/presets.json", "files/osmfeatures/brands/presets.json")) {
                    it.putNextEntry(JarEntry(name)); it.write("{}".toByteArray()); it.closeEntry()
                }
            }
            val resource = java.net.URI("jar:${jar.toURI()}!/files/osmfeatures/default/presets.json").toURL()
            val cache = File(root, "cache")
            val directory = desktopMetadataDirectory(cache, resource)
            assertTrue(File(directory, "brands/presets.json").delete())
            assertEquals(directory, desktopMetadataDirectory(cache, resource))
            assertEquals("{}", File(directory, "brands/presets.json").readText())
            assertEquals(1, cache.listFiles()!!.count { it.name.contains(".incomplete-") })
        } finally { root.deleteRecursively() }
    }
}
