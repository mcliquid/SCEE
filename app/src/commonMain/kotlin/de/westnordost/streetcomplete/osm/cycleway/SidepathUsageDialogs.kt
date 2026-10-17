package de.westnordost.streetcomplete.osm.cycleway

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.cheonjaeung.compose.grid.SimpleGridCells
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.quest.OsmQuestKey
import de.westnordost.streetcomplete.data.visiblequests.QuestsHiddenController
import de.westnordost.streetcomplete.osm.Sides
import de.westnordost.streetcomplete.osm.oneway.isOneway
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.dialogs.AreYouSureDialog
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import de.westnordost.streetcomplete.ui.common.item_select.ItemSelectGrid
import de.westnordost.streetcomplete.ui.common.quest.LocalMapRotation
import de.westnordost.streetcomplete.ui.util.ClipCirclePainter
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** What the user can choose in the question about the usage of the separate cycleways. The
 *  arrows point into the direction of the OSM way as shown on the map. */
@Serializable
enum class SidepathChoice {
    FORWARD, BACKWARD, BOTH,
    /** "No / can't tell": nothing is tagged, the quest is hidden */
    NO_OR_UNKNOWN;

    val roadSidepath: RoadSidepath? get() = when (this) {
        FORWARD -> RoadSidepath.FORWARD
        BACKWARD -> RoadSidepath.BACKWARD
        BOTH -> RoadSidepath.BOTH
        NO_OR_UNKNOWN -> null
    }
}

fun RoadSidepath.toChoice(): SidepathChoice? = when (this) {
    RoadSidepath.FORWARD -> SidepathChoice.FORWARD
    RoadSidepath.BACKWARD -> SidepathChoice.BACKWARD
    RoadSidepath.BOTH -> SidepathChoice.BOTH
    RoadSidepath.NONE -> null
}

/** The choices to offer for the given answers that may be written. "No / can't tell" is last. */
fun getSidepathChoices(answers: List<RoadSidepath>): List<SidepathChoice> =
    answers.mapNotNull { it.toChoice() } + SidepathChoice.NO_OR_UNKNOWN

/** Marks the quest about the usage of the separate cycleways of this element as hidden by the
 *  user, using the standard (persistent) hiding of quests */
fun QuestsHiddenController.hideSidepathUsageQuest(element: Element) {
    hide(OsmQuestKey(element.type, element.id, SIDEPATH_USAGE_QUEST_NAME))
}

/** Grid of arrows (and the "no / can't tell" tile) to choose from. [geometryRotation] is the
 *  orientation of the road, so the arrows point into the direction of the way on the map. */
@Composable
fun SidepathChoiceGrid(
    choices: List<SidepathChoice>,
    selected: SidepathChoice?,
    onSelect: (SidepathChoice?) -> Unit,
    geometryRotation: Float,
    isOneway: Boolean,
) {
    val rotation = geometryRotation - LocalMapRotation.current
    ItemSelectGrid(
        columns = SimpleGridCells.Fixed(minOf(choices.size, 3)),
        items = choices,
        selectedItem = selected,
        onSelect = onSelect,
    ) { choice ->
        val drawable = when (choice) {
            SidepathChoice.FORWARD -> Res.drawable.oneway_yes
            SidepathChoice.BACKWARD -> Res.drawable.oneway_yes_reverse
            SidepathChoice.BOTH -> Res.drawable.oneway_no
            SidepathChoice.NO_OR_UNKNOWN -> null
        }
        val painter = drawable?.let { painterResource(it) }
        ImageWithLabel(
            painter = painter?.let { remember(it) { ClipCirclePainter(it) } },
            label = stringResource(
                when (choice) {
                    SidepathChoice.FORWARD, SidepathChoice.BACKWARD ->
                        if (isOneway) Res.string.cycleway_sidepath_answer_oneway
                        else Res.string.cycleway_sidepath_answer_direction
                    SidepathChoice.BOTH -> Res.string.cycleway_sidepath_answer_both
                    SidepathChoice.NO_OR_UNKNOWN -> Res.string.cycleway_sidepath_answer_none
                }
            ),
            imageRotation = if (painter != null) rotation else null,
        )
    }
}

