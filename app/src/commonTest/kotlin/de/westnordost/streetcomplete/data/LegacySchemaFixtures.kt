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

/** Historical onCreate from 4d919e5376 (24), 00451c14e5 (23) and its parent (22).
 * Referenced table definitions are unchanged between these commits, except the two additions
 * guarded below (verified with git diff); no current configurator is called by this fixture. */
internal object LegacySchemaFixtures {
    fun create(db: Database, version: Int, upstreamOnly: Boolean = false) {
        // OSM notes
        db.exec(NoteTable.CREATE)
        db.exec(NoteTable.SPATIAL_INDEX_CREATE)

        // changes made on OSM notes
        db.exec(NoteEditsTable.CREATE)
        if (version >= 24) db.exec(NoteCreationAttemptsTable.CREATE)
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

        if (!upstreamOnly) createSceeTables(db, version)
        db.exec("PRAGMA user_version = $version")
    }

    private fun createSceeTables(db: Database, version: Int) {
        // create some EE tables if not existing
        // this is to avoid actual db upgrade to keep compatibility with upstream

        if (version >= 23) db.exec(de.westnordost.streetcomplete.data.osm.osmquests.WaterwayCrossingQuestTable.CREATE)
        if (version >= 23) db.exec(de.westnordost.streetcomplete.data.osm.osmquests.WaterwayCrossingQuestTable.INDEX_CREATE)
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
