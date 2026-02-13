package de.westnordost.streetcomplete.quests.guidepost_sport

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.guidepost_sport.GuidepostSport.BICYCLE
import de.westnordost.streetcomplete.quests.guidepost_sport.GuidepostSport.CLIMBING
import de.westnordost.streetcomplete.quests.guidepost_sport.GuidepostSport.HIKING
import de.westnordost.streetcomplete.quests.guidepost_sport.GuidepostSport.HORSE
import de.westnordost.streetcomplete.quests.guidepost_sport.GuidepostSport.INLINE_SKATING
import de.westnordost.streetcomplete.quests.guidepost_sport.GuidepostSport.MTB
import de.westnordost.streetcomplete.quests.guidepost_sport.GuidepostSport.NORDIC_WALKING
import de.westnordost.streetcomplete.quests.guidepost_sport.GuidepostSport.RUNNING
import de.westnordost.streetcomplete.quests.guidepost_sport.GuidepostSport.SKI
import de.westnordost.streetcomplete.quests.guidepost_sport.GuidepostSport.WINTER_HIKING

sealed interface GuidepostSportsAnswer

object IsSimpleGuidepost : GuidepostSportsAnswer

enum class GuidepostSport(val key: String) : GuidepostSportsAnswer {
    HIKING("hiking"),
    BICYCLE("bicycle"),
    MTB("mtb"),
    CLIMBING("climbing"),
    HORSE("horse"),
    NORDIC_WALKING("nordic_walking"),
    SKI("ski"),
    INLINE_SKATING("inline_skating"),
    RUNNING("running"),
    WINTER_HIKING("winter_hiking");

    companion object {
        val selectableValues = listOf(
            HIKING, BICYCLE, MTB, CLIMBING, HORSE, NORDIC_WALKING, SKI, INLINE_SKATING, RUNNING, WINTER_HIKING
        )
    }
}

val GuidepostSport.iconResId: Int get() = when (this) {
    HIKING ->          R.drawable.ic_guidepost_hiking
    BICYCLE ->         R.drawable.ic_guidepost_cycling
    MTB ->             R.drawable.ic_guidepost_mtb
    CLIMBING ->        R.drawable.ic_guidepost_climbing
    HORSE ->           R.drawable.ic_guidepost_horse_riding
    NORDIC_WALKING ->  R.drawable.ic_guidepost_nordic_walking
    SKI ->             R.drawable.ic_guidepost_ski
    INLINE_SKATING ->  R.drawable.ic_guidepost_inline_skating
    RUNNING ->         R.drawable.ic_guidepost_running
    WINTER_HIKING ->   R.drawable.ic_guidepost_snow_shoe_hiking
}

val GuidepostSport.titleResId: Int get() = when (this) {
    HIKING ->          R.string.quest_guidepost_sports_hiking
    BICYCLE ->         R.string.quest_guidepost_sports_bicycle
    MTB ->             R.string.quest_guidepost_sports_mtb
    CLIMBING ->        R.string.quest_guidepost_sports_climbing
    HORSE ->           R.string.quest_guidepost_sports_horse
    NORDIC_WALKING ->  R.string.quest_guidepost_sports_nordic_walking
    SKI ->             R.string.quest_guidepost_sports_ski
    INLINE_SKATING ->  R.string.quest_guidepost_sports_inline_skating
    RUNNING ->         R.string.quest_guidepost_sports_running
    WINTER_HIKING ->   R.string.quest_guidepost_sports_winter_hiking
}
