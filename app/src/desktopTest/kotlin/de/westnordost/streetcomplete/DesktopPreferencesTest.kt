package de.westnordost.streetcomplete

import de.westnordost.streetcomplete.data.preferences.DesktopPreferenceStore
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.preferences.Theme
import de.westnordost.streetcomplete.data.preferences.Autosync
import de.westnordost.streetcomplete.data.preferences.SettingsWithSnapshot
import de.westnordost.streetcomplete.data.quest.QuestType
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.util.UUID
import java.util.prefs.Preferences as JavaPreferences
import kotlin.test.*

class DesktopPreferencesTest {
    @Test fun `desktop wrapper preserves primitive reads and persisted native strings on recreation`() = store { node ->
        val first = Preferences(DesktopPreferenceStore(node))
        first.putBoolean("bool", true)
        first.putInt("int", Int.MIN_VALUE)
        first.putLong("long", Long.MAX_VALUE)
        first.putFloat("float", 0.125f)
        first.putString("json", "[\"AddTowerAccess\",\"ä🌍\"]")
        first.prefs.putDouble("double", -0.0)
        node.put("unknown", "000123")
        node.flush()
        val second = Preferences(DesktopPreferenceStore(JavaPreferences.userRoot().node(node.absolutePath())))
        assertTrue(second.getBoolean("bool", false))
        assertEquals(Int.MIN_VALUE, second.getInt("int", 0))
        assertEquals(Long.MAX_VALUE, second.getLong("long", 0))
        assertEquals(0.125f, second.getFloat("float", 0f))
        assertEquals((-0.0).toRawBits(), second.prefs.getDouble("double", 1.0).toRawBits())
        assertEquals(first.getString("json", ""), second.getString("json", ""))
        assertEquals(first.all, second.all)
        assertEquals("000123", second.all["unknown"])
        assertEquals(Long.MAX_VALUE.toString(), second.all["long"])
    }

    @Test fun `empty desktop store returns existing defaults without writing them`() = store { node ->
        val prefs = Preferences(DesktopPreferenceStore(node))
        assertFalse(prefs.expertMode)
        assertFalse(prefs.overrideCountryRestrictions)
        assertEquals(Theme.SYSTEM, prefs.theme)
        assertEquals(Autosync.ON, prefs.autosync)
        assertEquals(-1L, prefs.userId)
        assertEquals(0L, prefs.selectedEditTypePreset)
        assertNull(prefs.oAuth2AccessToken)
        assertNull(prefs.lastDataVersion)
        assertTrue(prefs.all.isEmpty())
    }

    @Test fun `quest compatibility access uses initialized desktop wrapper and same persistent store`() = store { node ->
        val field = Prefs::class.java.getDeclaredField("preferences").apply { isAccessible = true }
        val previous = field.get(Prefs)
        val app = koinApplication {
            modules(preferencesModule, module { single<SettingsWithSnapshot> { DesktopPreferenceStore(node) } })
        }
        try {
            val quest = object : QuestType {
                override val icon get() = error("No resources needed")
                override val title get() = error("No resources needed")
                override val wikiLink: String? = null
            }
            assertSame(app.koin.get<Preferences>(), quest.prefs)
            quest.prefs.expertMode = true
            assertTrue(Preferences(DesktopPreferenceStore(node)).expertMode)
            assertFailsWith<ClassNotFoundException> { Class.forName("android.content.SharedPreferences") }
        } finally {
            app.close()
            field.set(Prefs, previous)
        }
    }

    @Test fun `desktop data version update is repeatable and does not rename unrelated data`() = store { node ->
        val prefs = Preferences(DesktopPreferenceStore(node))
        prefs.putString("qs_AddTowerAccess_filter", "preserved")
        assertTrue(DesktopPreferencesMigration(prefs).migrate("b2-test"))
        val after = prefs.all
        val again = Preferences(DesktopPreferenceStore(node))
        assertFalse(DesktopPreferencesMigration(again).migrate("b2-test"))
        assertEquals(after, again.all)
        assertEquals("preserved", again.getString("qs_AddTowerAccess_filter", ""))
    }

