package de.westnordost.streetcomplete

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import de.westnordost.streetcomplete.util.logs.Log
import de.westnordost.streetcomplete.util.logs.Logger
import de.westnordost.streetcomplete.screens.settings.renamedQuests
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class QuestPreferencesMigrationTest {
    private lateinit var raw: SharedPreferences
    private val previousLoggers = Log.instances
    private val warnings = mutableListOf<String>()

    @Before fun setUp() {
        raw = RuntimeEnvironment.getApplication().getSharedPreferences("kmp_m2b1_fixture", Context.MODE_PRIVATE)
        assertTrue(raw.edit().clear().putString(MARKER, "old").commit())
        Log.instances = mutableListOf(object : Logger {
            override fun v(tag: String, message: String) {}
            override fun d(tag: String, message: String) {}
            override fun i(tag: String, message: String) {}
            override fun w(tag: String, message: String, exception: Throwable?) { warnings += message }
            override fun e(tag: String, message: String, exception: Throwable?) {}
        })
    }

    @After fun tearDown() { Log.instances = previousLoggers }

    @Test fun `successful plan preserves native types JSON unicode sets and double long bits`() {
        raw.edit().putBoolean(old("boolean"), true).putInt(old("int"), Int.MAX_VALUE)
            .putLong(old("long"), Long.MAX_VALUE).putFloat(old("float"), 0.125f)
            .putString(old("json"), "{\"name\":\"AddPlaygroundAccess ä & < >\"}")
            .putStringSet(old("set"), setOf("AddPlaygroundAccess", "", "ä & < >"))
            .putLong(old("double"), (-0.0).toRawBits()).commit()
        assertTrue(migrate())
        assertEquals(true, raw.all[new("boolean")])
        assertEquals(Int.MAX_VALUE, raw.all[new("int")])
        assertEquals(Long.MAX_VALUE, raw.all[new("long")])
        assertEquals(0.125f, raw.all[new("float")])
        assertEquals("{\"name\":\"AddPlaygroundAccess ä & < >\"}", raw.all[new("json")])
        assertEquals(setOf("AddPlaygroundAccess", "", "ä & < >"), raw.all[new("set")])
        assertEquals((-0.0).toRawBits(), raw.all[new("double")])
        assertFalse(raw.all.keys.any { it.startsWith("7_qs_AddPlaygroundAccess_") })
        assertEquals(CURRENT, raw.getString(MARKER, null))
    }

    @Test fun `completed second start does not even open an editor`() {
        raw.edit().putString(old(), "filter").commit()
        assertTrue(migrate())
        val once = raw.all
        val noWrites = object : SharedPreferences by raw {
            override fun edit(): SharedPreferences.Editor = error("No write expected")
        }
        assertFalse(migrate(noWrites))
        assertEquals(once, raw.all)
    }

    @Test fun `missing nullable marker is created without inventing other entries`() {
        raw.edit().clear().commit()
        assertTrue(migrate())
        assertEquals(mapOf(MARKER to CURRENT), raw.all)
        assertFalse(migrate())
    }

    @Test fun `unknown keys payloads preset user and map values are left byte for byte unchanged`() {
        raw.edit().putString("oauth2.accessToken", "fixture-only-AddPlaygroundAccess-not-a-credential")
            .putLong("osm.userid", Long.MAX_VALUE).putString("osm.username", "ä AddTowerAccess")
            .putLong("selectedQuestsPreset", 7L).putLong("map.latitude", 52.52.toRawBits())
            .putBoolean("map.following", false).putString("custom_overlay_0_filter", "name=AddPlaygroundAccess")
            .putString("unknown_AddTowerAccess", "{\"quest\":\"AddPlaygroundAccess\"}")
            .putStringSet("legacy_set_AddPlaygroundAccess", setOf("AddTowerAccess")).commit()
        val before = raw.all - MARKER
        assertTrue(migrate())
        assertEquals(before, raw.all - MARKER)
    }

    @Test fun `quest name substrings and history suffixes are not mistaken for exact quest references`() {
        raw.edit().putString("qs_AddPlaygroundAccessSuffix_filter", "x")
            .putString("7_qs_AddTowerAccessExtra_filter", "x")
            .putString("imageListLastPicked.AddPlaygroundAccessExtra", "[]").commit()
        val before = raw.all - MARKER
        migrate()
        assertEquals(before, raw.all - MARKER)
    }

    @Test fun `all five quest renames preserve filters labels and answer history payloads`() {
        for ((oldName, newName) in renamedQuests) {
            raw.edit().clear().putString(MARKER, "old").commit()
            val payload = "name = $oldName and ref = AddTowerAccess"
            val keys = listOf("qs_${oldName}_full_element_selection", "7_qs_${oldName}_label_sources",
                "0_qs_${oldName}_full_element_selection", "imageListLastPicked.$oldName")
            val editor = raw.edit()
            keys.forEach { editor.putString(it, payload) }
            editor.putString("user_text", oldName).putString("custom_overlay_0_filter", payload).commit()
            assertTrue(migrate())
            for (key in keys) {
                val target = key.replace(oldName, newName)
                assertEquals(payload, raw.getString(target, null))
                assertFalse(raw.contains(key))
            }
            assertEquals(oldName, raw.getString("user_text", null))
            assertEquals(payload, raw.getString("custom_overlay_0_filter", null))
            assertFalse(migrate())
        }
    }

    @Test fun `merged quest history conflicts remain lossless and deterministic`() {
        val playground = "imageListLastPicked.AddPlaygroundAccess"
        val tower = "imageListLastPicked.AddTowerAccess"
        val target = "imageListLastPicked.AddGeneralAccess"
        raw.edit().putString(playground, "[\"yes\"]").putString(tower, "[\"private\"]").commit()
        val before = raw.all
        assertFalse(migrate())
        assertEquals(before, raw.all)
        raw.edit().putString(target, "[\"no\"]").commit()
        assertTrue(migrate())
        assertEquals("[\"no\"]", raw.getString(target, null))
        assertEquals("[\"yes\"]", raw.getString(playground, null))
        assertEquals("[\"private\"]", raw.getString(tower, null))
    }

    @Test fun `existing destination wins while different source remains recoverable`() {
        raw.edit().putString(old(), "old").putString(new(), "new").commit()
        assertTrue(migrate())
        assertEquals("new", raw.getString(new(), null))
        assertEquals("old", raw.getString(old(), null))
        assertFalse(migrate())
    }

    @Test fun `equal source and existing destination remove only redundant source`() {
        raw.edit().putString(old(), "same").putString(new(), "same").commit()
        migrate()
        assertFalse(raw.contains(old()))
        assertEquals("same", raw.getString(new(), null))
    }

    @Test fun `two identical old values merge once regardless of insertion order`() {
        for (reverse in listOf(false, true)) {
            raw.edit().clear().putString(MARKER, "old").commit()
            val sources = listOf(old(), tower()).let { if (reverse) it.reversed() else it }
            sources.forEach { raw.edit().putString(it, "same").commit() }
            assertTrue(migrate())
            assertFalse(raw.contains(old()))
            assertFalse(raw.contains(tower()))
            assertEquals("same", raw.getString(new(), null))
        }
    }

    @Test fun `different sources defer whole plan without editor or partial independent rename`() {
        raw.edit().putString(old(), "first").putString(tower(), "second")
            .putString("qs_AddPicnicTableCover_filter", "independent").commit()
        val before = raw.all
        val noEditor = object : SharedPreferences by raw {
            override fun edit(): SharedPreferences.Editor = error("Conflict must precede editor")
        }
        assertFalse(migrate(noEditor))
        assertEquals(before, raw.all)
        assertEquals(1, warnings.size)
        assertFalse(warnings.single().contains("first"))
        assertFalse(warnings.single().contains("second"))
    }

    @Test fun `different sources defer deterministically for reversed snapshot order too`() {
        raw.edit().putString(old(), "a").putString(tower(), "b").commit()
        val before = raw.all
        for (reverse in listOf(false, true)) {
            val ordered = before.entries.sortedBy { it.key }.let { if (reverse) it.reversed() else it }
                .associateTo(linkedMapOf()) { it.key to it.value }
            val store = object : SharedPreferences by raw {
                override fun getAll(): MutableMap<String, *> = ordered
            }
            assertFalse(migrate(store))
            assertEquals(before, raw.all)
        }
    }

    @Test fun `same numeric magnitude with different native types is a conflict`() {
        raw.edit().putInt(old(), 1).putLong(tower(), 1L).commit()
        assertFalse(migrate())
        assertEquals("old", raw.getString(MARKER, null))
        assertFalse(raw.contains(new()))
    }

    @Test fun `source set changes only its key never its elements`() {
        raw.edit().putStringSet(old(), setOf("AddPlaygroundAccess", "AddTowerAccess")).commit()
        migrate()
        assertFalse(raw.contains(old()))
        assertEquals(setOf("AddPlaygroundAccess", "AddTowerAccess"), raw.getStringSet(new(), null))
    }

    @Test fun `existing destination set and differing legacy set are both preserved`() {
        raw.edit().putStringSet(old(), setOf("old")).putStringSet(new(), setOf("new")).commit()
        migrate()
        assertEquals(setOf("old"), raw.getStringSet(old(), null))
        assertEquals(setOf("new"), raw.getStringSet(new(), null))
    }

    @Test fun `equal source sets with different element iteration orders merge safely`() {
        raw.edit().putStringSet(old(), linkedSetOf("a", "b")).putStringSet(tower(), linkedSetOf("b", "a")).commit()
        assertTrue(migrate())
        assertEquals(setOf("a", "b"), raw.getStringSet(new(), null))
        assertFalse(raw.contains(old()))
        assertFalse(raw.contains(tower()))
    }

    @Test fun `conflict in one preset blocks all and explicit destination resolves it without losing old data`() {
        raw.edit().putString("qs_AddPlaygroundAccess_filter", "global")
            .putString("7_qs_AddPlaygroundAccess_filter", "seven")
            .putString("9_qs_AddPlaygroundAccess_filter", "nine a")
            .putString("9_qs_AddTowerAccess_filter", "nine b").commit()
        val before = raw.all
        assertFalse(migrate())
        assertEquals(before, raw.all)
        raw.edit().putString("9_qs_AddGeneralAccess_filter", "chosen").commit()
        assertTrue(migrate())
        assertEquals("global", raw.getString("qs_AddGeneralAccess_filter", null))
        assertEquals("seven", raw.getString("7_qs_AddGeneralAccess_filter", null))
        assertEquals("chosen", raw.getString("9_qs_AddGeneralAccess_filter", null))
        assertEquals("nine a", raw.getString("9_qs_AddPlaygroundAccess_filter", null))
        assertEquals("nine b", raw.getString("9_qs_AddTowerAccess_filter", null))
    }

    @Test fun `history collision defers without merging or normalizing JSON lists`() {
        raw.edit().putString("imageListLastPicked.AddPlaygroundAccess", "[\"a\"]")
            .putString("imageListLastPicked.AddTowerAccess", "[ \"a\" ]").commit()
        val before = raw.all
        assertFalse(migrate())
        assertEquals(before, raw.all)
    }

    @Test fun `partially migrated snapshot keeps destination and migrates remaining independent keys`() {
        raw.edit().putString(new(), "existing").putString(old(), "backup")
            .putString("qs_AddPicnicTableCover_filter", "cover").commit()
        assertTrue(migrate())
        assertEquals("existing", raw.getString(new(), null))
        assertEquals("backup", raw.getString(old(), null))
        assertEquals("cover", raw.getString("qs_AddAmenityCover_filter", null))
        assertFalse(migrate())
    }

    @Test fun `snapshot exception leaves everything intact and normal retry succeeds`() {
        raw.edit().putString(old(), "x").commit()
        val before = raw.all
        val broken = object : SharedPreferences by raw {
            override fun getAll(): MutableMap<String, *> = error("snapshot failed")
        }
        assertFailsWith<IllegalStateException> { migrate(broken) }
        assertEquals(before, raw.all)
        assertTrue(migrate())
    }

    @Test fun `unsupported value during planning fails before any editor is opened`() {
        raw.edit().putString(old(), "actual value").commit()
        val before = raw.all
        val broken = object : SharedPreferences by raw {
            override fun getAll(): MutableMap<String, *> = raw.all.toMutableMap().apply { put(old(), 1.5) }
            override fun edit(): SharedPreferences.Editor = error("No editor allowed during preparation")
        }
        val failure = assertFailsWith<IllegalStateException> { migrate(broken) }
        assertEquals("Unsupported quest preference value", failure.message)
        assertEquals(before, raw.all)
        assertTrue(migrate())
    }

    @Test fun `invalid set elements fail during preparation without converting or dropping data`() {
        val before = raw.all
        val broken = object : SharedPreferences by raw {
            override fun getAll(): MutableMap<String, *> = raw.all.toMutableMap().apply { put(old(), setOf(1)) }
        }
        assertFailsWith<IllegalArgumentException> { migrate(broken) }
        assertEquals(before, raw.all)
        assertTrue(migrate())
    }

    @Test fun `editor creation failure leaves snapshot unchanged and retry succeeds`() {
        raw.edit().putString(old(), "x").commit()
        val before = raw.all
        val broken = object : SharedPreferences by raw {
            override fun edit(): SharedPreferences.Editor = error("editor failed")
        }
        assertFailsWith<IllegalStateException> { migrate(broken) }
        assertEquals(before, raw.all)
        assertTrue(migrate())
    }

    @Test fun `exception while buffering target writes never publishes the prepared removals`() {
        raw.edit().putString(old(), "x").commit()
        val before = raw.all
        val broken = editWith { delegate ->
            object : SharedPreferences.Editor by delegate {
                override fun putString(key: String, value: String?): SharedPreferences.Editor = error("buffer failed")
            }
        }
        assertFailsWith<IllegalStateException> { migrate(broken) }
        assertEquals(before, raw.all)
        assertTrue(migrate())
    }

    @Test fun `false commit before publication is rejected and remains retryable`() {
        raw.edit().putString(old(), "x").commit()
        var attempts = 0
        val broken = editWith { delegate ->
            object : SharedPreferences.Editor by delegate {
                override fun commit(): Boolean = if (++attempts == 1) false else delegate.commit()
            }
        }
        assertFailsWith<IllegalStateException> { migrate(broken) }
        assertEquals(2, attempts)
        assertEquals("old", raw.getString(MARKER, null))
        assertEquals("x", raw.getString(old(), null))
        assertTrue(migrate())
    }

    @Test fun `false commit after memory publication restores marker and retries without double migration`() {
        raw.edit().putString(old(), "x").commit()
        var attempts = 0
        val broken = editWith { delegate ->
            object : SharedPreferences.Editor by delegate {
                override fun commit(): Boolean {
                    val result = delegate.commit()
                    return if (++attempts == 1) false else result
                }
            }
        }
        assertFailsWith<IllegalStateException> { migrate(broken) }
        assertEquals("old", raw.getString(MARKER, null))
        assertEquals("x", raw.getString(new(), null))
        assertFalse(raw.contains(old()))
        val payload = raw.all - MARKER
        assertTrue(migrate())
        assertEquals(payload, raw.all - MARKER)
        assertFalse(migrate())
    }

    @Test fun `exception after publication restores marker before normal retry`() {
        raw.edit().putString(old(), "x").commit()
        var attempts = 0
        val broken = editWith { delegate ->
            object : SharedPreferences.Editor by delegate {
                override fun commit(): Boolean {
                    val result = delegate.commit()
                    if (++attempts == 1) error("write reported failure")
                    return result
                }
            }
        }
        assertFailsWith<IllegalStateException> { migrate(broken) }
        assertEquals("old", raw.getString(MARKER, null))
        assertTrue(migrate())
    }

    @Test fun `repeated negative commit results are never reported as success even on marker restoration`() {
        raw.edit().putString(old(), "x").commit()
        val broken = editWith { delegate ->
            object : SharedPreferences.Editor by delegate {
                override fun commit(): Boolean { delegate.commit(); return false }
            }
        }
        val failure = assertFailsWith<IllegalStateException> { migrate(broken) }
        assertEquals(1, failure.suppressed.size)
        assertEquals("old", raw.getString(MARKER, null))
        assertTrue(migrate())
    }

    @Test fun `failed publication on first install restores missing marker rather than string null`() {
        raw.edit().remove(MARKER).putString(old(), "x").commit()
        var attempts = 0
        val broken = editWith { delegate ->
            object : SharedPreferences.Editor by delegate {
                override fun commit(): Boolean {
                    val result = delegate.commit()
                    return if (++attempts == 1) false else result
                }
            }
        }
        assertFailsWith<IllegalStateException> { migrate(broken) }
        assertFalse(raw.contains(MARKER))
        assertTrue(migrate())
    }

    @Test fun `marker and renames publish in one commit and never use apply`() {
        raw.edit().putString(old(), "x").commit()
        var commits = 0
        val observed = editWith { delegate ->
            object : SharedPreferences.Editor by delegate {
                override fun apply() = error("Asynchronous publication not allowed")
                override fun commit(): Boolean {
                    assertEquals("old", raw.getString(MARKER, null))
                    assertTrue(raw.contains(old()))
                    assertFalse(raw.contains(new()))
                    commits++
                    return delegate.commit()
                }
            }
        }
        assertTrue(migrate(observed))
        assertEquals(1, commits)
        assertEquals(CURRENT, raw.getString(MARKER, null))
        assertEquals("x", raw.getString(new(), null))
    }

    @Test fun `unrelated update after snapshot is not overwritten by targeted migration`() {
        raw.edit().putString(old(), "x").putString("user_text", "before").commit()
        val concurrent = object : SharedPreferences by raw {
            override fun edit(): SharedPreferences.Editor {
                raw.edit().putString("user_text", "after").commit()
                return raw.edit()
            }
        }
        assertTrue(migrate(concurrent))
        assertEquals("after", raw.getString("user_text", null))
    }

    private fun migrate(store: SharedPreferences = raw) = migrateQuestPreferences(store, CURRENT)

    private fun editWith(wrap: (SharedPreferences.Editor) -> SharedPreferences.Editor): SharedPreferences =
        object : SharedPreferences by raw {
            override fun edit(): SharedPreferences.Editor = wrap(raw.edit())
        }

    companion object {
        private const val MARKER = "lastVersion_data"
        private const val CURRENT = "fixture-current"
        private fun old(suffix: String = "filter") = "7_qs_AddPlaygroundAccess_$suffix"
        private fun tower() = "7_qs_AddTowerAccess_filter"
        private fun new(suffix: String = "filter") = "7_qs_AddGeneralAccess_$suffix"
    }
}
