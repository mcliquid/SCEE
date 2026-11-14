package de.westnordost.streetcomplete.data

import de.westnordost.streetcomplete.data.download.tiles.DownloadedTilesTable
import de.westnordost.streetcomplete.data.externalsource.ExternalSourceQuestTables
import de.westnordost.streetcomplete.data.logs.LogsTable
import de.westnordost.streetcomplete.data.osm.created_elements.CreatedElementsTable
import de.westnordost.streetcomplete.data.osm.edits.EditElementsTable
import de.westnordost.streetcomplete.data.osm.edits.ElementEditsTable
import de.westnordost.streetcomplete.data.osm.edits.ElementIdProviderTable
import de.westnordost.streetcomplete.data.osm.edits.upload.changesets.OpenChangesetsTable
import de.westnordost.streetcomplete.data.osm.geometry.RelationGeometryTable
import de.westnordost.streetcomplete.data.osm.geometry.WayGeometryTable
import de.westnordost.streetcomplete.data.osm.mapdata.NodeTable
import de.westnordost.streetcomplete.data.osm.mapdata.RelationTables
import de.westnordost.streetcomplete.data.osm.mapdata.WayTables
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuestTable
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuestsHiddenTable
import de.westnordost.streetcomplete.data.osmcal.CalendarEventsTable
import de.westnordost.streetcomplete.data.osmnotes.NoteTable
import de.westnordost.streetcomplete.data.osmnotes.edits.NoteEditsTable
import de.westnordost.streetcomplete.data.osmnotes.edits.NoteCreationAttemptsTable
import de.westnordost.streetcomplete.data.osmnotes.notequests.NoteQuestsHiddenTable
import de.westnordost.streetcomplete.data.presets.EditTypePresetsTable
import de.westnordost.streetcomplete.data.user.achievements.UserAchievementsTable
import de.westnordost.streetcomplete.data.user.achievements.UserLinksTable
import de.westnordost.streetcomplete.data.user.statistics.ActiveDatesTable
import de.westnordost.streetcomplete.data.user.statistics.CountryStatisticsTable
import de.westnordost.streetcomplete.data.user.statistics.EditTypeStatisticsTable
import de.westnordost.streetcomplete.data.visiblequests.QuestTypeOrderTable
import de.westnordost.streetcomplete.data.visiblequests.VisibleEditTypeTable
import de.westnordost.streetcomplete.quests.osmose.OsmoseTable
import de.westnordost.streetcomplete.util.logs.Log

object StreetCompleteDatabaseConfigurator : DatabaseConfigurator {
    // File-format gate: legacy software must refuse databases with independent migration tracks.
    override val version = 25
    internal const val UPSTREAM_VERSION = 22
    private val forkMigrations: List<(Database) -> Unit> = listOf(
        { createSceeTables(it) }, // fork:1, formerly user_version 23
        { it.exec(NoteCreationAttemptsTable.CREATE) }, // fork:2, formerly user_version 24
    )

