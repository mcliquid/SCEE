package de.westnordost.streetcomplete.data.preferences

import com.russhwolf.settings.SettingsListener
import de.westnordost.streetcomplete.util.logs.Log
import java.security.MessageDigest
import java.util.UUID
import java.util.prefs.PreferenceChangeListener
import java.util.prefs.Preferences

/** Immutable generations with one publication reference; reads native and M6 legacy data.
 * Read-back detects observable dropped writes, not power-loss safety; flush semantics depend on the JDK.
 * Throwing backend failures are propagated. Not a multi-key or multi-process transaction.
 */
class DesktopPreferenceStore(private val store: Preferences) : SettingsWithSnapshot {
    override fun putString(key: String, value: String) = write(key, value)
    override fun getStringOrNull(key: String): String? = synchronized(lock) { read(key)?.value }
    override fun getString(key: String, defaultValue: String) = getStringOrNull(key) ?: defaultValue
    override fun remove(key: String) = write(key, null)
    override fun hasKey(key: String) = getStringOrNull(key) != null
    override val keys: Set<String> get() = synchronized(lock) {
        val result = legacyKeys().toMutableSet()
        if (store.nodeExists(VALUES)) {
            for (digest in store.node(VALUES).childrenNames()) {
                val record = recoverCorruption { committed(store.node(VALUES).node(digest)) } ?: continue
                if (hash(record.key) != digest) {
                    Log.w("DesktopPreferences", "Invalid preference key digest; entry omitted, original data retained")
                    continue
                }
                result.add(record.key)
            }
        }
        result.filterTo(linkedSetOf()) { key -> recoverCorruption { read(key) }?.value != null }
    }
    override val size: Int get() = keys.size
    override val all: Map<String, *> get() = synchronized(lock) { keys.associateWith { getStringOrNull(it) } }
    override fun clear() { synchronized(lock) { keys.forEach(::remove) } }

    private data class Record(val key: String, val value: String?, val id: String)
    private class CorruptPreference(message: String) : IllegalStateException(message)

    private fun <T> recoverCorruption(read: () -> T): T? = try {
        read()
    } catch (failure: CorruptPreference) {
        Log.w("DesktopPreferences", "${failure.message}; original data retained", failure)
        null
    }

    private fun read(key: String): Record? {
        val path = "$VALUES/${hash(key)}"
        if (store.nodeExists(path)) {
            committed(store.node(path))?.let {
                check(it.key == key) { "Preference key collision" }
                return it
            }
        }
        return legacyRead(key)?.let { Record(key, it, "") }
    }

    private fun write(key: String, value: String?) = synchronized(lock) {
        require('\u0000' !in key) { "Preference keys must not contain NUL" }
        val old = recoverCorruption { read(key) }
        // A null record needs a tombstone too: it may mask unreadable legacy data.
        if (old != null && old.value == value) return@synchronized
        val node = store.node(VALUES).node(hash(key))
        val oldReference = node.get(CURRENT, null)
        val id = "g-${UUID.randomUUID()}"
        val generation = node.node(id)
        putParts(generation, "key", key)
        if (value != null) putParts(generation, "value", value)
        generation.putBoolean("deleted", value == null)
        generation.put("complete", id)
        generation.flush()
        check(generationRecord(node, id) == Record(key, value, id)) { "Preference generation read-back failed" }
        try {
            // Windows putSpi can swallow Registry errors; verify visibility, not power-loss safety.
            val reference = id + (old?.id?.takeIf { it.isNotEmpty() }?.let { ":$it" } ?: "")
            node.put(CURRENT, reference)
            node.flush()
            check(node.get(CURRENT, null) == reference && generationRecord(node, id) == Record(key, value, id)) {
                "Preference publication read-back failed"
            }
        } catch (failure: Exception) {
            try {
                if (oldReference == null) node.remove(CURRENT) else node.put(CURRENT, oldReference)
                node.flush()
            } catch (restoreFailure: Exception) {
                failure.addSuppressed(restoreFailure)
            }
            throw failure
        }
        // Reclaim only our obsolete predecessor, never another process's staged generation.
        try {
            val obsolete = oldReference?.substringAfter(':', "")
            if (!obsolete.isNullOrEmpty() && obsolete.startsWith("g-") && '/' !in obsolete && ':' !in obsolete
                && obsolete != old?.id && obsolete !in node.get(CURRENT, "").split(':')
                && generationRecord(node, obsolete)?.key == key) {
                node.node(obsolete).removeNode()
            }
            node.flush()
        } catch (failure: Exception) {
            Log.w("DesktopPreferences", "Could not reclaim obsolete preference generations", failure)
        }
    }

