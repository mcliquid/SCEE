package de.westnordost.streetcomplete

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.westnordost.streetcomplete.testutils.SnapshotMapSettings as MapSettings
import de.westnordost.streetcomplete.data.Database
import de.westnordost.streetcomplete.data.DatabaseImpl
import de.westnordost.streetcomplete.data.StreetCompleteDatabaseConfigurator
import de.westnordost.streetcomplete.data.initialize
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.presets.EditTypePresetsController
import de.westnordost.streetcomplete.data.presets.EditTypePresetsDao
import de.westnordost.streetcomplete.data.visiblequests.VisibleEditTypeDao
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreferencesQuestStorageCompatibilityTest {
    @Test fun `persisted preset id resolves database name after controller recreation`() = database { db ->
        db.insert("quest_presets", listOf("quest_preset_id" to 7L, "quest_preset_name" to "Rad – Alltag"))
        val settings = MapSettings().apply { putLong("selectedQuestsPreset", 7L) }
        val first = EditTypePresetsController(EditTypePresetsDao(db), Preferences(settings))
        assertEquals("Rad – Alltag", first.selectedEditTypePresetName)
        val second = EditTypePresetsController(EditTypePresetsDao(db), Preferences(settings))
        assertEquals(7L, second.selectedId)
        assertEquals("Rad – Alltag", second.selectedEditTypePresetName)
    }

    @Test fun `activation overrides are database rows scoped by preset and exact quest name`() = database { db ->
        db.insert("quest_visibility", listOf("quest_preset_id" to 7L, "quest_type" to "AddPlaygroundAccess", "visibility" to 0))
        val dao = VisibleEditTypeDao(db)
        assertFalse(dao.get(7L, "AddPlaygroundAccess"))
        assertTrue(dao.get(8L, "AddPlaygroundAccess"))
        assertTrue(dao.get(7L, "AddGeneralAccess"))
        assertEquals(mapOf("AddPlaygroundAccess" to false), VisibleEditTypeDao(db).getAll(7L))
    }

    private fun database(block: (Database) -> Unit) {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            block(DatabaseImpl(connection).apply { initialize(StreetCompleteDatabaseConfigurator) })
        } finally {
            connection.close()
        }
    }
}
