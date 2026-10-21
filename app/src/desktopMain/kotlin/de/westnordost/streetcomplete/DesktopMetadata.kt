package de.westnordost.streetcomplete

import de.westnordost.streetcomplete.resources.Res
import java.io.File
import java.net.JarURLConnection
import java.net.URI
import java.net.URL
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID

/** osmfeatures requires real files. Extract only its bundled metadata, never user data. */
@Synchronized
internal fun desktopMetadataDirectory(
    cacheDirectory: File,
    resource: URL = URI(Res.getUri("files/osmfeatures/default/presets.json")).toURL(),
): File {
    if (resource.protocol == "file") return File(resource.toURI()).parentFile.parentFile
    require(resource.protocol == "jar") { "Unsupported bundled metadata URL" }
    val connection = resource.openConnection() as JarURLConnection
    connection.useCaches = false
    connection.jarFile.use { jar ->
        val prefix = connection.entryName.removeSuffix("default/presets.json")
        require(prefix != connection.entryName)
        val entries = jar.entries().asSequence().filter { !it.isDirectory && it.name.startsWith(prefix) }.toList()
        require(entries.isNotEmpty()) { "Missing bundled metadata" }
        val index = entries.sortedBy { it.name }.joinToString("\n") { "${it.name}:${it.size}:${it.crc}" }
        val digest = MessageDigest.getInstance("SHA-256").digest(index.toByteArray()).joinToString("") { "%02x".format(it) }
        val directory = File(cacheDirectory, digest)
        fun complete() = File(directory, ".complete").isFile && entries.all {
            File(directory, it.name.removePrefix(prefix)).let { file -> file.isFile && file.length() == it.size }
        }
        Files.createDirectories(cacheDirectory.toPath())
        FileChannel.open(File(cacheDirectory, ".lock").toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            channel.lock().use {
                if (complete()) return directory
                if (directory.exists()) {
                    // Preserve incomplete evidence while making the canonical location recoverable.
                    Files.move(
                        directory.toPath(),
                        File(cacheDirectory, "$digest.incomplete-${UUID.randomUUID()}").toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                    )
                }
                val temporary = Files.createTempDirectory(cacheDirectory.toPath(), ".metadata-").toFile()
                try {
                    for (entry in entries) {
                        val file = File(temporary, entry.name.removePrefix(prefix))
                        require(file.canonicalFile.toPath().startsWith(temporary.canonicalFile.toPath())) { "Invalid resource path" }
                        check(file.parentFile.mkdirs() || file.parentFile.isDirectory)
                        jar.getInputStream(entry).use { input -> file.outputStream().use { input.copyTo(it) } }
                    }
                    File(temporary, ".complete").writeText(digest)
                    Files.move(temporary.toPath(), directory.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    return directory
                } finally {
                    temporary.deleteRecursively()
                }
            }
        }
    }
}
