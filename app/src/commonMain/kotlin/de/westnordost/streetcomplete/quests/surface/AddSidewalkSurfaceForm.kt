package de.westnordost.streetcomplete.quests.surface

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.preferences.getLastPicked
import de.westnordost.streetcomplete.data.preferences.setLastPicked
import de.westnordost.streetcomplete.osm.Sides
import de.westnordost.streetcomplete.osm.any
import de.westnordost.streetcomplete.osm.sidewalk.Sidewalk
import de.westnordost.streetcomplete.osm.sidewalk.parseSidewalkSides
import de.westnordost.streetcomplete.osm.sidewalk_surface.SidewalkSurface
import de.westnordost.streetcomplete.osm.surface.Surface
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.quest.AnswerItem
import de.westnordost.streetcomplete.ui.common.quest.LocalMapRotation
import de.westnordost.streetcomplete.ui.common.quest.LocalMapTilt
import de.westnordost.streetcomplete.ui.common.quest.QuestForm
import de.westnordost.streetcomplete.ui.util.rememberSerializable
import de.westnordost.streetcomplete.util.math.getOrientationOrZero
import de.westnordost.streetcomplete.util.takeFavorites
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun AddSidewalkSurfaceForm(
    on: (QuestAction<SidewalkSurfaceAnswer>) -> Unit,
    element: Element,
    geometry: ElementGeometry,
    countryInfo: CountryInfo,
    preferences: Preferences = koinInject(),
) {
    val sidewalk = remember(element) { parseSidewalkSides(element.tags) }
    val hasSidewalkLeft = sidewalk?.left == Sidewalk.YES
    val hasSidewalkRight = sidewalk?.right == Sidewalk.YES
    val favKey = sidewalkSurfaceLastPickedKey(hasSidewalkLeft, hasSidewalkRight)
    val geometryRotation = remember(geometry) { geometry.getOrientationOrZero() }

    val lastPicked = remember(favKey) {
        loadSidewalkSurfaceLastPicked(preferences, hasSidewalkLeft, hasSidewalkRight)
    }

    var sidewalkSurfaces by rememberSerializable(element) { mutableStateOf(Sides<Surface>(null, null)) }

    QuestForm(
        on = on,
        isComplete =
            (!hasSidewalkLeft || sidewalkSurfaces.left != null) &&
            (!hasSidewalkRight || sidewalkSurfaces.right != null),
        hasChanges =
            sidewalkSurfaces.any { it != null },
        onClickOk = {
            saveSidewalkSurfaceLastPicked(preferences, sidewalkSurfaces, hasSidewalkLeft, hasSidewalkRight)
            on(Answer(SidewalkSurfaceAnswer.Surfaces(SidewalkSurface(sidewalkSurfaces))))
        },
        otherAnswers = { listOf(
            AnswerItem(stringResource(Res.string.quest_sidewalk_answer_different)) {
                on(Answer(SidewalkSurfaceAnswer.SidewalkIsDifferent))
            }
        ) },
        contentPadding = PaddingValues.Zero,
    ) {
        SidewalkSurfaceForm(
            value = sidewalkSurfaces,
            onValueChanged = { sidewalkSurfaces = it },
            geometryRotation = geometryRotation,
            mapRotation = LocalMapRotation.current,
            mapTilt = LocalMapTilt.current,
            isLeftHandTraffic = countryInfo.isLeftHandTraffic,
            countryCode = countryInfo.countryCode,
            lastPicked = lastPicked,
            hasSidewalkLeft = hasSidewalkLeft,
            hasSidewalkRight = hasSidewalkRight,
        )
    }
}

internal const val SIDEWALK_SURFACE_LAST_PICKED_KEY = "AddSidewalkSurfaceForm"

internal fun sidewalkSurfaceLastPickedKey(
    hasSidewalkLeft: Boolean,
    hasSidewalkRight: Boolean,
): String? = when {
    hasSidewalkLeft && hasSidewalkRight -> SIDEWALK_SURFACE_LAST_PICKED_KEY
    hasSidewalkLeft -> "$SIDEWALK_SURFACE_LAST_PICKED_KEY.left"
    hasSidewalkRight -> "$SIDEWALK_SURFACE_LAST_PICKED_KEY.right"
    else -> null
}

internal fun sidewalkSurfacesForLastPicked(
    sidewalkSurfaces: Sides<Surface>,
    hasSidewalkLeft: Boolean,
    hasSidewalkRight: Boolean,
): Sides<Surface> = Sides(
    left = sidewalkSurfaces.left.takeIf { hasSidewalkLeft },
    right = sidewalkSurfaces.right.takeIf { hasSidewalkRight },
)

internal fun loadSidewalkSurfaceLastPicked(
    preferences: Preferences,
    hasSidewalkLeft: Boolean,
    hasSidewalkRight: Boolean,
): List<Sides<Surface>> {
    val key = sidewalkSurfaceLastPickedKey(hasSidewalkLeft, hasSidewalkRight) ?: return emptyList()
    return preferences.getLastPicked<Sides<Surface>>(key)
        .takeFavorites(n = 5, history = 15, first = 1)
}

internal fun saveSidewalkSurfaceLastPicked(
    preferences: Preferences,
    sidewalkSurfaces: Sides<Surface>,
    hasSidewalkLeft: Boolean,
    hasSidewalkRight: Boolean,
) {
    val key = sidewalkSurfaceLastPickedKey(hasSidewalkLeft, hasSidewalkRight) ?: return
    preferences.setLastPicked(
        key,
        listOf(sidewalkSurfacesForLastPicked(sidewalkSurfaces, hasSidewalkLeft, hasSidewalkRight))
    )
}
