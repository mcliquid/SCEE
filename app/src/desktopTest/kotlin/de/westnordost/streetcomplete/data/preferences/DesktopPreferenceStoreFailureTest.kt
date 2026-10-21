package de.westnordost.streetcomplete.data.preferences

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.prefs.AbstractPreferences
import java.util.prefs.BackingStoreException
import kotlin.test.*

class DesktopPreferenceStoreFailureTest {
    private class Disk {
        var durable = linkedMapOf<String, MutableMap<String, String>>()
        var live = linkedMapOf<String, MutableMap<String, String>>()
        var fail: ((String, String, String) -> Boolean)? = null
        var drop: ((String, String) -> Boolean)? = null
        fun check(op: String, path: String, key: String = "") {
            if (fail?.invoke(op, path, key) == true) throw BackingStoreException("Injected $op failure")
        }
        fun restart(): Memory {
            live = durable.mapValuesTo(linkedMapOf()) { (_, v) -> v.toMutableMap() }
            fail = null
            return Memory(this)
        }
    }
    private class Memory(val disk: Disk, parent: Memory? = null, name: String = "") : AbstractPreferences(parent, name) {
        private val data get() = disk.live.getOrPut(absolutePath()) { linkedMapOf() }
        override fun putSpi(key: String, value: String) {
            disk.check("put", absolutePath(), key)
            if (disk.drop?.invoke(absolutePath(), key) != true) data[key] = value
        }
        override fun getSpi(key: String) = data[key]
        override fun removeSpi(key: String) { disk.check("remove", absolutePath(), key); data.remove(key) }
        override fun keysSpi() = data.keys.toTypedArray()
        override fun childrenNamesSpi() = disk.live.keys.mapNotNull { path ->
            val prefix = absolutePath().trimEnd('/') + "/"
            path.takeIf { it.startsWith(prefix) && it != absolutePath() }
                ?.removePrefix(prefix)?.substringBefore('/')?.takeIf { it.isNotEmpty() }
        }.distinct().toTypedArray()
        override fun childSpi(name: String) = Memory(disk, this, name).also { disk.live.getOrPut(it.absolutePath()) { linkedMapOf() } }
        override fun flushSpi() {
            disk.check("flush", absolutePath())
            disk.durable[absolutePath()] = data.toMutableMap()
        }
        override fun syncSpi() {}
        override fun removeNodeSpi() {
            disk.check("deleteNode", absolutePath())
            disk.live.remove(absolutePath()); disk.durable.remove(absolutePath())
        }
    }

    @Test fun `every interrupted generation write preserves the complete old value on restart`() {
        // Capture every storage boundary of a successful replacement, then fail each independently.
        val disk = Disk(); val raw = Memory(disk); val prefs = DesktopPreferenceStore(raw)
        val old = "A".repeat(20_000); val new = "B".repeat(27_000)
        prefs.putString("sources", old)
        val baseline = disk.durable.mapValues { (_, v) -> v.toMutableMap() }
        val operations = mutableListOf<Triple<String, String, String>>()
        disk.fail = { op, path, key -> operations += Triple(op, path.substringAfterLast('/').takeIf { it.startsWith("g-") }?.let { "generation" } ?: "index", key); false }
        prefs.putString("sources", new)
        for (failureIndex in operations.indices) {
            disk.durable = baseline.mapValuesTo(linkedMapOf()) { (_, v) -> v.toMutableMap() }
            val restarted = disk.restart(); val subject = DesktopPreferenceStore(restarted)
            var count = 0
            disk.fail = { _, _, _ -> count++ == failureIndex }
            runCatching { subject.putString("sources", new) }
            val recovered = DesktopPreferenceStore(disk.restart())
            assertTrue(recovered.getString("sources", "") in setOf(old, new), "boundary $failureIndex")
            assertEquals(setOf("sources"), recovered.keys)
        }
    }

    @Test fun `failed reference flush and failed rollback leave a whole recoverable value`() {
        val disk = Disk(); val raw = Memory(disk); val prefs = DesktopPreferenceStore(raw)
        prefs.putString("k", "old".repeat(9000))
        disk.fail = { op, path, _ -> op == "flush" && !path.substringAfterLast('/').startsWith("g-") }
        val failure = assertFails { prefs.putString("k", "new".repeat(9000)) }
        assertTrue(failure.suppressed.isNotEmpty())
        assertEquals("old".repeat(9000), DesktopPreferenceStore(disk.restart()).getString("k", ""))
    }

