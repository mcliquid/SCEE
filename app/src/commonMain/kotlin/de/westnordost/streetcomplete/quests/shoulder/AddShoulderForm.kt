package de.westnordost.streetcomplete.quests.shoulder

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cheonjaeung.compose.grid.SimpleGridCells
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.osm.Sides
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.dialogs.SimpleItemSelectDialog
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import de.westnordost.streetcomplete.ui.common.quest.LocalMapRotation
import de.westnordost.streetcomplete.ui.common.quest.LocalMapTilt
import de.westnordost.streetcomplete.ui.common.quest.QuestForm
import de.westnordost.streetcomplete.ui.common.street_side_select.Side
import de.westnordost.streetcomplete.ui.common.street_side_select.StreetSideForm
import de.westnordost.streetcomplete.ui.util.rememberSerializable
import de.westnordost.streetcomplete.util.ktx.shoulderLineStyleDrawable
import de.westnordost.streetcomplete.util.math.getOrientationOrZero
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AddShoulderForm(
    on: (QuestAction<ShoulderSides>) -> Unit,
    @Suppress("UNUSED_PARAMETER") element: Element,
    geometry: ElementGeometry,
    countryInfo: CountryInfo,
) {
    var shoulders by rememberSerializable { mutableStateOf(Sides<Boolean?>(null, null)) }
    var pickerSide by remember { mutableStateOf<Side?>(null) }

    val geometryRotation = remember(geometry) { geometry.getOrientationOrZero() }
    val shoulderLinePainter = painterResource(countryInfo.shoulderLineStyleDrawable)
    val noShoulderPainter = painterResource(Res.drawable.shoulder_no)
    val bareRoadPainter = painterResource(Res.drawable.street_broad)

    val hintText = stringResource(Res.string.quest_shoulder_explanation2) + "\n" +
        stringResource(Res.string.quest_street_side_puzzle_tutorial)

    QuestForm(
        on = on,
        isComplete = shoulders.left != null && shoulders.right != null,
        hasChanges = shoulders.left != null || shoulders.right != null,
        hintText = hintText,
        onClickOk = {
            on(Answer(ShoulderSides(shoulders.left!!, shoulders.right!!)))
        },
        contentPadding = PaddingValues.Zero,
    ) {
        StreetSideForm(
            value = shoulders,
            onValueChanged = { shoulders = it },
            getIllustrationPainter = { hasShoulder, _ ->
                when (hasShoulder) {
                    true -> shoulderLinePainter
                    false -> noShoulderPainter
                    null -> null
                }
            },
            getFloatingPainter = { hasShoulder, _ ->
                if (hasShoulder == false) bareRoadPainter else null
            },
            onClickSide = { pickerSide = it },
            geometryRotation = geometryRotation,
            mapRotation = LocalMapRotation.current,
            mapTilt = LocalMapTilt.current,
            isLeftHandTraffic = countryInfo.isLeftHandTraffic,
        )
    }

    pickerSide?.let { side ->
        SimpleItemSelectDialog(
            onDismissRequest = { pickerSide = null },
            columns = SimpleGridCells.Fixed(2),
            items = listOf(true, false),
            onSelected = { hasShoulder ->
                shoulders = when (side) {
                    Side.LEFT -> shoulders.copy(left = hasShoulder)
                    Side.RIGHT -> shoulders.copy(right = hasShoulder)
                }
            },
            itemContent = { hasShoulder ->
                ImageWithLabel(
                    painter = if (hasShoulder) shoulderLinePainter else bareRoadPainter,
                    label = stringResource(
                        if (hasShoulder) Res.string.quest_shoulder_value_yes
                        else Res.string.quest_shoulder_value_no
                    ),
                )
            },
        )
    }
}
