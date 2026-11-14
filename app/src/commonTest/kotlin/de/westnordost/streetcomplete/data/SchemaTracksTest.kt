package de.westnordost.streetcomplete.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.westnordost.streetcomplete.data.osm.edits.*
import de.westnordost.streetcomplete.data.osm.edits.create.CreateNodeAction
import de.westnordost.streetcomplete.data.osmnotes.edits.*
import de.westnordost.streetcomplete.data.externalsource.*
import de.westnordost.streetcomplete.data.quest.ExternalSourceQuestKey
import de.westnordost.streetcomplete.testutils.*
import kotlin.test.*

class SchemaTracksTest {
    private fun database(test: (Database) -> Unit) {
        val db = DatabaseImpl(BundledSQLiteDriver().open(":memory:"))
        try { test(db) } finally { db.close() }
    }

    @Test fun `fresh schema and repeated initialization have independent progress`() = database { db ->
        db.initialize(StreetCompleteDatabaseConfigurator)
        assertEquals(mapOf("upstream" to 22, "fork" to 3), SchemaTracks.read(db))
        val before = snapshot(db)
        db.initialize(StreetCompleteDatabaseConfigurator)
        assertEquals(before, snapshot(db))
    }

    @Test fun `historical fork 23 and 24 preserve all existing rows and journals`() {
        for (version in listOf(23, 24)) database { db ->
            LegacySchemaFixtures.create(db, version)
            seed(db, version)
            val before = snapshot(db)
            db.initialize(StreetCompleteDatabaseConfigurator)
            before.forEach { (table, rows) -> assertEquals(rows, snapshot(db)[table], "v$version $table") }
            assertEquals(mapOf("upstream" to 22, "fork" to 3), SchemaTracks.read(db))
            val complete = snapshot(db)
            db.initialize(StreetCompleteDatabaseConfigurator)
            assertEquals(complete, snapshot(db))
        }
    }

    @Test fun `upstream only 22 adds fork structures without changing user data`() = database { db ->
        LegacySchemaFixtures.create(db, 22, upstreamOnly = true)
        val edits = ElementEditsDao(db, AllEditTypes(emptyList(), listOf(TestElementEditType)), TestElementEditType, TestElementEditType)
        val pending = edit(action = CreateNodeAction(p(), mapOf("amenity" to "bench")))
        edits.put(pending)
        ElementIdProviderDao(db).assign(pending.id, 1, 0, 0)
        NoteEditsDao(db).add(noteEdit(action = NoteEditAction.CREATE))
        val before = snapshot(db)
        db.initialize(StreetCompleteDatabaseConfigurator)
        before.forEach { (table, rows) -> assertEquals(rows, snapshot(db)[table]) }
        assertEquals(mapOf("upstream" to 22, "fork" to 3), SchemaTracks.read(db))
    }

    @Test fun `failure after DDL rolls back progress and schema and can retry`() = database { db ->
        LegacySchemaFixtures.create(db, 23)
        seed(db, 23)
        val before = snapshot(db)
        val failing = object : Database by db {
            override fun exec(sql: String, args: Array<Any>?) {
                db.exec(sql, args)
                if (sql.contains("CREATE TABLE osm_note_creation_attempts")) error("injected after journal DDL")
            }
        }
        assertFailsWith<IllegalStateException> { failing.initialize(StreetCompleteDatabaseConfigurator) }
        assertEquals(before, snapshot(db))
        assertEquals(23, db.rawQuery("PRAGMA user_version") { it.getInt("user_version") }.single())
        db.initialize(StreetCompleteDatabaseConfigurator)
        assertEquals(3, SchemaTracks.read(db)["fork"])
    }

    @Test fun `upstream 23 is not skipped because old fork used 23 and 24`() = database { db ->
        LegacySchemaFixtures.create(db, 24)
        db.initialize(StreetCompleteDatabaseConfigurator)
        val future = object : DatabaseConfigurator by StreetCompleteDatabaseConfigurator {
            override fun migrate(db: Database, oldVersion: Int) {
                SchemaTracks.migrate(db, 23, { database, old ->
                    assertEquals(22, old)
                    database.exec("CREATE TABLE upstream_23 (id INTEGER)")
                }, listOf({}, {}, {}))
            }
        }
        db.initialize(future)
        assertEquals(mapOf("upstream" to 23, "fork" to 3), SchemaTracks.read(db))
        db.initialize(future) // duplicate DDL would fail
        assertTrue(db.tableInfo("upstream_23").isNotEmpty())
        assertFailsWith<IllegalStateException> { db.initialize(StreetCompleteDatabaseConfigurator) }
    }