    @Test fun `remove is a recoverable tombstone and cannot resurrect legacy data`() {
        val disk = Disk(); val raw = Memory(disk)
        raw.put("legacy", "old"); raw.flush()
        val prefs = DesktopPreferenceStore(raw)
        prefs.putString("legacy", "new")
        disk.fail = { op, _, key -> op == "put" && key == "current" }
        assertFails { prefs.remove("legacy") }
        assertEquals("new", DesktopPreferenceStore(disk.restart()).getString("legacy", ""))
        prefs.remove("legacy")
        val recovered = DesktopPreferenceStore(disk.restart())
        assertFalse(recovered.hasKey("legacy")); assertTrue(recovered.all.isEmpty())
        assertEquals("old", raw.get("legacy", null))
    }

    @Test fun `all types share long keys and reserved names without collisions`() {
        val prefs = DesktopPreferenceStore(Memory(Disk()))
        val names = listOf("k".repeat(68), "k".repeat(69), "k".repeat(80), "k".repeat(81), "🌍".repeat(5000), "~abc.__name", "user.__part.0")
        for (key in names) {
            prefs.putInt(key, Int.MIN_VALUE); assertEquals(Int.MIN_VALUE, prefs.getIntOrNull(key))
            prefs.putLong(key, Long.MAX_VALUE); assertEquals(Long.MAX_VALUE, prefs.getLongOrNull(key))
            prefs.putFloat(key, 0.125f); assertEquals(0.125f, prefs.getFloatOrNull(key))
            prefs.putDouble(key, -0.0); assertEquals((-0.0).toRawBits(), prefs.getDouble(key, 1.0).toRawBits())
            prefs.putBoolean(key, true); assertEquals(true, prefs.getBooleanOrNull(key))
            prefs.putString(key, "@@scee-parts:2@@"); assertEquals("@@scee-parts:2@@", prefs.getStringOrNull(key))
        }
        assertEquals(names.toSet(), prefs.keys)
        for (key in names) { prefs.remove(key); assertFalse(prefs.hasKey(key)) }
        assertTrue(prefs.all.isEmpty())
    }

    @Test fun `native long keys and M6 parts remain readable and deletable`() {
        val disk = Disk(); val raw = Memory(disk)
        val key = "k".repeat(70)
        raw.putInt(key, 42)
        raw.put("sources", "@@scee-parts:2@@"); raw.put("sources.__part.0", "A".repeat(8000)); raw.put("sources.__part.1", "B")
        raw.flush()
        val prefs = DesktopPreferenceStore(raw)
        assertEquals(42, prefs.getInt(key, 0)); assertTrue(prefs.hasKey(key))
        assertEquals("A".repeat(8000) + "B", prefs.getStringOrNull("sources"))
        assertEquals(setOf(key, "sources"), prefs.keys)
        prefs.remove(key); assertFalse(prefs.hasKey(key))
        assertEquals("42", raw.get(key, null))
    }

    @Test fun `corrupt current generation recovers previous without deleting evidence`() {
        val disk = Disk(); val raw = Memory(disk); val prefs = DesktopPreferenceStore(raw)
        prefs.putString("k", "old"); prefs.putString("k", "new")
        val index = raw.node("scee-values-v2").childrenNames().single()
        val node = raw.node("scee-values-v2/$index")
        val current = node.get("current", "").substringBefore(':')
        node.node(current).remove("value.0")
        assertEquals("old", DesktopPreferenceStore(raw).getStringOrNull("k"))
        assertTrue(node.nodeExists(current))
    }

