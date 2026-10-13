package de.westnordost.streetcomplete.data.osm.osmquests

import de.westnordost.streetcomplete.data.CursorPosition
import de.westnordost.streetcomplete.data.Database
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.osm.mapdata.ElementKey
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuestTable.Columns.ELEMENT_ID
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuestTable.Columns.ELEMENT_TYPE
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuestTable.Columns.LATITUDE
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuestTable.Columns.LONGITUDE
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuestTable.Columns.QUEST_TYPE
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuestTable.NAME
import de.westnordost.streetcomplete.data.quest.OsmQuestKey
import de.westnordost.streetcomplete.util.Mockable

/** Persists OsmQuest objects, or more specifically, OsmQuestEntry objects */
@Mockable
class OsmQuestDao(private val db: Database) {

    fun put(quest: OsmQuestDaoEntry) {
        db.replace(quest.table(), quest.toPairs())
    }

    fun get(key: OsmQuestKey): OsmQuestDaoEntry? =
        db.queryOne(key.table(),
            where = "$ELEMENT_TYPE = ? AND $ELEMENT_ID = ? AND $QUEST_TYPE = ?" + key.crossingWhere(),
            args = key.args()
        ) { it.toOsmQuestEntry(if (key.discriminator.isEmpty()) "" else it.getString(WaterwayCrossingQuestTable.CROSSING_ID)) }

    fun delete(key: OsmQuestKey): Boolean =
        db.delete(key.table(),
            where = "$ELEMENT_TYPE = ? AND $ELEMENT_ID = ? AND $QUEST_TYPE = ?" + key.crossingWhere(),
            args = key.args()
        ) == 1

    fun putAll(quests: Collection<OsmQuestDaoEntry>) {
        if (quests.isEmpty()) return
        for ((table, group) in quests.groupBy { it.table() }) {
            val columns = arrayOf(QUEST_TYPE, ELEMENT_TYPE, ELEMENT_ID, LATITUDE, LONGITUDE)
            val values = group.map { q -> arrayOf<Any?>(q.questTypeName, q.elementType.name, q.elementId, q.position.latitude, q.position.longitude) }
            db.replaceMany(table,
                if (table == NAME) columns else columns + WaterwayCrossingQuestTable.CROSSING_ID,
                values.mapIndexed { i, row -> if (table == NAME) row else row + group[i].discriminator })
        }
    }

    fun getAllForElements(keys: Collection<ElementKey>): List<OsmQuestDaoEntry> {
        if (keys.isEmpty()) return emptyList()
        return listOf(NAME, WaterwayCrossingQuestTable.NAME).flatMap { table ->
            db.query(table, where = "$ELEMENT_ID IN (${keys.map { it.id }.joinToString(",")})") {
                it.toOsmQuestEntry(if (table == NAME) "" else it.getString(WaterwayCrossingQuestTable.CROSSING_ID))
            }
        }.filter { ElementKey(it.elementType, it.elementId) in keys }
    }

    fun getAllInBBox(bounds: BoundingBox, questTypes: Collection<String>? = null): List<OsmQuestDaoEntry> {
        var builder = inBoundsSql(bounds)
        if (questTypes != null) {
            if (questTypes.isEmpty()) return emptyList()
            val questTypesStr = questTypes.joinToString(",") { "'$it'" }
            builder += " AND $QUEST_TYPE IN ($questTypesStr)"
        }
        return listOf(NAME, WaterwayCrossingQuestTable.NAME).flatMap { table ->
            db.query(table, where = builder) { it.toOsmQuestEntry(if (table == NAME) "" else it.getString(WaterwayCrossingQuestTable.CROSSING_ID)) }
        }
    }

    fun deleteAll(keys: Collection<OsmQuestKey>) {
        if (keys.isEmpty()) return
        db.transaction {
            for (key in keys) {
                delete(key)
            }
        }
    }

    fun clear() {
        db.delete(NAME)
        db.delete(WaterwayCrossingQuestTable.NAME)
    }
}

private fun inBoundsSql(bbox: BoundingBox): String = """
    ($LATITUDE BETWEEN ${bbox.min.latitude} AND ${bbox.max.latitude}) AND
    ($LONGITUDE BETWEEN ${bbox.min.longitude} AND ${bbox.max.longitude})
""".trimIndent()

private fun CursorPosition.toOsmQuestEntry(discriminator: String = ""): OsmQuestDaoEntry = BasicOsmQuestDaoEntry(
    ElementType.valueOf(getString(ELEMENT_TYPE)),
    getLong(ELEMENT_ID),
    getString(QUEST_TYPE),
    LatLon(getDouble(LATITUDE), getDouble(LONGITUDE)), discriminator
)

private fun OsmQuestDaoEntry.toPairs() = listOf(
    QUEST_TYPE to questTypeName,
    ELEMENT_TYPE to elementType.name,
    ELEMENT_ID to elementId,
    LATITUDE to position.latitude,
    LONGITUDE to position.longitude
) + if (discriminator.isEmpty()) emptyList() else listOf(WaterwayCrossingQuestTable.CROSSING_ID to discriminator)

data class BasicOsmQuestDaoEntry(
    override val elementType: ElementType,
    override val elementId: Long,
    override val questTypeName: String,
    override val position: LatLon,
    override val discriminator: String = ""
) : OsmQuestDaoEntry

private fun OsmQuestDaoEntry.table() = if (discriminator.isEmpty()) NAME else WaterwayCrossingQuestTable.NAME
private fun OsmQuestKey.table() = if (discriminator.isEmpty()) NAME else WaterwayCrossingQuestTable.NAME
private fun OsmQuestKey.crossingWhere() = if (discriminator.isEmpty()) "" else " AND ${WaterwayCrossingQuestTable.CROSSING_ID} = ?"
private fun OsmQuestKey.args(): Array<Any> =
    if (discriminator.isEmpty()) arrayOf(elementType.name, elementId, questTypeName)
    else arrayOf(elementType.name, elementId, questTypeName, discriminator)
