package de.westnordost.streetcomplete.osm

import de.westnordost.streetcomplete.data.elementfilter.toElementFilterExpression
import de.westnordost.streetcomplete.data.osm.mapdata.Element

/** Filter fragment for ways not explicitly or inherited private/no for bicycles. */
val FILTER_BICYCLE_ACCESSIBLE =
    "bicycle !~ private|no|dismount and (bicycle or (vehicle !~ private|no and (vehicle or access !~ private|no)))"

/** Filter fragment for ways not explicitly or inherited private/no for pedestrians. */
val FILTER_FOOT_ACCESSIBLE =
    "foot !~ private|no and (foot or (vehicle !~ private|no and (vehicle or access !~ private|no)))"

/** Filter for ways where either walking or cycling is permitted despite general access restrictions. */
val FILTER_FOOT_OR_BICYCLE_ACCESSIBLE =
    "($FILTER_FOOT_ACCESSIBLE) or ($FILTER_BICYCLE_ACCESSIBLE)"

private val isPrivateOnFootFilter by lazy { """
    nodes, ways, relations with
      access ~ private|no
      and (!foot or foot ~ private|no)
""".toElementFilterExpression() }

fun isPrivateOnFoot(element: Element): Boolean = isPrivateOnFootFilter.matches(element)