    private fun committed(node: Preferences): Record? {
        val reference = node.get(CURRENT, null) ?: return null
        val problems = mutableListOf<String>()
        for ((index, id) in reference.split(':').withIndex()) {
            if (!id.startsWith("g-") || id.length > Preferences.MAX_NAME_LENGTH || '/' in id) {
                problems += "invalid reference"
                continue
            }
            if (!node.nodeExists(id)) { problems += "missing generation"; continue }
            val record = generationRecord(node, id)
            if (record == null) { problems += "incomplete or corrupt generation"; continue }
            if (index != 0) Log.w("DesktopPreferences", "Recovered previous complete preference generation")
            return record
        }
        throw CorruptPreference("No complete preference generation (${problems.joinToString()})")
    }

    private fun generationRecord(node: Preferences, id: String): Record? {
        if (!node.nodeExists(id)) return null
        val generation = node.node(id)
        if (generation.get("complete", null) != id) return null
        val key = readParts(generation, "key") ?: return null
        val value = when (generation.get("deleted", null)) {
            "true" -> null
            "false" -> readParts(generation, "value") ?: return null
            else -> return null
        }
        return Record(key, value, id)
    }

    private fun putParts(node: Preferences, prefix: String, text: String) {
        val parts = text.chunked(PART_SIZE)
        parts.forEachIndexed { i, part -> node.put("$prefix.$i", part) }
        node.putInt("$prefix.count", parts.size)
        node.put("$prefix.sha256", hash(text))
    }

    private fun readParts(node: Preferences, prefix: String): String? {
        val count = node.getInt("$prefix.count", -1)
        if (count < 0) return null
        val text = buildString {
            for (i in 0 until count) append(node.get("$prefix.$i", null) ?: return null)
        }
        return text.takeIf { hash(it) == node.get("$prefix.sha256", null) }
    }

    private fun legacyRead(key: String): String? {
        val physical = legacyPhysical(key)
        if (physical != key && store.get(physical, null) != null) {
            val name = store.get("$physical.__name", null)
                ?: throw CorruptPreference("Missing legacy preference key name")
            if (legacyPhysical(name) != physical) throw CorruptPreference("Corrupt legacy preference key mapping")
            check(name == key) { "Legacy preference key collision" }
        }
        val raw = store.get(physical, null) ?: if (key.length <= Preferences.MAX_KEY_LENGTH) store.get(key, null) else null
        if (raw == null) return null
        val count = partCount(raw)
        if (count == 0) return raw
        return buildString {
            for (i in 0 until count) append(store.get("$physical.__part.$i", null) ?: run {
                Log.w("DesktopPreferences", "Missing legacy preference value part; original data retained")
                return null
            })
        }
    }

    private fun legacyKeys(): Set<String> {
        val physicalKeys = store.keys().toSet()
        val aliases = physicalKeys.filter { it.matches(Regex("~[0-9a-f]{40}")) }.mapNotNull { physical ->
            store.get("$physical.__name", null)?.takeIf { legacyPhysical(it) == physical }?.let { physical to it }
        }.toMap()
        val damagedAliases = physicalKeys.filter { it.matches(Regex("~[0-9a-f]{40}")) && it !in aliases }.toSet()
        if (damagedAliases.isNotEmpty()) Log.w("DesktopPreferences", "Corrupt legacy preference key mappings omitted; original data retained")
        return physicalKeys.mapNotNull { physical ->
            when {
                physical in damagedAliases || physical.removeSuffix(".__name") in damagedAliases -> null
                physical.substringBeforeLast(".__part.", "") in damagedAliases -> null
                physical.removeSuffix(".__name") in aliases && physical.endsWith(".__name") -> null
                physical in aliases -> aliases[physical]
                physical.substringBeforeLast(".__part.", "").let { parent ->
                    parent.isNotEmpty() && partCount(store.get(parent, null)) > 0
                } -> null
                else -> physical
            }
        }.toSet()
    }

    private fun legacyPhysical(key: String) = if (key.length <= 68) key else "~" + hash(key).take(40)
    private fun partCount(raw: String?) = raw?.takeIf { it.startsWith("@@scee-parts:") && it.endsWith("@@") }
        ?.removePrefix("@@scee-parts:")?.removeSuffix("@@")?.toIntOrNull()?.takeIf { it > 0 } ?: 0