    override fun migrate(db: Database, oldVersion: Int) {
        if (oldVersion == 0) {
            onCreate(db)
            SchemaTracks.create(db, UPSTREAM_VERSION, forkMigrations.size)
        } else {
            val tracked = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf(SchemaTracks.TABLE)) { it.getString("name") }.isNotEmpty()
            if (!tracked) {
                check(oldVersion in 22..24) {
                    "Cannot safely identify legacy schema $oldVersion; migration tracks are missing"
                }
                val fork = (oldVersion - 22).coerceAtLeast(0)
                validateSchema(db, fork)
                check(fork >= 1 || db.tableInfo(de.westnordost.streetcomplete.data.osm.osmquests.WaterwayCrossingQuestTable.NAME).isEmpty()) {
                    "Legacy schema marker disagrees with crossing migration; refusing to guess"
                }
                check(fork >= 2 || db.tableInfo(NoteCreationAttemptsTable.NAME).isEmpty()) {
                    "Legacy schema marker disagrees with note upload journal; refusing to guess"
                }
                SchemaTracks.create(db, 22, fork)
            } else {
                check(oldVersion == version) { "Migration tracks disagree with database file-format marker" }
                val progress = SchemaTracks.read(db)
                validateSchema(db, progress.getValue("fork"))
            }
            SchemaTracks.migrate(db, UPSTREAM_VERSION, ::onUpgrade, forkMigrations)
        }
        validateSchema(db, forkMigrations.size)
    }

    private fun validateSchema(db: Database, fork: Int) {
        val required = listOf(NoteTable.NAME, NoteEditsTable.NAME, ElementEditsTable.NAME,
            EditElementsTable.NAME, ElementIdProviderTable.NAME, NodeTable.NAME, WayTables.NAME,
            WayTables.NAME_NODES, RelationTables.NAME, RelationTables.NAME_MEMBERS,
            WayGeometryTable.NAME, RelationGeometryTable.NAME, CreatedElementsTable.NAME,
            VisibleEditTypeTable.NAME, QuestTypeOrderTable.NAME, EditTypePresetsTable.NAME,
            OsmQuestTable.NAME, OsmQuestsHiddenTable.NAME, NoteQuestsHiddenTable.NAME,
            OpenChangesetsTable.NAME, DownloadedTilesTable.NAME, LogsTable.NAME,
            CalendarEventsTable.TABLE, UserAchievementsTable.NAME, UserLinksTable.NAME,
            ActiveDatesTable.NAME, CountryStatisticsTable.NAME, CountryStatisticsTable.NAME_CURRENT_WEEK,
            EditTypeStatisticsTable.NAME, EditTypeStatisticsTable.NAME_CURRENT_WEEK)
        for (table in required) check(db.tableInfo(table).isNotEmpty()) { "Missing legacy table $table" }
        check(db.tableInfo(ElementEditsTable.NAME).map { it.name }.toSet() == setOf(
            "id", "quest_type", "geometry", "source", "latitude", "longitude", "created", "synced", "action", "is_near")) {
            "Unknown element edit schema; refusing destructive recovery"
        }
        if (fork >= 1) {
            check(db.tableInfo(de.westnordost.streetcomplete.data.osm.osmquests.WaterwayCrossingQuestTable.NAME)
                .map { it.name }.toSet() == setOf("quest_type", "element_id", "element_type", "latitude", "longitude", "crossing_id")) { "Fork crossing migration is marked complete but its table is missing" }
            check(db.tableInfo(ExternalSourceQuestTables.NAME_EDITS).isNotEmpty()) { "Missing external-source edit links" }
        }
        if (fork >= 2) {
            val journal = db.tableInfo(NoteCreationAttemptsTable.NAME)
            check(journal.map { it.name }.toSet() == setOf("edit_id", "text", "note_id") &&
                journal.single { it.name == "edit_id" }.primaryKey == 1) {
                "Fork note journal migration is marked complete but its schema is inconsistent"
            }
        }
    }

    override fun onCreate(db: Database) {
        // OSM notes
        db.exec(NoteTable.CREATE)
        db.exec(NoteTable.SPATIAL_INDEX_CREATE)

        // changes made on OSM notes
        db.exec(NoteEditsTable.CREATE)
        db.exec(NoteCreationAttemptsTable.CREATE)
        db.exec(NoteEditsTable.SPATIAL_INDEX_CREATE)
        db.exec(NoteEditsTable.NOTE_ID_INDEX_CREATE)

        // OSM map data
        db.exec(WayGeometryTable.CREATE)
        db.exec(RelationGeometryTable.CREATE)

        db.exec(NodeTable.CREATE)
        db.exec(NodeTable.SPATIAL_INDEX_CREATE)

        db.exec(WayTables.CREATE)
        db.exec(WayTables.NODES_CREATE)
        db.exec(WayTables.NODES_INDEX_CREATE)
        db.exec(WayTables.WAYS_BY_NODE_ID_INDEX_CREATE)

        db.exec(RelationTables.CREATE)
        db.exec(RelationTables.MEMBERS_CREATE)
        db.exec(RelationTables.MEMBERS_INDEX_CREATE)
        db.exec(RelationTables.MEMBERS_BY_ELEMENT_INDEX_CREATE)

        // changes made on OSM map data
        db.exec(ElementEditsTable.CREATE)
        db.exec(ElementIdProviderTable.CREATE)
        db.exec(ElementIdProviderTable.INDEX_CREATE)
        db.exec(ElementIdProviderTable.ELEMENT_INDEX_CREATE)

        db.exec(EditElementsTable.CREATE)
        db.exec(EditElementsTable.INDEX_CREATE)

        db.exec(CreatedElementsTable.CREATE)

        // quests
        db.exec(VisibleEditTypeTable.CREATE)
        db.exec(QuestTypeOrderTable.CREATE)
        db.exec(QuestTypeOrderTable.INDEX_CREATE)
        db.exec(EditTypePresetsTable.CREATE)

        // quests based on OSM elements
        db.exec(OsmQuestTable.CREATE)
        db.exec(OsmQuestTable.SPATIAL_INDEX_CREATE)
        db.exec(OsmQuestsHiddenTable.CREATE)

        // quests based on OSM notes
        db.exec(NoteQuestsHiddenTable.CREATE)

        // for upload / download
        db.exec(OpenChangesetsTable.CREATE)
        db.exec(DownloadedTilesTable.CREATE)

        // user statistics
        db.exec(EditTypeStatisticsTable.create(EditTypeStatisticsTable.NAME))
        db.exec(EditTypeStatisticsTable.create(EditTypeStatisticsTable.NAME_CURRENT_WEEK))
        db.exec(CountryStatisticsTable.create(CountryStatisticsTable.NAME))
        db.exec(CountryStatisticsTable.create(CountryStatisticsTable.NAME_CURRENT_WEEK))
        db.exec(UserAchievementsTable.CREATE)
        db.exec(UserLinksTable.CREATE)
        db.exec(ActiveDatesTable.CREATE)

        // logs
        db.exec(LogsTable.CREATE)
        db.exec(LogsTable.INDEX_CREATE)

        // OSM calendar events
        db.exec(CalendarEventsTable.CREATE)

        createSceeTables(db)
    }

    override fun onUpgrade(db: Database, oldVersion: Int) {
        if (oldVersion < 2) {
            db.exec(CreatedElementsTable.CREATE)
        }
        if (oldVersion < 3) {
            db.exec(QuestTypeOrderTable.CREATE)
            db.exec(QuestTypeOrderTable.INDEX_CREATE)

            db.exec(EditTypePresetsTable.CREATE)

            val oldName = "quest_visibility_old"
            db.exec("ALTER TABLE ${VisibleEditTypeTable.NAME} RENAME TO $oldName;")
            db.exec(VisibleEditTypeTable.CREATE)
            db.exec("""
                INSERT INTO ${VisibleEditTypeTable.NAME} (
                    ${VisibleEditTypeTable.Columns.EDIT_TYPE_PRESET_ID},
                    ${VisibleEditTypeTable.Columns.EDIT_TYPE},
                    ${VisibleEditTypeTable.Columns.VISIBILITY}
                ) SELECT
                    0,
                    ${VisibleEditTypeTable.Columns.EDIT_TYPE},
                    ${VisibleEditTypeTable.Columns.VISIBILITY}
                FROM $oldName;
            """.trimIndent())
            db.exec("DROP TABLE $oldName;")
        }
        if (oldVersion < 4) {
            db.exec("DROP TABLE IF EXISTS new_achievements")
        }
        if (oldVersion < 5) {
            db.exec(NodeTable.SPATIAL_INDEX_CREATE)
            db.exec(WayGeometryTable.CREATE)
            db.exec(RelationGeometryTable.CREATE)
            val oldGeometryTableName = "elements_geometry"
            val oldTypeName = "element_type"
            val oldIdName = "element_id"
            db.exec("""
                INSERT INTO ${WayGeometryTable.NAME} (
                    ${WayGeometryTable.Columns.ID},
                    ${WayGeometryTable.Columns.GEOMETRY_POLYLINES},
                    ${WayGeometryTable.Columns.GEOMETRY_POLYGONS},
                    ${WayGeometryTable.Columns.CENTER_LATITUDE},
                    ${WayGeometryTable.Columns.CENTER_LONGITUDE}
                ) SELECT
                    $oldIdName,
                    ${WayGeometryTable.Columns.GEOMETRY_POLYLINES},
                    ${WayGeometryTable.Columns.GEOMETRY_POLYGONS},
                    ${WayGeometryTable.Columns.CENTER_LATITUDE},
                    ${WayGeometryTable.Columns.CENTER_LONGITUDE}
                FROM
                    $oldGeometryTableName
                WHERE
                    $oldTypeName = 'WAY';
            """.trimIndent()
            )
            db.exec("""
                INSERT INTO ${RelationGeometryTable.NAME} (
                    ${RelationGeometryTable.Columns.ID},
                    ${RelationGeometryTable.Columns.GEOMETRY_POLYLINES},
                    ${RelationGeometryTable.Columns.GEOMETRY_POLYGONS},
                    ${RelationGeometryTable.Columns.CENTER_LATITUDE},
                    ${RelationGeometryTable.Columns.CENTER_LONGITUDE}
                ) SELECT
                    $oldIdName,
                    ${RelationGeometryTable.Columns.GEOMETRY_POLYLINES},
                    ${RelationGeometryTable.Columns.GEOMETRY_POLYGONS},
                    ${RelationGeometryTable.Columns.CENTER_LATITUDE},
                    ${RelationGeometryTable.Columns.CENTER_LONGITUDE}
                FROM
                    $oldGeometryTableName
                WHERE
                    $oldTypeName = 'RELATION';
            """.trimIndent()
            )
            db.exec("DROP TABLE $oldGeometryTableName;")
        }
        if (oldVersion < 6) {
            db.tryExec("ALTER TABLE ${NoteEditsTable.NAME} ADD COLUMN ${NoteEditsTable.Columns.TRACK} text DEFAULT '[]' NOT NULL")
        }
        if (oldVersion < 7) {
            db.exec(EditTypeStatisticsTable.create(EditTypeStatisticsTable.NAME_CURRENT_WEEK))
            db.exec(CountryStatisticsTable.create(CountryStatisticsTable.NAME_CURRENT_WEEK))
            db.exec(ActiveDatesTable.CREATE)
        }
        if (oldVersion < 8) {
            db.delete(ElementEditsTable.NAME, "${ElementEditsTable.Columns.QUEST_TYPE} = 'AddShoulder'", null)
        }
        if (oldVersion < 9) {
            db.renameQuest("AddPicnicTableCover", "AddAmenityCover")
            db.renameValue(ElementEditsTable.NAME, ElementEditsTable.Columns.QUEST_TYPE,"ExternalQuest", "CustomQuest")
        }
        if (oldVersion < 10) {
            db.exec("DROP TABLE ${DownloadedTilesTable.NAME};")
            db.exec(DownloadedTilesTable.CREATE)
        }
        if (oldVersion < 11) {
            db.exec("DROP INDEX osm_element_edits_index")

            // Recreating table (=clearing table) because it would be very complicated to pick the
            // data from the table in the old format and put it into the new format: the fields of
            // the serialized actions all changed
            db.exec("DROP TABLE ${ElementEditsTable.NAME};")
            db.exec(ElementEditsTable.CREATE)

            db.exec(EditElementsTable.CREATE)
            db.exec(EditElementsTable.INDEX_CREATE)

            db.exec(ElementIdProviderTable.ELEMENT_INDEX_CREATE)
        }
        if (oldVersion < 12) {
            db.exec(LogsTable.CREATE)
            db.exec(LogsTable.INDEX_CREATE)
        }
        if (oldVersion < 13) {
            // Deleting all data related to elements and their geometries, since
            // the binary format in which `GEOMETRY_{POLYGONS,POLYLINES}` data from
            // `{Way,Relation}GeometryTable` is stored has changed (see #5307)

            db.exec("DELETE FROM ${RelationTables.NAME_MEMBERS};")
            db.exec("DELETE FROM ${RelationTables.NAME};")

            db.exec("DELETE FROM ${WayTables.NAME_NODES};")
            db.exec("DELETE FROM ${WayTables.NAME};")

            db.exec("DELETE FROM ${NodeTable.NAME};")

            db.exec("DELETE FROM ${RelationGeometryTable.NAME};")
            db.exec("DELETE FROM ${WayGeometryTable.NAME};")

            db.exec("DELETE FROM ${DownloadedTilesTable.NAME};")
        }
        if (oldVersion < 14) {
            db.tryExec("ALTER TABLE ${ElementEditsTable.NAME} ADD COLUMN ${ElementEditsTable.Columns.IS_NEAR_USER_LOCATION} int DEFAULT 1 NOT NULL")
            db.tryExec("ALTER TABLE ${OpenChangesetsTable.NAME} ADD COLUMN ${OpenChangesetsTable.Columns.LAST_POSITION_LATITUDE} double DEFAULT 0 NOT NULL")
            db.tryExec("ALTER TABLE ${OpenChangesetsTable.NAME} ADD COLUMN ${OpenChangesetsTable.Columns.LAST_POSITION_LONGITUDE} double DEFAULT 0 NOT NULL")
        }
        if (oldVersion < 15) {
            db.renameOverlay("ShopsOverlay", "PlacesOverlay")
        }
        if (oldVersion < 16) {
            db.deleteQuest("AddCrossingType")
        }
        if (oldVersion < 17) {
            db.renameQuest("AddProhibitedForMoped", "AddMopedAccess")
        }
        if (oldVersion < 18) {
            db.exec("DROP TABLE IF EXISTS direction_of_flow;")
            db.deleteQuest("AddSuspectedOneway")
        }
        if (oldVersion < 19) {
            db.deleteQuest("AddParcelLockerMailIn")
            db.deleteQuest("AddParcelLockerPickup")
            db.deleteQuest("AddShoulder")
        }
        if (oldVersion < 20) {
            db.exec(CalendarEventsTable.CREATE)
        }
        if (oldVersion < 21) {
            db.deleteQuest("AddPlaygroundAccess")
            db.deleteQuest("AddProhibitedForPedestrians")
            db.deleteQuest("AddTowerAccess")
        }
        if (oldVersion < 22) {
            db.deleteQuest("AddChargingStationCapacity")
            db.deleteQuest("AddChargingStationBicycleCapacity")
        }

    }

    private fun createSceeTables(db: Database) {
        // create some EE tables if not existing
        // this is to avoid actual db upgrade to keep compatibility with upstream

        db.exec(de.westnordost.streetcomplete.data.osm.osmquests.WaterwayCrossingQuestTable.CREATE)
        db.exec(de.westnordost.streetcomplete.data.osm.osmquests.WaterwayCrossingQuestTable.INDEX_CREATE)
        // create other source tables
        db.exec(ExternalSourceQuestTables.CREATE_HIDDEN)
        db.exec(ExternalSourceQuestTables.CREATE_EDITS)

        // create osmose table
        db.exec(OsmoseTable.CREATE_IF_NOT_EXISTS)
        db.exec(OsmoseTable.CREATE_SPATIAL_INDEX_IF_NOT_EXISTS)
        // create osm quests element id index if not existing
        db.exec(OsmQuestTable.CREATE_ELEMENT_ID_INDEX_IF_NOT_EXISTS)
    }
}

