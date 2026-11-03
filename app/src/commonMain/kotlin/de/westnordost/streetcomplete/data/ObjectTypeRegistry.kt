package de.westnordost.streetcomplete.data

import de.westnordost.streetcomplete.data.osm.edits.EditType

/** A class where objects of a certain type are
 *  1. registered and can be recalled by class name
 *  2. or recalled by ordinal
 *  3. or iterated in the order as specified in the constructor
 */
open class ObjectTypeRegistry<T>(private val ordinalsAndEntries: List<Pair<Int, T & Any>>) : AbstractList<T>() {

    protected val byName = hashMapOf<String, T>()
    protected val byOrdinal = hashMapOf<Int, T>()
    protected val ordinalByObject = hashMapOf<T, Int>()
    protected val objects = mutableListOf<T>()

    init { reloadInit() }

    protected fun reloadInit() {
        for ((ordinal, objectType) in ordinalsAndEntries) {
            val typeName = objectType.registryTypeName().intern()
            require(!byName.containsKey(typeName)) {
                val previous = byName[typeName]
                "A object type's name must be unique! \"$typeName\" is defined twice " +
                    "(${objectType::class.qualifiedName} and ${previous!!::class.qualifiedName})!"
            }
            require(!byOrdinal.containsKey(ordinal)) {
                val otherTypeName = byOrdinal[ordinal]!!::class.simpleName!!
                "Duplicate ordinal for \"$typeName\" and \"$otherTypeName\""
            }
            byName[typeName] = objectType
            byOrdinal[ordinal] = objectType
        }
        ordinalsAndEntries.associateTo(ordinalByObject) { it.second to it.first }
        ordinalsAndEntries.mapTo(objects) { it.second }
    }

    fun getByName(typeName: String): T? = byName[typeName]

    fun getByOrdinal(ordinal: Int): T? = byOrdinal[ordinal]

    fun getOrdinalOf(type: T): Int? = ordinalByObject[type]

    override val size: Int get() = objects.size
    override fun get(index: Int): T = objects[index]

    /** Stable registry key; must match [EditType.name] for edit/quest/overlay types. */
    private fun T.registryTypeName(): String = when (val entry = this) {
        is EditType -> entry.name
        else -> (entry as Any)::class.simpleName!!
    }
}