    @Test fun `future migration failure cannot advance either track`() = database { db ->
        db.initialize(StreetCompleteDatabaseConfigurator)
        val before = snapshot(db)
        val future = object : DatabaseConfigurator by StreetCompleteDatabaseConfigurator {
            override fun migrate(db: Database, oldVersion: Int) {
                SchemaTracks.migrate(db, 23, { database, _ -> database.exec("CREATE TABLE upstream_23 (id INTEGER)") },
                    listOf({}, {}, {}, { it.exec("CREATE TABLE fork_3 (id INTEGER)"); error("injected") }))
            }
        }
        assertFailsWith<IllegalStateException> { db.initialize(future) }
        assertEquals(before, snapshot(db))
    }

    @Test fun `missing journal unknown track and unmarked format fail without data changes`() {
        for (damage in listOf<(Database) -> Unit>(
            { it.exec("DROP TABLE osm_note_creation_attempts") },
            { it.exec("UPDATE scee_schema_tracks SET version=99 WHERE track='fork'") },
            { it.exec("DELETE FROM scee_schema_tracks WHERE track='upstream'") },
            { it.exec("DROP TABLE scee_schema_tracks") },
        )) database { db ->
            db.initialize(StreetCompleteDatabaseConfigurator)
            damage(db)
            val before = snapshot(db)
            assertFailsWith<IllegalStateException> { db.initialize(StreetCompleteDatabaseConfigurator) }
            assertEquals(before, snapshot(db))
        }
    }

    @Test fun `ambiguous legacy version never guesses upstream identity`() = database { db ->
        LegacySchemaFixtures.create(db, 22, upstreamOnly = true)
        db.exec("PRAGMA user_version=23")
        val before = snapshot(db)
        assertFailsWith<IllegalStateException> { db.initialize(StreetCompleteDatabaseConfigurator) }
        assertEquals(before, snapshot(db))
    }

    @Test fun `B11 tracked database upgrades fork 2 to review fork 3 with DDL rollback`() = database { db ->
        LegacySchemaFixtures.create(db, 24)
        seed(db, 24)
        SchemaTracks.create(db, 22, 2)
        db.exec("PRAGMA user_version=25")
        val before = snapshot(db)
        val failing = object : Database by db {
            override fun exec(sql: String, args: Array<Any>?) {
                db.exec(sql, args)
                if (sql.contains("CREATE TABLE osm_element_edit_reviews")) error("injected review DDL failure")
            }
        }
        assertFailsWith<IllegalStateException> { failing.initialize(StreetCompleteDatabaseConfigurator) }
        assertEquals(before, snapshot(db))
        db.initialize(StreetCompleteDatabaseConfigurator)
        assertEquals(mapOf("upstream" to 22, "fork" to 3), SchemaTracks.read(db))
        before.filterKeys { it != SchemaTracks.TABLE }.forEach { (table, rows) -> assertEquals(rows, snapshot(db)[table]) }
    }

    private fun seed(db: Database, version: Int) {
        val edits = ElementEditsDao(db, AllEditTypes(emptyList(), listOf(TestElementEditType)), TestElementEditType, TestElementEditType)
        val edit = edit(action = CreateNodeAction(p(), mapOf("amenity" to "bench")))
        edits.put(edit)
        ElementIdProviderDao(db).assign(edit.id, 1, 0, 0)
        EditElementsDao(db).put(edit.id, edit.action.elementKeys)
        ExternalSourceDao(db).addElementEdit(ExternalSourceQuestKey("issue", "osmose"), edit.id)
        NoteEditsDao(db).add(noteEdit(action = NoteEditAction.CREATE))
        if (version >= 24) db.insert(NoteCreationAttemptsTable.NAME, listOf("edit_id" to 1, "text" to "uncertain", "note_id" to null))
        db.insert("quest_presets", listOf("quest_preset_id" to 7, "quest_preset_name" to "keep"))
    }

    private fun snapshot(db: Database) = db.rawQuery(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
    ) { it.getString("name") }.associateWith { table ->
        val columns = db.tableInfo(table).joinToString(" || '|' || ") { "quote(\"${it.name}\")" }
        db.rawQuery("SELECT $columns AS value FROM \"$table\" ORDER BY value") { it.getString("value") }
    }
}