/* E.g. when upgrading directly from a version below 10, it will create the ElementEditsTable
   directly in the latest schema because we use the constant from EditElementsTable.CREATE to create
   it.
   But then, the upgrade step to version 13 would fail, because at that point, this table already
   has the "is_near" column.
   So, we don't really care about that and a "IF NOT EXISTS" syntax is not supported for the
   ALTER TABLE statement. */
private fun Database.tryExec(sql: String, args: Array<Any>? = null) = try {
    exec(sql, args)
} catch (e: Exception) {
    Log.w("DatabaseInitializer", e.message ?: "Query failed: $sql")
}

private fun Database.deleteQuest(name: String) {
    deleteValue(ElementEditsTable.NAME, ElementEditsTable.Columns.QUEST_TYPE, name)
    deleteValue(OsmQuestTable.NAME, OsmQuestTable.Columns.QUEST_TYPE, name)
    deleteValue(OsmQuestsHiddenTable.NAME, OsmQuestsHiddenTable.Columns.QUEST_TYPE, name)
    deleteValue(VisibleEditTypeTable.NAME, VisibleEditTypeTable.Columns.EDIT_TYPE, name)
    deleteValue(OpenChangesetsTable.NAME, OpenChangesetsTable.Columns.QUEST_TYPE, name)
    deleteValue(QuestTypeOrderTable.NAME, QuestTypeOrderTable.Columns.BEFORE, name)
    deleteValue(QuestTypeOrderTable.NAME, QuestTypeOrderTable.Columns.AFTER, name)
}