/** Result of the follow-up: the sides including the answer and whether the user answered
 *  "No / can't tell", so the quest about it should be hidden */
data class SidepathFollowUpResult(val sides: Sides<CyclewayAndDirection>, val hideQuest: Boolean)

/** Whether answering with [sides] needs the follow-up about the compulsory use of a separate
 *  cycleway (a question and/or a confirmation to remove a contradicting tag) */
fun needsSidepathFollowUp(
    tags: Map<String, String>,
    original: Sides<CyclewayAndDirection>,
    sides: Sides<CyclewayAndDirection>,
    isLeftHandTraffic: Boolean,
    countryCode: String?,
): Boolean =
    needsSidepathQuestion(tags, original, sides, isLeftHandTraffic, countryCode) ||
    needsStaleSidepathRemoval(tags, original, sides)

/** Follow-up after a separate cycleway has been selected: asks whether it is compulsory to use
 *  the cycleways here and, if that would change or contradict existing tags, whether those should
 *  be changed. [onResult] is called with the sides including the answer. [onDismiss] if the user
 *  aborted. Existing tags are never changed without confirmation. */
@Composable
fun SidepathUsageDialogs(
    tags: Map<String, String>,
    original: Sides<CyclewayAndDirection>,
    sides: Sides<CyclewayAndDirection>,
    geometryRotation: Float,
    isLeftHandTraffic: Boolean,
    countryCode: String?,
    onResult: (SidepathFollowUpResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val answers = remember(tags, sides, countryCode) {
        if (needsSidepathQuestion(tags, original, sides, isLeftHandTraffic, countryCode)) {
            getSidepathAnswers(tags, isLeftHandTraffic)
        } else {
            emptyList()
        }
    }
    val choices = remember(answers) { getSidepathChoices(answers) }
    val isOneway = remember(tags) { isOneway(tags) }
    var selected by remember(choices) {
        mutableStateOf(getExistingRoadSidepath(tags)?.toChoice()?.takeIf { it in choices })
    }
    var current by remember(answers) { mutableStateOf(if (answers.isEmpty()) sides else null) }
    var hideQuest by remember { mutableStateOf(false) }

    val answered = current
    if (answered == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.cycleway_sidepath_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(Res.string.cycleway_sidepath_hint),
                        style = MaterialTheme.typography.body2
                    )
                    SidepathChoiceGrid(choices, selected, { selected = it }, geometryRotation, isOneway)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selected != null,
                    onClick = {
                        val choice = selected ?: return@TextButton
                        hideQuest = choice == SidepathChoice.NO_OR_UNKNOWN
                        current = sides.withRoadSidepath(choice.roadSidepath)
                    }
                ) { Text(stringResource(Res.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            },
        )
    } else {
        val answer = answered.roadSidepath
        val isStale = answer == null && needsStaleSidepathRemoval(tags, original, answered)
        val isChange = answer != null &&
            needsSidepathChangeConfirmation(tags, answer, isLeftHandTraffic)
        if (!isStale && !isChange) {
            LaunchedEffect(answered) { onResult(SidepathFollowUpResult(answered, hideQuest)) }
        } else {
            // dismissing without confirming keeps the existing tag
            val decided = remember { booleanArrayOf(false) }
            AreYouSureDialog(
                onDismissRequest = {
                    if (!decided[0]) {
                        decided[0] = true
                        onResult(SidepathFollowUpResult(answered.withRoadSidepath(null), hideQuest))
                    }
                },
                onConfirmed = {
                    decided[0] = true
                    val confirmed = if (isStale) answered.withRoadSidepath(RoadSidepath.NONE) else answered
                    onResult(SidepathFollowUpResult(confirmed, hideQuest))
                },
                text = { Text(stringResource(Res.string.cycleway_sidepath_remove_confirmation)) }
            )
        }
    }
}