    @Test fun `values beyond the java prefs limit are stored in parts and read back exactly`() = store { node ->
        // unicode around the part boundary (8000) must not be damaged
        val long = "a".repeat(7_999) + "🌍ä" + (0 until 30_000).joinToString("") { ('a' + it % 26).toString() }
        val prefs = Preferences(DesktopPreferenceStore(node))
        prefs.putString("sources", long)
        assertEquals(long, prefs.getString("sources", ""))
        // survives a new instance on the same node, and is listed once under its real name
        val again = Preferences(DesktopPreferenceStore(JavaPreferences.userRoot().node(node.absolutePath())))
        assertEquals(long, again.getString("sources", ""))
        assertEquals(setOf("sources"), again.all.keys)
        assertEquals(long, again.all["sources"])
        // shrinking and overwriting leaves no stale parts behind
        again.putString("sources", "short")
        assertEquals("short", again.getString("sources", ""))
        assertEquals(setOf("sources"), again.all.keys)
        again.putString("sources", long)
        again.remove("sources")
        assertNull(again.prefs.getStringOrNull("sources"))
        assertTrue(again.all.isEmpty())
    }

    @Test fun `a missing part is a missing value, not a truncated one`() = store { node ->
        val prefs = Preferences(DesktopPreferenceStore(node))
        node.put("k", "@@scee-parts:3@@")
        node.put("k.__part.0", "x".repeat(8000))
        node.put("k.__part.2", "x".repeat(4000))
        assertEquals("fallback", prefs.getString("k", "fallback"))
    }

    @Test fun `keys beyond the java prefs limit work and keep their name`() = store { node ->
        val key = "EditTagsAdapter_" + "amenity/place_of_worship/".repeat(5) + "_keys"
        val prefs = Preferences(DesktopPreferenceStore(node))
        prefs.putString(key, "v")
        prefs.putString("$key-other", "y".repeat(9000))
        assertEquals("v", prefs.getString(key, ""))
        assertTrue(prefs.contains(key))
        assertEquals(setOf(key, "$key-other"), prefs.all.keys)
        assertEquals("y".repeat(9000), prefs.getString("$key-other", ""))
        prefs.remove(key)
        assertFalse(prefs.contains(key))
    }

    @Test fun `listeners see the complete long value`() = store { node ->
        val prefs = Preferences(DesktopPreferenceStore(node))
        val seen = java.util.concurrent.atomic.AtomicReference<String?>()
        val listener = prefs.prefs.addStringOrNullListener("big") { seen.set(it) }
        prefs.putString("big", "z".repeat(25_000))
        // java.util.prefs delivers change events asynchronously
        val deadline = System.currentTimeMillis() + 5_000
        while (seen.get() == null && System.currentTimeMillis() < deadline) Thread.sleep(20)
        listener.deactivate()
        assertEquals("z".repeat(25_000), seen.get())
    }

    @Test fun `real backend keeps corrupt evidence while put and remove repair the entry`() = store { node ->
        val settings = DesktopPreferenceStore(node)
        settings.putString("broken", "original")
        settings.putString("healthy", "retained")
        val index = node.node("scee-values-v2").childrenNames().single { digest ->
            val entry = node.node("scee-values-v2/$digest")
            entry.node(entry.get("current", "")).get("key.0", "") == "broken"
        }.let { node.node("scee-values-v2/$it") }
        val damaged = index.get("current", "")
        index.node(damaged).remove("complete")
        assertEquals(mapOf("healthy" to "retained"), settings.all)
        settings.putString("broken", "recovered")
        assertEquals("recovered", DesktopPreferenceStore(node).getStringOrNull("broken"))
        assertTrue(index.nodeExists(damaged))
        val replacement = index.get("current", "").substringBefore(':')
        index.node(replacement).remove("complete")
        settings.remove("broken")
        assertNull(DesktopPreferenceStore(node).getStringOrNull("broken"))
        assertEquals("original", index.node(damaged).get("value.0", null))
        assertTrue(index.nodeExists(replacement))
    }

    private fun store(test: (JavaPreferences) -> Unit) {
        val node = JavaPreferences.userRoot().node("scee-test-${UUID.randomUUID()}")
        try {
            test(node)
        } finally {
            node.removeNode()
            JavaPreferences.userRoot().flush()
        }
    }
}