    @Test fun `unrecoverable entries do not block snapshots and can be overwritten or removed`() {
        for (damage in listOf("missing", "marker", "parts", "checksum", "deleted", "reference")) {
            val raw = Memory(Disk()); val prefs = DesktopPreferenceStore(raw)
            prefs.putString("broken", "evidence"); prefs.putString("healthy", "retained")
            val index = raw.node("scee-values-v2").childrenNames().single { digest ->
                val node = raw.node("scee-values-v2/$digest")
                node.node(node.get("current", "")).get("key.0", "") == "broken"
            }
            val node = raw.node("scee-values-v2/$index")
            val id = node.get("current", ""); val generation = node.node(id)
            when (damage) {
                "missing" -> generation.removeNode()
                "marker" -> generation.remove("complete")
                "parts" -> generation.remove("value.0")
                "checksum" -> generation.put("value.sha256", "bad")
                "deleted" -> generation.put("deleted", "invalid")
                "reference" -> node.put("current", "invalid/reference")
            }
            val failure = assertFailsWith<IllegalStateException> { prefs.getStringOrNull("broken") }
            assertTrue(failure.message!!.contains(when (damage) {
                "missing" -> "missing generation"
                "reference" -> "invalid reference"
                else -> "incomplete or corrupt generation"
            }))
            assertEquals(setOf("healthy"), prefs.keys)
            assertEquals(mapOf("healthy" to "retained"), prefs.all)
            assertEquals(1, prefs.size)
            val evidence = if (damage == "missing") emptyMap() else generation.keys().associateWith { generation.get(it, null) }
            if (damage == "parts") {
                prefs.remove("broken")
                assertNull(prefs.getStringOrNull("broken"))
            } else {
                prefs.putString("broken", "recovered")
                assertEquals("recovered", prefs.getStringOrNull("broken"))
            }
            repeat(4) { prefs.putInt("broken", it) }
            if (damage != "missing") {
                assertTrue(node.nodeExists(id))
                assertEquals(evidence, generation.keys().associateWith { generation.get(it, null) })
            }
            prefs.remove("broken")
            assertEquals(setOf("healthy"), prefs.keys)
        }
    }

    @Test fun `corrupt previous generation is not reclaimed by a healthy write`() {
        val raw = Memory(Disk()); val prefs = DesktopPreferenceStore(raw)
        prefs.putString("k", "one"); prefs.putString("k", "two")
        val node = raw.node("scee-values-v2").childrenNames().single().let { raw.node("scee-values-v2/$it") }
        val previous = node.get("current", "").substringAfter(':')
        node.node(previous).remove("complete")
        prefs.putString("k", "three")
        assertEquals("three", prefs.getStringOrNull("k"))
        assertTrue(node.nodeExists(previous))
    }

