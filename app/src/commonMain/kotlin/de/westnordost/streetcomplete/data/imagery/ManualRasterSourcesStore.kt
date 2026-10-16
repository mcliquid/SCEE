package de.westnordost.streetcomplete.data.imagery

import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.util.Mockable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/** Persists user-defined raster sources and migrates the legacy single-URL prefs. */
@Mockable
class ManualRasterSourcesStore(private val prefs: Preferences) {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ManualRasterSource.serializer())

    init {
        migrateIfNeeded()
        ensureBuiltins()
    }

    fun getAll(): List<ManualRasterSource> {
        migrateIfNeeded()
        ensureBuiltins()
        return decode()
    }

    fun get(id: String): ManualRasterSource? = getAll().find { it.id == id }

    fun put(source: ManualRasterSource) {
        migrateIfNeeded()
        ensureBuiltins()
        val list = decode().toMutableList()
        val index = list.indexOfFirst { it.id == source.id }
        if (index >= 0) list[index] = source else list.add(source)
        encode(list)
    }

    fun delete(id: String): Boolean {
        migrateIfNeeded()
        ensureBuiltins()
        val list = decode()
        val filtered = list.filterNot { it.id == id }
        if (filtered.size == list.size) return false
        encode(filtered)
        if (AerialSourceSelection.decode(prefs.getString(Prefs.AERIAL_SOURCE, AerialSourceSelection.DEFAULT_ENCODED))
            == AerialSourceSelection.Manual(id)
        ) {
            prefs.putString(Prefs.AERIAL_SOURCE, AerialSourceSelection.Esri.encode())
        }
        return true
    }

    fun createId(): String = Uuid.random().toString()

    /**
     * Insert missing built-in sources once per [BuiltinManualRasterSources.VERSION].
     * Never overwrites user-edited entries; after the version is applied, user deletes stick.
     */
    fun ensureBuiltins() {
        if (!prefs.contains(Prefs.MANUAL_RASTER_SOURCES)) return
        val applied = prefs.getInt(Prefs.MANUAL_RASTER_BUILTINS_VERSION, 0)
        if (applied >= BuiltinManualRasterSources.VERSION) return
        val list = decode().toMutableList()
        var changed = false
        for (builtin in BuiltinManualRasterSources.all) {
            if (list.none { it.id == builtin.id }) {
                list.add(builtin)
                changed = true
            }
        }
        if (changed) encode(list)
        prefs.putInt(Prefs.MANUAL_RASTER_BUILTINS_VERSION, BuiltinManualRasterSources.VERSION)
    }

    private fun migrateIfNeeded() {
        if (prefs.contains(Prefs.MANUAL_RASTER_SOURCES)) return
        val oldUrl = prefs.getStringOrNull(Prefs.RASTER_TILE_URL)
        if (oldUrl != null) {
            val migrated = ManualRasterSource(
                id = createId(),
                name = DEFAULT_MIGRATED_NAME,
                url = oldUrl,
                maxZoom = prefs.getInt(Prefs.RASTER_TILE_MAXZOOM, ApplicationConstants.RASTER_DEFAULT_MAXZOOM),
                hideLabels = prefs.getBoolean(Prefs.NO_SATELLITE_LABEL, false),
            )
            encode(listOf(migrated))
            prefs.putString(Prefs.AERIAL_SOURCE, AerialSourceSelection.Manual(migrated.id).encode())
            prefs.remove(Prefs.RASTER_TILE_URL)
            prefs.remove(Prefs.RASTER_TILE_MAXZOOM)
            prefs.remove(Prefs.NO_SATELLITE_LABEL)
        } else {
            encode(emptyList())
        }
    }

    private fun decode(): List<ManualRasterSource> =
        try {
            val raw = prefs.getString(Prefs.MANUAL_RASTER_SOURCES, "[]")
            json.decodeFromString(serializer, raw)
        } catch (_: Exception) {
            emptyList()
        }

    private fun encode(list: List<ManualRasterSource>) {
        prefs.putString(Prefs.MANUAL_RASTER_SOURCES, json.encodeToString(serializer, list))
    }

    companion object {
        const val DEFAULT_MIGRATED_NAME = "Custom"
    }
}
