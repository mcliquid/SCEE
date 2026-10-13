package de.westnordost.streetcomplete.data

import de.westnordost.streetcomplete.data.osm.edits.ElementEditsDao
import de.westnordost.streetcomplete.data.osm.edits.waterway_crossing.WaterwayCrossingTestData
import de.westnordost.streetcomplete.data.osm.mapdata.*
import de.westnordost.streetcomplete.data.osm.osmquests.*
import de.westnordost.streetcomplete.screens.settings.LAST_KNOWN_DB_VERSION
import de.westnordost.streetcomplete.testutils.TestElementEditType
import de.westnordost.streetcomplete.testutils.edit
import de.westnordost.streetcomplete.testutils.p
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseInitializationPhase2Test : StreetCompleteDatabaseTestCase() {
    private lateinit var db: Database
    override fun onDatabaseInitialized(database: Database) { db = database }

    @Test fun `version 22 upgrade passes application startup guard and preserves existing data`() {
        // Recreate the v22 schema only inside this isolated test database.
        db.exec("DROP TABLE ${WaterwayCrossingQuestTable.NAME}")
        db.exec("PRAGMA user_version = 22")
        val f = WaterwayCrossingTestData()
        NodeDao(db).putAll(f.data.nodes.toList())
        WayDao(db).putAll(f.data.ways.toList())
        db.insert("downloaded_tiles", listOf("x" to 123, "y" to 456, "date" to 1000))
        db.insert("quest_presets", listOf("quest_preset_id" to 7, "quest_preset_name" to "Survey settings"))
        val quest = BasicOsmQuestDaoEntry(ElementType.WAY, f.road.id, "AddWaterwayCrossing", p())
        db.insert(OsmQuestTable.NAME, listOf("element_type" to "WAY", "element_id" to quest.elementId,
            "quest_type" to quest.questTypeName, "latitude" to 0.0, "longitude" to 0.0))
        OsmQuestsHiddenDao(db).add(quest.key)
        val edits = ElementEditsDao(db, AllEditTypes(emptyList(), listOf(TestElementEditType)),
            TestElementEditType, TestElementEditType)
        val pending = edit(action = f.bridge)
        edits.put(pending)
        val before = snapshotExistingTables()

        db.initialize(StreetCompleteDatabaseConfigurator)
        // The exact guard used by StreetCompleteApplication.onCreate, after DI preferences.
        require(StreetCompleteDatabaseConfigurator.version == LAST_KNOWN_DB_VERSION.toInt()) {
            "update database import/export"
        }
        assertEquals(23, db.rawQuery("PRAGMA user_version") { it.getInt("user_version") }.single())
        assertEquals(before, snapshotExistingTables())
        assertEquals(pending, edits.get(pending.id))
        assertEquals(1, edits.getUnsyncedCount())
        assertEquals(quest, OsmQuestDao(db).get(quest.key))
        assertEquals(listOf(quest.key), OsmQuestsHiddenDao(db).getAll().map { it.key })
        val crossing = quest.copy(discriminator = "0:0")
        OsmQuestDao(db).put(crossing)
        assertEquals(crossing, OsmQuestDao(db).get(crossing.key))
        assertTrue(db.rawQuery("PRAGMA integrity_check") { it.getString("integrity_check") }.all { it == "ok" })
        // Reopening an already upgraded DB must not change persisted state either.
        val upgraded = snapshotExistingTables()
        db.initialize(StreetCompleteDatabaseConfigurator)
        assertEquals(upgraded, snapshotExistingTables())
    }

    private fun snapshotExistingTables(): Map<String, List<String>> = db.rawQuery(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name != '${WaterwayCrossingQuestTable.NAME}'"
    ) { it.getString("name") }.associateWith { table ->
        val columns = db.tableInfo(table).joinToString(" || '|' || ") { "quote(\"${it.name}\")" }
        db.rawQuery("SELECT $columns AS snapshot FROM \"$table\" ORDER BY snapshot") { it.getString("snapshot") }
    }
}