    @Test fun `damaged legacy hash mappings are isolated and recoverable without removing raw data`() {
        for (name in listOf(null, "wrong")) {
            for (remove in listOf(false, true)) {
                val raw = Memory(Disk()); val prefs = DesktopPreferenceStore(raw)
                val key = "long-key".repeat(20)
                val alias = "~" + java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }.take(40)
                raw.put(alias, "retained legacy payload")
                if (name != null) raw.put("$alias.__name", name)
                prefs.putString("healthy", "yes")
                assertFailsWith<IllegalStateException> { prefs.getStringOrNull(key) }
                assertEquals(setOf("healthy"), prefs.keys)
                assertEquals(mapOf("healthy" to "yes"), prefs.all)
                if (remove) { prefs.remove(key); assertNull(prefs.getStringOrNull(key)) }
                else { prefs.putString(key, "recovered"); assertEquals("recovered", prefs.getStringOrNull(key)) }
                assertEquals("retained legacy payload", raw.get(alias, null))
                assertEquals(name, raw.get("$alias.__name", null))
            }
        }
    }

    @Test fun `unexpected node failure is not suppressed as corrupt data`() {
        val raw = Memory(Disk()).node("test"); val prefs = DesktopPreferenceStore(raw)
        raw.removeNode()
        assertFailsWith<IllegalStateException> { prefs.keys }
        assertFailsWith<IllegalStateException> { prefs.putString("k", "v") }
    }

    @Test fun `read back detects silently dropped generation and publication writes`() {
        for (droppedKey in listOf("value.0", "complete", "current")) {
            val disk = Disk(); val raw = Memory(disk); val prefs = DesktopPreferenceStore(raw)
            prefs.putString("k", "old")
            disk.drop = { _, key -> key == droppedKey }
            assertFailsWith<IllegalStateException> { prefs.putString("k", "new") }
            disk.drop = null
            assertEquals("old", prefs.getStringOrNull("k"))
        }
    }

    @Test fun `listeners observe same part count updates from another wrapper and stop after deactivate`() {
        val raw = Memory(Disk()); val first = DesktopPreferenceStore(raw); val second = DesktopPreferenceStore(raw)
        val key = "long".repeat(40); val seen = LinkedBlockingQueue<String?>()
        val listener = first.addStringOrNullListener(key) { seen.offer(it ?: "removed") }
        for (text in listOf("A".repeat(20_000), "B".repeat(20_000), "short", "C".repeat(30_000))) {
            second.putString(key, text)
            assertEquals(text, seen.poll(5, TimeUnit.SECONDS))
        }
        second.remove(key); assertEquals("removed", seen.poll(5, TimeUnit.SECONDS))
        listener.deactivate(); second.putString(key, "ignored")
        assertNull(seen.poll(100, TimeUnit.MILLISECONDS))
    }

    @Test fun `malformed native primitives retain PreferencesSettings defaults`() {
        val raw = Memory(Disk()); raw.put("bad", "not a primitive")
        val prefs = DesktopPreferenceStore(raw)
        assertEquals(0, prefs.getIntOrNull("bad")); assertEquals(23, prefs.getInt("bad", 23))
        assertEquals(0L, prefs.getLongOrNull("bad")); assertEquals(23L, prefs.getLong("bad", 23L))
        assertEquals(0f, prefs.getFloatOrNull("bad")); assertEquals(23f, prefs.getFloat("bad", 23f))
        assertEquals(0.0, prefs.getDoubleOrNull("bad")); assertEquals(23.0, prefs.getDouble("bad", 23.0))
        assertEquals(false, prefs.getBooleanOrNull("bad")); assertTrue(prefs.getBoolean("bad", true))
    }

    @Test fun `a mismatched stored key cannot be overwritten through a colliding index`() {
        val raw = Memory(Disk()); val prefs = DesktopPreferenceStore(raw)
        prefs.putString("left", "retained")
        val node = raw.node("scee-values-v2").childrenNames().single().let { raw.node("scee-values-v2/$it") }
        val generation = node.node(node.get("current", "").substringBefore(':'))
        generation.put("key.0", "right")
        generation.put("key.sha256", java.security.MessageDigest.getInstance("SHA-256").digest("right".toByteArray()).joinToString("") { "%02x".format(it) })
        assertFailsWith<IllegalStateException> { prefs.putString("left", "must not overwrite") }
        assertEquals("retained", generation.get("value.0", null))
        assertTrue(prefs.keys.isEmpty())
        assertTrue(prefs.all.isEmpty())
    }

    @Test fun `failed reclamation retains the committed value and older evidence`() {
        val disk = Disk(); val raw = Memory(disk); val prefs = DesktopPreferenceStore(raw)
        prefs.putString("k", "one"); prefs.putString("k", "two")
        disk.fail = { op, _, _ -> op == "deleteNode" }
        prefs.putString("k", "three")
        assertEquals("three", DesktopPreferenceStore(disk.restart()).getStringOrNull("k"))
    }

    @Test fun `failed removal flush preserves the prior value after restart`() {
        val disk = Disk(); val raw = Memory(disk); val prefs = DesktopPreferenceStore(raw)
        prefs.putString("k", "retained")
        disk.fail = { op, path, _ -> op == "flush" && !path.substringAfterLast('/').startsWith("g-") }
        assertFails { prefs.remove("k") }
        assertEquals("retained", DesktopPreferenceStore(disk.restart()).getStringOrNull("k"))
    }

    @Test fun `typed long-key listeners observe replacement and removal`() {
        val raw = Memory(Disk()); val prefs = DesktopPreferenceStore(raw)
        val key = "typed".repeat(40); val seen = LinkedBlockingQueue<Int?>()
        val listener = prefs.addIntOrNullListener(key) { seen.offer(it ?: -1) }
        prefs.putInt(key, 42); assertEquals(42, seen.poll(5, TimeUnit.SECONDS))
        prefs.putInt(key, 73); assertEquals(73, seen.poll(5, TimeUnit.SECONDS))
        prefs.remove(key); assertEquals(-1, seen.poll(5, TimeUnit.SECONDS))
        listener.deactivate()
    }

    @Test fun `concurrent wrappers never expose mixed generations`() {
        val raw = Memory(Disk()); val workers = java.util.concurrent.Executors.newFixedThreadPool(4)
        val values = (0..3).map { ('A' + it).toString().repeat(20_000) }.toSet()
        try {
            val jobs = values.map { value -> workers.submit {
                val prefs = DesktopPreferenceStore(raw)
                repeat(12) {
                    prefs.putString("shared", value)
                    check(prefs.getStringOrNull("shared") in values)
                    check(prefs.all["shared"] in values)
                }
            } }
            jobs.forEach { it.get(10, TimeUnit.SECONDS) }
        } finally { workers.shutdownNow() }
    }
}