private fun Database.renameQuest(old: String, new: String) {
    renameValue(ElementEditsTable.NAME, ElementEditsTable.Columns.QUEST_TYPE, old, new)
    renameValue(OsmQuestTable.NAME, OsmQuestTable.Columns.QUEST_TYPE, old, new)
    renameValue(OsmQuestsHiddenTable.NAME, OsmQuestsHiddenTable.Columns.QUEST_TYPE, old, new)
    renameValue(VisibleEditTypeTable.NAME, VisibleEditTypeTable.Columns.EDIT_TYPE, old, new)
    renameValue(OpenChangesetsTable.NAME, OpenChangesetsTable.Columns.QUEST_TYPE, old, new)
    renameValue(QuestTypeOrderTable.NAME, QuestTypeOrderTable.Columns.BEFORE, old, new)
    renameValue(QuestTypeOrderTable.NAME, QuestTypeOrderTable.Columns.AFTER, old, new)
}

private fun Database.renameOverlay(old: String, new: String) {
    renameValue(ElementEditsTable.NAME, ElementEditsTable.Columns.QUEST_TYPE, old, new)
    renameValue(OpenChangesetsTable.NAME, OpenChangesetsTable.Columns.QUEST_TYPE, old, new)
}

private fun Database.renameValue(table: String, column: String, oldValue: String, newValue: String) {
    update(table, listOf(column to newValue), "$column = ?", arrayOf(oldValue))
}

private fun Database.deleteValue(table: String, column: String, value: String) {
    delete(table, "$column = ?", arrayOf(value))
}
