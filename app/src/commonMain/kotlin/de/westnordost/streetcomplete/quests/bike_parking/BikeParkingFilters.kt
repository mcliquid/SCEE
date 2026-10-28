package de.westnordost.streetcomplete.quests.bike_parking

/** Surveyable bicycle parking: private stays excluded; access=no allowed with explicit cargo permission. */
internal const val SURVEYABLE_BICYCLE_PARKING_ACCESS =
    "access !~ private and (access !~ no or cargo_bike ~ yes|designated)"