    private fun <T> listen(key: String, getter: () -> T, callback: (T) -> Unit): SettingsListener = synchronized(lock) {
        var previous = getter()
        var active = true
        val node = store.node(VALUES).node(hash(key))
        val changed = {
            val notification = synchronized(lock) {
                if (!active) null else {
                    val value = getter()
                    if (value == previous) null else {
                        previous = value
                        { callback(value) }
                    }
                }
            }
            notification?.invoke()
        }
        val currentListener = PreferenceChangeListener { if (it.key == CURRENT) changed() }
        val legacyListener = PreferenceChangeListener { if (it.key == key || it.key == legacyPhysical(key)) changed() }
        node.addPreferenceChangeListener(currentListener)
        store.addPreferenceChangeListener(legacyListener)
        object : SettingsListener {
            override fun deactivate() = synchronized(lock) {
                if (active) {
                    active = false
                    node.removePreferenceChangeListener(currentListener)
                    store.removePreferenceChangeListener(legacyListener)
                }
            }
        }
    }

    override fun putInt(key: String, value: Int) = putString(key, value.toString())
    override fun getIntOrNull(key: String) = getStringOrNull(key)?.let { it.toIntOrNull() ?: 0 }
    override fun getInt(key: String, defaultValue: Int) = getStringOrNull(key)?.toIntOrNull() ?: defaultValue
    override fun putLong(key: String, value: Long) = putString(key, value.toString())
    override fun getLongOrNull(key: String) = getStringOrNull(key)?.let { it.toLongOrNull() ?: 0L }
    override fun getLong(key: String, defaultValue: Long) = getStringOrNull(key)?.toLongOrNull() ?: defaultValue
    override fun putFloat(key: String, value: Float) = putString(key, value.toString())
    override fun getFloatOrNull(key: String) = getStringOrNull(key)?.let { it.toFloatOrNull() ?: 0f }
    override fun getFloat(key: String, defaultValue: Float) = getStringOrNull(key)?.toFloatOrNull() ?: defaultValue
    override fun putDouble(key: String, value: Double) = putString(key, value.toString())
    override fun getDoubleOrNull(key: String) = getStringOrNull(key)?.let { it.toDoubleOrNull() ?: 0.0 }
    override fun getDouble(key: String, defaultValue: Double) = getStringOrNull(key)?.toDoubleOrNull() ?: defaultValue
    override fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())
    override fun getBooleanOrNull(key: String) = getStringOrNull(key)?.equals("true", true)
    override fun getBoolean(key: String, defaultValue: Boolean) = getStringOrNull(key)?.let {
        when { it.equals("true", true) -> true; it.equals("false", true) -> false; else -> defaultValue }
    } ?: defaultValue

    override fun addStringListener(key: String, defaultValue: String, callback: (String) -> Unit) = listen(key, { getString(key, defaultValue) }, callback)
    override fun addStringOrNullListener(key: String, callback: (String?) -> Unit) = listen(key, { getStringOrNull(key) }, callback)
    override fun addIntListener(key: String, defaultValue: Int, callback: (Int) -> Unit) = listen(key, { getInt(key, defaultValue) }, callback)
    override fun addIntOrNullListener(key: String, callback: (Int?) -> Unit) = listen(key, { getIntOrNull(key) }, callback)
    override fun addLongListener(key: String, defaultValue: Long, callback: (Long) -> Unit) = listen(key, { getLong(key, defaultValue) }, callback)
    override fun addLongOrNullListener(key: String, callback: (Long?) -> Unit) = listen(key, { getLongOrNull(key) }, callback)
    override fun addFloatListener(key: String, defaultValue: Float, callback: (Float) -> Unit) = listen(key, { getFloat(key, defaultValue) }, callback)
    override fun addFloatOrNullListener(key: String, callback: (Float?) -> Unit) = listen(key, { getFloatOrNull(key) }, callback)
    override fun addDoubleListener(key: String, defaultValue: Double, callback: (Double) -> Unit) = listen(key, { getDouble(key, defaultValue) }, callback)
    override fun addDoubleOrNullListener(key: String, callback: (Double?) -> Unit) = listen(key, { getDoubleOrNull(key) }, callback)
    override fun addBooleanListener(key: String, defaultValue: Boolean, callback: (Boolean) -> Unit) = listen(key, { getBoolean(key, defaultValue) }, callback)
    override fun addBooleanOrNullListener(key: String, callback: (Boolean?) -> Unit) = listen(key, { getBooleanOrNull(key) }, callback)

    private companion object {
        const val VALUES = "scee-values-v2"
        const val CURRENT = "current"
        const val PART_SIZE = 8000
        // Serializes publication and snapshots across all adapter instances in this JVM.
        val lock = Any()
        fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
