package de.westnordost.streetcomplete.util

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.MissingResourceException
import java.security.MessageDigest
import java.io.BufferedReader
import java.io.StringReader
import java.util.concurrent.Executors
import kotlin.test.*

abstract class BundledAssetsContract {
    // Hashes of the LF-normalized content as stored in git; a Windows checkout may contain CRLF.
    private val hashes = mapOf(
        "brewery/brewerySuggestions.txt" to "6f1b420f919b0ac186e9709abeb08f04ec9473edbbccdb3eee959531bd90e6de",
        "cuisine/cuisineSuggestions.txt" to "4bad07ff52f04c2611274fb99a2f35880edaf8685b45351cc4b2f09f61a678df",
        "tag_editor/keySuggestionsForFeature.json" to "4859073c5e0d82f3360b905a872a1230d421ce4c9ad48e737c47aa84b9f4b0fb",
        "tag_editor/valueSuggestionsByKey.json" to "14eaf09e22ef4c7d4042a4510e60e89e86289a28f6c834f63f7cfb4e48847ff8",
        "tree/osmGenus.txt" to "364ecbf5a506cefd8467a875e6d36a061274572c8109338800468fd96038ed6f",
        "tree/osmSpecies.txt" to "3b6d7ba840860eefb74afd45556c9c3f5874eee78d9ef323f847dbc2d2a61543",
        "tree/otherDataGenus.txt" to "11b706f0c078373ea5ba57c8f0702794d6ca696e2e214349574d1b8bc0264403",
        "tree/otherDataSpecies.txt" to "258c5a52dfcc49b4003fe3f6bbf4ed4b1195443e1beba7b9ffa693875ee6c093"
    )

    @Test fun `all migrated assets preserve their git content on this platform`() = runBlocking<Unit> {
        for ((path, hash) in hashes) {
            val bytes = readBundledAsset(path).filter { it != 13.toByte() }.toByteArray()
            assertEquals(hash, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, path)
        }
    }

    @Test fun `UTF8 text and reader line semantics remain unchanged`() = runBlocking<Unit> {
        val text = readBundledText("brewery/brewerySuggestions.txt")
        assertTrue(text.any { it.code > 127 })
        assertEquals(BufferedReader(StringReader(text)).readLines(), readBundledLines("brewery/brewerySuggestions.txt"))
        for (value in listOf("", "a", "a\n", "a\r\nb", "a\r", "a\n\n", "ä🌍\r\n")) {
            assertEquals(BufferedReader(StringReader(value)).readLines(), assetLines(value))
        }
    }

    @Test fun `existing binary resources are not decoded as text`() = runBlocking<Unit> {
        val bytes = readBundledAsset("plop0.wav")
        assertEquals("RIFF", bytes.copyOfRange(0, 4).decodeToString())
        assertTrue(bytes.any { it == 0.toByte() })
    }

    @Test fun `missing and Unicode resource names propagate a missing resource error`() = runBlocking<Unit> {
        assertFailsWith<MissingResourceException> { readBundledAsset("missing-asset.txt") }
        assertFailsWith<MissingResourceException> { readBundledAsset("测试/ä-missing.bin") }
    }

    @Test fun `absolute and traversal paths are rejected`() = runBlocking<Unit> {
        for (path in listOf("", "/x", "../x", "a/../x", "a//x", "C:/x", "a\\x")) {
            assertFailsWith<IllegalArgumentException> { readBundledAsset(path) }
        }
    }

    @Test open fun `Unicode filenames and UTF8 fixture contents are supported`() = runBlocking<Unit> {
        assertEquals("UTF-8 漢字🌍\n", readBundledText("m4-fixtures/ä.txt"))
    }

    @Test fun `parallel repeated reads have independent byte buffers`() {
        val pool = Executors.newFixedThreadPool(4)
        try {
            val expected = runBlocking { readBundledAsset("cuisine/cuisineSuggestions.txt") }
            val futures = (0..7).map { pool.submit<ByteArray> { runBlocking { readBundledAsset("cuisine/cuisineSuggestions.txt") } } }
            for (future in futures) assertContentEquals(expected, future.get())
            val changed = runBlocking { readBundledAsset("cuisine/cuisineSuggestions.txt") }
            changed[0] = (changed[0].toInt() xor 255).toByte()
            assertContentEquals(expected, runBlocking { readBundledAsset("cuisine/cuisineSuggestions.txt") })
        } finally { pool.shutdownNow() }
    }
}
