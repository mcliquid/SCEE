package de.westnordost.streetcomplete.quests.tree

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class TreeGenusAssetsTest {
    private val resources = generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
        .flatMap { sequenceOf(it, File(it, "app")) }
        .map { File(it, "src/commonMain/composeResources") }
        .first { File(it, "values/tree_genus.xml").exists() }
    private val photos = Json.parseToJsonElement(File(resources, "../../jvmAndroidTest/resources/tree/genus_photos.json").readText()).jsonArray

    @Test fun `all bundled photos have exact provenance and visible offline credits`() {
        val authors = File(resources, "files/authors.txt").readLines()
        val credits = File(resources, "files/credits_art.yml").readLines()
        val names = photos.map { it.jsonObject.getValue("file").jsonPrimitive.content }
        assertEquals(26, names.size)
        assertEquals(26, names.distinct().size)
        val actual = resources.listFiles()!!.filter { it.isDirectory && it.name.startsWith("drawable") }
            .flatMap { it.listFiles()!!.toList() }.filter { it.name.startsWith("tree_genus_") }.map { it.name }.toSet()
        assertEquals(names.toSet(), actual)
        assertFalse(File(resources, "files/tree/genus_photos.json").exists(), "test metadata must not be bundled")
        val walnut = authors.single { it.startsWith("produce_walnut.jpg ") }
        assertTrue(walnut.contains("CC-BY-SA 2.5"))
        for (photo in photos) {
            val entry = photo.jsonObject.filterValues { it is JsonPrimitive }.mapValues { it.value.jsonPrimitive.content }
            val name = entry.getValue("file")
            assertEquals(1, authors.count { it.startsWith("$name ") }, name)
            assertEquals(1, credits.count { it.contains("$name:") }, name)
            for (key in listOf("title", "author", "source", "license", "licenseUrl", "modifications", "retrieved")) {
                assertTrue(entry.getValue(key).isNotBlank(), "$name: $key")
            }
            assertTrue(entry.getValue("source").startsWith("https://commons.wikimedia.org/wiki/File:"), name)
            assertTrue(entry.getValue("licenseUrl").startsWith("https://creativecommons.org/"), name)
            assertTrue(entry.getValue("license").let { it.startsWith("CC BY") || it in listOf("CC0", "Public domain") }, name)
            val credit = credits.single { it.contains("$name:") }
            assertTrue(credit.contains(entry.getValue("licenseUrl")), name)
            assertTrue(credit.contains(entry.getValue("author")), name)
            assertTrue(credit.contains(entry.getValue("license")), name)
            assertTrue(credit.contains(entry.getValue("modifications")), name)
        }
        assertEquals(TreeGenus.entries.map { it.osmValue }.toSet(), photos.map { it.jsonObject.getValue("genus").jsonPrimitive.content }.toSet())
    }

    @Test fun `optimized photos decode match their recorded checksums and stay within size budget`() {
        var bytes = 0L
        for (photo in photos) {
            val entry = photo.jsonObject
            for ((density, variant) in entry.getValue("variants").jsonObject) {
                val details = variant.jsonObject
                val file = File(resources, "drawable-$density/${entry.getValue("file").jsonPrimitive.content}")
                val image = ImageIO.read(file)
                val size = details.getValue("size").jsonPrimitive.content.toInt()
                assertEquals(size, image.width)
                assertEquals(size, image.height)
                assertEquals(mapOf("mdpi" to 128, "hdpi" to 192, "xhdpi" to 256, "xxhdpi" to 384).getValue(density), size)
                val sha256 = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
                assertEquals(details.getValue("sha256").jsonPrimitive.content, sha256, file.name)
                bytes += file.length()
            }
        }
        assertTrue(bytes < 3 * 1024 * 1024, "photo budget exceeded: $bytes")
    }

    @Test fun `English and German names and aliases are complete and searchable`() {
        for (locale in listOf("values", "values-de")) {
            val factory = DocumentBuilderFactory.newInstance()
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            val xml = factory.newDocumentBuilder().parse(File(resources, "$locale/tree_genus.xml"))
            val nodes = xml.getElementsByTagName("string")
            val strings = (0 until nodes.length).associate {
                val node = nodes.item(it)
                node.attributes.getNamedItem("name").nodeValue to node.textContent
            }
            for (genus in TreeGenus.entries) {
                val key = "tree_genus_${genus.osmValue.lowercase()}"
                assertTrue(strings.getValue(key).isNotBlank())
                for (alias in strings.getValue("${key}_aliases").split(';')) {
                    assertTrue(genus.matches(alias, strings.getValue(key), strings.getValue("${key}_aliases")))
                }
            }
            if (locale == "values-de") {
                val chestnuts = TreeGenus.entries.filter {
                    val key = "tree_genus_${it.osmValue.lowercase()}"
                    it.matches("Kastanie", strings.getValue(key), strings.getValue("${key}_aliases"))
                }
                assertEquals(setOf(TreeGenus.AESCULUS, TreeGenus.CASTANEA), chestnuts.toSet())
            }
        }
    }
}
