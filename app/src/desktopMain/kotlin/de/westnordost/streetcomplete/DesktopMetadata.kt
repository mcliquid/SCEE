package de.westnordost.streetcomplete

import de.westnordost.streetcomplete.resources.Res
import java.io.File
import java.net.JarURLConnection
import java.net.URI
import java.net.URL
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID
import java.util.jar.JarEntry

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
        val bundled = entries.map { BundledJarEntry(it, validatedRelativeJarPath(it.name, prefix)) }
        val index = bundled.sortedBy { it.entry.name }.joinToString("\n") {
            "${it.entry.name}:${it.entry.size}:${it.entry.crc}"
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(index.toByteArray()).joinToString("") { "%02x".format(it) }
        val directory = File(cacheDirectory, digest)
        val rootPath = directory.toPath().toAbsolutePath().normalize()
        fun complete() = File(directory, ".complete").isFile && bundled.all {
            val file = resolvePathInsideRoot(rootPath, it.relativePath).let { target ->
                requireCanonicalInsideRoot(rootPath, target)
                target.toFile()
            }
            file.isFile && file.length() == it.entry.size
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
                val temporaryRoot = temporary.toPath().toAbsolutePath().normalize()
                try {
                    for (item in bundled) {
                        val target = resolvePathInsideRoot(temporaryRoot, item.relativePath)
                        requireCanonicalInsideRoot(temporaryRoot, target)
                        val file = target.toFile()
                        check(file.parentFile.mkdirs() || file.parentFile.isDirectory)
                        jar.getInputStream(item.entry).use { input -> file.outputStream().use { input.copyTo(it) } }
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

internal data class BundledJarEntry(val entry: JarEntry, val relativePath: String)

internal fun validatedRelativeJarPath(entryName: String, prefix: String): String {
    require(entryName.startsWith(prefix)) { "Invalid resource path" }
    val relative = entryName.substring(prefix.length).replace('\\', '/')
    require(relative.isNotEmpty() && relative != "." && !relative.startsWith('/') && !relative.contains("..")) {
        "Invalid resource path"
    }
    return relative
}

internal fun resolvePathInsideRoot(root: Path, relativePath: String): Path {
    val target = root.resolve(relativePath).normalize()
    require(target.startsWith(root)) { "Invalid resource path" }
    return target
}

internal fun jarEntryTarget(root: Path, entry: JarEntry, prefix: String): Path =
    resolvePathInsideRoot(root, validatedRelativeJarPath(entry.name, prefix))

internal fun requireCanonicalInsideRoot(root: Path, target: Path) {
    val rootDir = root.toFile()
    val targetFile = target.toFile()
    require(targetFile.canonicalFile.toPath().startsWith(rootDir.canonicalFile.toPath())) {
        "Invalid resource path"
    }
}