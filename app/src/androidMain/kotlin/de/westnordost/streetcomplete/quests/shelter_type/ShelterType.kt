package de.westnordost.streetcomplete.quests.shelter_type

import de.westnordost.streetcomplete.R

enum class ShelterType(val osmValue: String) {
    PUBLIC_TRANSPORT("public_transport"),
    PICNIC_SHELTER("picnic_shelter"),
    GAZEBO("gazebo"),
    LEAN_TO("lean_to"),
    BASIC_HUT("basic_hut"),
    SUN_SHELTER("sun_shelter"),
    FIELD_SHELTER("field_shelter"),
    ROCK_SHELTER("rock_shelter"),
    WEATHER_SHELTER("weather_shelter")
}

val ShelterType.icon get() = when (this) {
    ShelterType.PUBLIC_TRANSPORT -> R.drawable.shelter_type_public_transport
    ShelterType.PICNIC_SHELTER -> R.drawable.shelter_type_picnic_shelter
    ShelterType.GAZEBO -> R.drawable.shelter_type_gazebo
    ShelterType.LEAN_TO -> R.drawable.shelter_type_lean_to
    ShelterType.BASIC_HUT -> R.drawable.shelter_type_basic_hut
    ShelterType.SUN_SHELTER -> R.drawable.shelter_type_sun_shelter
    ShelterType.FIELD_SHELTER -> R.drawable.shelter_type_field_shelter
    ShelterType.ROCK_SHELTER -> R.drawable.shelter_type_rock_shelter
    ShelterType.WEATHER_SHELTER -> 0
}

val ShelterType.title get() = when (this) {
    ShelterType.PUBLIC_TRANSPORT -> R.string.quest_shelter_type_public_transport
    ShelterType.PICNIC_SHELTER -> R.string.quest_shelter_type_picnic_shelter
    ShelterType.GAZEBO -> R.string.quest_shelter_type_gazebo
    ShelterType.LEAN_TO -> R.string.quest_shelter_type_lean_to
    ShelterType.BASIC_HUT -> R.string.quest_shelter_type_basic_hut
    ShelterType.SUN_SHELTER -> R.string.quest_shelter_type_sun_shelter
    ShelterType.FIELD_SHELTER -> R.string.quest_shelter_type_field_shelter
    ShelterType.ROCK_SHELTER -> R.string.quest_shelter_type_rock_shelter
    ShelterType.WEATHER_SHELTER -> 0
}
