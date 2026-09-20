package de.westnordost.streetcomplete.quests.smoothness

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.osmquests.Action
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.osm.surface.Surface
import de.westnordost.streetcomplete.osm.surface.parseSurface
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.dialogs.InfoDialog
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithDescription
import de.westnordost.streetcomplete.ui.common.quest.AnswerItem
import de.westnordost.streetcomplete.ui.common.quest.ItemSelectQuestForm
import de.westnordost.streetcomplete.ui.common.quest.LocalQuestType
import de.westnordost.streetcomplete.util.ktx.couldBeSteps
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun AddSmoothnessForm(
    on: (QuestAction<SmoothnessAnswer>) -> Unit,
    element: Element,
    surfaceKey: String = "surface",
    allowIsActuallySteps: Boolean = true,
    allowWrongSurface: Boolean = true,
) {
    val surfaceTag = element.tags[surfaceKey]
    val items = remember(surfaceKey, surfaceTag) { smoothnessAnswersForSurfaceKey(element.tags, surfaceKey) }
    val prefs: Preferences = koinInject()

    var showObstacleHint by remember { mutableStateOf(false) }
    var confirmSurface by remember { mutableStateOf<Surface?>(null) }
    val titleExtra = if (prefs.expertMode && surfaceTag != null) " ($surfaceTag)" else ""
    val knownSurface = parseSurface(surfaceTag)?.takeIf { it != Surface.UNSUPPORTED }
    val titleRes = LocalQuestType.current?.title ?: Res.string.quest_smoothness_title

    ItemSelectQuestForm(
        on = {
            on(when (it) {
                is Answer<Smoothness> -> Answer(SmoothnessValueAnswer(it.value))
                is Action -> it
            })
        },
        items = items,
        itemContent = { item ->
            val illustrationSurface = item.getIllustrationSurface(surfaceTag)
            Box {
                ImageWithDescription(
                    painter = item.getImage(illustrationSurface)?.let { painterResource(it) },
                    title = stringResource(item.title),
                    description = item.getDescription(illustrationSurface)?.let { stringResource(it) }
                )
                Image(
                    painter = painterResource(item.icon),
                    contentDescription = item.emoji,
                    modifier = Modifier.padding(8.dp)
                )
            }
        },
        itemsPerRow = 1,
        // SC #7091: use quest title; keep SCEE expert-mode surface suffix
        title = stringResource(titleRes) + titleExtra,
        otherAnswers = { listOfNotNull(
            if (allowWrongSurface && knownSurface != null) {
                AnswerItem(stringResource(Res.string.quest_smoothness_wrong_surface)) {
                    confirmSurface = knownSurface
                }
            } else {
                null
            },
            if (allowIsActuallySteps && element.couldBeSteps()) {
                AnswerItem(stringResource(Res.string.quest_generic_answer_is_actually_steps)) {
                    on(Answer(IsActuallyStepsAnswer))
                }
            } else {
                null
            },
            AnswerItem(stringResource(Res.string.quest_smoothness_obstacle)) {
                showObstacleHint = true
            }
        ) }
    )

    if (showObstacleHint) {
        InfoDialog(
            onDismissRequest = { showObstacleHint = false },
            text = { Text(stringResource(Res.string.quest_smoothness_obstacle_hint)) }
        )
    }

    confirmSurface?.let { surface ->
        ConfirmSurfaceDialog(
            onDismissRequest = { confirmSurface = null },
            surface = surface,
            onConfirmSurface = { on(Action.LeaveNote) },
            onWrongSurface = { on(Answer(WrongSurfaceAnswer)) }
        )
    }
}
