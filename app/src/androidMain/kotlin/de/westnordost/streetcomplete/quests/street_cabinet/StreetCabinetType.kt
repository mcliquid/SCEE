package de.westnordost.streetcomplete.quests.street_cabinet

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.street_cabinet.StreetCabinetType.GAS
import de.westnordost.streetcomplete.quests.street_cabinet.StreetCabinetType.POSTAL_SERVICE
import de.westnordost.streetcomplete.quests.street_cabinet.StreetCabinetType.POWER
import de.westnordost.streetcomplete.quests.street_cabinet.StreetCabinetType.SEWERAGE
import de.westnordost.streetcomplete.quests.street_cabinet.StreetCabinetType.STREET_LIGHTING
import de.westnordost.streetcomplete.quests.street_cabinet.StreetCabinetType.TELECOM
import de.westnordost.streetcomplete.quests.street_cabinet.StreetCabinetType.TELEVISION
import de.westnordost.streetcomplete.quests.street_cabinet.StreetCabinetType.TRAFFIC_CONTROL
import de.westnordost.streetcomplete.quests.street_cabinet.StreetCabinetType.TRAFFIC_MONITORING
import de.westnordost.streetcomplete.quests.street_cabinet.StreetCabinetType.TRANSPORT_MANAGEMENT
import de.westnordost.streetcomplete.quests.street_cabinet.StreetCabinetType.WASTE
import de.westnordost.streetcomplete.quests.street_cabinet.StreetCabinetType.WATER

enum class StreetCabinetType(val osmKey: String, val osmValue: String) {
    POWER("utility", "power"),
    TELECOM("utility", "telecom"),
    TRAFFIC_CONTROL("street_cabinet", "traffic_control"),
    POSTAL_SERVICE("street_cabinet", "postal_service"),
    GAS("utility", "gas"),
    STREET_LIGHTING("utility", "street_lighting"),
    TRANSPORT_MANAGEMENT("street_cabinet", "transport_management"),
    TRAFFIC_MONITORING("street_cabinet", "traffic_monitoring"),
    WASTE("street_cabinet", "waste"),
    TELEVISION("utility", "television"),
    WATER("utility", "water"),
    SEWERAGE("utility", "sewerage");
}

val StreetCabinetType.titleResId: Int get() = when (this) {
    POWER ->                R.string.quest_utility_power
    TELECOM ->              R.string.quest_utility_telecom
    POSTAL_SERVICE ->       R.string.quest_street_cabinet_postal_service
    TRAFFIC_CONTROL ->      R.string.quest_street_cabinet_traffic_control
    TRAFFIC_MONITORING ->   R.string.quest_street_cabinet_traffic_monitoring
    TRANSPORT_MANAGEMENT -> R.string.quest_street_cabinet_transport_management
    WASTE ->                R.string.quest_street_cabinet_waste
    TELEVISION ->           R.string.quest_street_cabinet_television
    GAS ->                  R.string.quest_utility_gas
    STREET_LIGHTING ->      R.string.quest_street_cabinet_street_lighting
    WATER ->                R.string.quest_utility_water
    SEWERAGE ->             R.string.quest_utility_sewerage
}

val StreetCabinetType.iconResId: Int get() = when (this) {
    POWER ->                R.drawable.quest_street_cabinet_power
    TELECOM ->              R.drawable.quest_street_cabinet_telecom
    POSTAL_SERVICE ->       R.drawable.quest_street_cabinet_postal_service
    TRAFFIC_CONTROL ->      R.drawable.quest_street_cabinet_traffic_control
    TRAFFIC_MONITORING ->   R.drawable.quest_street_cabinet_traffic_monitoring
    TRANSPORT_MANAGEMENT -> R.drawable.quest_street_cabinet_transport_management
    WASTE ->                R.drawable.quest_street_cabinet_waste
    TELEVISION ->           R.drawable.quest_street_cabinet_television
    GAS ->                  R.drawable.quest_street_cabinet_gas
    STREET_LIGHTING ->      R.drawable.quest_street_cabinet_street_lighting
    WATER ->                R.drawable.quest_street_cabinet_water
    SEWERAGE ->             R.drawable.quest_street_cabinet_sewerage
}
