package de.westnordost.streetcomplete

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.russhwolf.settings.ObservableSettings
import de.westnordost.streetcomplete.data.preferences.AndroidPreferenceStore
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.preferences.SettingsWithSnapshot
import de.westnordost.streetcomplete.quests.general_access.AddGeneralAccess
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PreferencesPlatformTest {
    @Test fun `instance snapshots preserve unknown native types and double bits across recreation`() {
        val context = RuntimeEnvironment.getApplication()
        val raw = context.getSharedPreferences("kmp_b2_native", Context.MODE_PRIVATE)
        raw.edit().clear().putBoolean("bool", true).putInt("int", Int.MIN_VALUE)
            .putLong("long", Long.MAX_VALUE).putFloat("float", 0.125f)
            .putString("json", "[\"AddPlaygroundAccess\",\"ä🌍\"]")
            .putStringSet("unknown-set", setOf("AddTowerAccess", "漢字"))
            .putLong("bits", (-0.0).toRawBits()).commit()
        val before = raw.all
        val first = Preferences(AndroidPreferenceStore(raw))
        assertEquals(before, first.all)
        first.expertMode = true
        assertTrue(raw.getBoolean(Prefs.EXPERT_MODE, false))
        val second = Preferences(AndroidPreferenceStore(context.getSharedPreferences("kmp_b2_native", 0)))
        assertEquals(raw.all, second.all)
        assertIs<Long>(second.all["bits"])
        assertEquals((-0.0).toRawBits(), second.all["bits"])
        assertEquals(setOf("AddTowerAccess", "漢字"), second.all["unknown-set"])
        assertEquals(before, second.all - Prefs.EXPERT_MODE)
    }

    @Test fun `production Koin eagerly initializes singleton bridge before quest consumers`() {
        val field = Prefs::class.java.getDeclaredField("preferences").apply { isAccessible = true }
        val previous = field.get(Prefs)
        val context = RuntimeEnvironment.getApplication()
        val raw = context.getSharedPreferences(context.packageName + "_preferences", 0)
        raw.edit().clear().putBoolean(Prefs.EXPERT_MODE, true)
            .putString("lastVersion_data", "old").putString("qs_AddTowerAccess_filter", "nodes").commit()
        val app = koinApplication { androidContext(context); modules(androidModule, commonModule) }
        try {
            // Read the quest bridge before explicitly requesting the Preferences singleton.
            val questPrefs = AddGeneralAccess().prefs
            assertSame(app.koin.get<Preferences>(), questPrefs)
            assertSame(app.koin.get<SettingsWithSnapshot>(), app.koin.get<ObservableSettings>())
            assertSame(raw, app.koin.get<SharedPreferences>())
            assertTrue(questPrefs.expertMode)
            assertEquals("old", questPrefs.lastDataVersion) // registering services does not migrate early
            assertTrue(app.koin.get<PreferencesMigration>().migrate("b2-test"))
            assertEquals("nodes", questPrefs.getString("qs_AddGeneralAccess_filter", "missing"))
            assertEquals("b2-test", raw.getString("lastVersion_data", null))
            assertFalse(app.koin.get<PreferencesMigration>().migrate("b2-test"))
            questPrefs.putInt("wrapper-write", 42)
            assertEquals(42, raw.getInt("wrapper-write", 0))
        } finally {
            app.close()
            field.set(Prefs, previous)
            raw.edit().clear().commit()
        }
    }
}
