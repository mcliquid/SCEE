package de.westnordost.streetcomplete.quests.artwork

import de.westnordost.streetcomplete.quests.artwork.ArtworkType.ARCHITECTURE
import de.westnordost.streetcomplete.quests.artwork.ArtworkType.BUST
import de.westnordost.streetcomplete.quests.artwork.ArtworkType.FOUNTAIN
import de.westnordost.streetcomplete.quests.artwork.ArtworkType.GRAFFITI
import de.westnordost.streetcomplete.quests.artwork.ArtworkType.INSTALLATION
import de.westnordost.streetcomplete.quests.artwork.ArtworkType.LAND_ART
import de.westnordost.streetcomplete.quests.artwork.ArtworkType.MOSAIC
import de.westnordost.streetcomplete.quests.artwork.ArtworkType.MURAL
import de.westnordost.streetcomplete.quests.artwork.ArtworkType.PAINTING
import de.westnordost.streetcomplete.quests.artwork.ArtworkType.RELIEF
import de.westnordost.streetcomplete.quests.artwork.ArtworkType.SCULPTURE
import de.westnordost.streetcomplete.quests.artwork.ArtworkType.STATUE
import de.westnordost.streetcomplete.quests.artwork.ArtworkType.STONE
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.artwork_type_architecture
import de.westnordost.streetcomplete.resources.artwork_type_fountain
import de.westnordost.streetcomplete.resources.artwork_type_graffiti
import de.westnordost.streetcomplete.resources.artwork_type_installation
import de.westnordost.streetcomplete.resources.artwork_type_land_art
import de.westnordost.streetcomplete.resources.artwork_type_mosaic
import de.westnordost.streetcomplete.resources.artwork_type_mural
import de.westnordost.streetcomplete.resources.artwork_type_painting
import de.westnordost.streetcomplete.resources.artwork_type_relief
import de.westnordost.streetcomplete.resources.artwork_type_sculpture
import de.westnordost.streetcomplete.resources.artwork_type_statue
import de.westnordost.streetcomplete.resources.artwork_type_stone
import de.westnordost.streetcomplete.resources.memorial_type_bust
import de.westnordost.streetcomplete.resources.quest_artwork_architecture
import de.westnordost.streetcomplete.resources.quest_artwork_bust
import de.westnordost.streetcomplete.resources.quest_artwork_fountain
import de.westnordost.streetcomplete.resources.quest_artwork_graffiti
import de.westnordost.streetcomplete.resources.quest_artwork_installation
import de.westnordost.streetcomplete.resources.quest_artwork_land_art
import de.westnordost.streetcomplete.resources.quest_artwork_mosaic
import de.westnordost.streetcomplete.resources.quest_artwork_mural
import de.westnordost.streetcomplete.resources.quest_artwork_painting
import de.westnordost.streetcomplete.resources.quest_artwork_relief
import de.westnordost.streetcomplete.resources.quest_artwork_sculpture
import de.westnordost.streetcomplete.resources.quest_artwork_statue
import de.westnordost.streetcomplete.resources.quest_artwork_stone
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

val ArtworkType.title: StringResource get() = when (this) {
    SCULPTURE -> Res.string.quest_artwork_sculpture
    STATUE -> Res.string.quest_artwork_statue
    BUST -> Res.string.quest_artwork_bust
    ARCHITECTURE -> Res.string.quest_artwork_architecture
    RELIEF -> Res.string.quest_artwork_relief
    MURAL -> Res.string.quest_artwork_mural
    FOUNTAIN -> Res.string.quest_artwork_fountain
    INSTALLATION -> Res.string.quest_artwork_installation
    STONE -> Res.string.quest_artwork_stone
    MOSAIC -> Res.string.quest_artwork_mosaic
    GRAFFITI -> Res.string.quest_artwork_graffiti
    PAINTING -> Res.string.quest_artwork_painting
    LAND_ART -> Res.string.quest_artwork_land_art
}

val ArtworkType.icon: DrawableResource get() = when (this) {
    SCULPTURE -> Res.drawable.artwork_type_sculpture
    STATUE -> Res.drawable.artwork_type_statue
    BUST -> Res.drawable.memorial_type_bust
    ARCHITECTURE -> Res.drawable.artwork_type_architecture
    RELIEF -> Res.drawable.artwork_type_relief
    MURAL -> Res.drawable.artwork_type_mural
    FOUNTAIN -> Res.drawable.artwork_type_fountain
    INSTALLATION -> Res.drawable.artwork_type_installation
    STONE -> Res.drawable.artwork_type_stone
    MOSAIC -> Res.drawable.artwork_type_mosaic
    GRAFFITI -> Res.drawable.artwork_type_graffiti
    PAINTING -> Res.drawable.artwork_type_painting
    LAND_ART -> Res.drawable.artwork_type_land_art
}
