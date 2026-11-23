package de.westnordost.streetcomplete

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JarEntryTargetTest {
    @Test fun `resolved path stays inside extraction root`() {
        val root = Files.createTempDirectory("jar-entry-target").toAbsolutePath().normalize()
        try {
            val target = jarEntryTarget(root, JarEntry("files/osmfeatures/default/presets.json"), "files/osmfeatures/")
            assertEquals(root.resolve("default/presets.json"), target)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun `rejects traversal segments`() {
        val root = Path.of("metadata-cache")
        assertFailsWith<IllegalArgumentException> {
            jarEntryTarget(root, JarEntry("files/osmfeatures/../../../outside"), "files/osmfeatures/")
        }
    }

    @Test fun `rejects absolute relative names`() {
        val root = Path.of("metadata-cache")
        assertFailsWith<IllegalArgumentException> {
            jarEntryTarget(root, JarEntry("files/osmfeatures//etc/passwd"), "files/osmfeatures/")
        }
    }

    @Test fun `canonical check rejects symlink targets outside the root`() {
        val root = Files.createTempDirectory("jar-entry-canonical").toAbsolutePath().normalize()
        val outside = Files.createTempDirectory("jar-outside").toAbsolutePath().normalize()
        val link = root.resolve("link")
        try {
            if (!runCatching { Files.createSymbolicLink(link, outside) }.isSuccess) return
            assertFailsWith<IllegalArgumentException> { requireCanonicalInsideRoot(root, link) }
        } finally {
            root.toFile().deleteRecursively()
            outside.toFile().deleteRecursively()
        }
    }
}