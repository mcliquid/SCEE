package de.westnordost.streetcomplete.quests.barrier_locked

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import de.westnordost.osm_opening_hours.model.Month
import de.westnordost.osm_opening_hours.model.MonthRange
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.ComposeViewBinding
import de.westnordost.streetcomplete.osm.opening_hours.HierarchicOpeningHours
import de.westnordost.streetcomplete.osm.time_restriction.TimeRestriction
import de.westnordost.streetcomplete.osm.time_restriction.TimeRestrictionInput
import de.westnordost.streetcomplete.quests.AbstractOsmQuestForm
import de.westnordost.streetcomplete.quests.AnswerItem
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.quest_fee_answer_yes_but
import de.westnordost.streetcomplete.ui.util.content
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jetbrains.compose.resources.stringResource

class AddBarrierLockedForm : AbstractOsmQuestForm<BarrierLockedAnswer>() {

    override val contentLayoutResId = R.layout.compose_view
    private val binding by contentViewBinding(ComposeViewBinding::bind)

    override val buttonPanelAnswers get() =
        if (answer.value == null) {
            listOf(
                AnswerItem(R.string.quest_generic_hasFeature_no) { applyAnswer(NotLocked) },
                AnswerItem(R.string.quest_generic_hasFeature_yes) { applyAnswer(Locked) }
            )
        } else {
            emptyList()
        }

    override val otherAnswers get() = listOfNotNull(
        AnswerItem(R.string.quest_fee_answer_hours) {
            answer.value = LockedAtHours(TimeRestriction(
                HierarchicOpeningHours(),TimeRestriction.Mode.ONLY_AT_HOURS
            ))
        },
        if (answer.value == null) null
        else AnswerItem(R.string.quest_openingHours_answer_seasonal_opening_hours) {
            val old = (answer.value as? LockedAtHours)?.timeRestriction
                ?: TimeRestriction(HierarchicOpeningHours(),TimeRestriction.Mode.ONLY_AT_HOURS)
            val allMonths = listOf(MonthRange(Month.January, Month.December))
            val hours = HierarchicOpeningHours(
                old.hours.monthsList.map { months ->
                    if (months.selectors.isEmpty()) months.copy(selectors = allMonths) else months
                }
            )
            answer.value = LockedAtHours(TimeRestriction(hours, old.mode))
        },
    )

    private val answer: MutableState<BarrierLockedAnswer?> = mutableStateOf(null)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        snapshotFlow { answer.value }
            .onEach {
                updateButtonPanel()
                checkIsFormComplete()
            }
            .launchIn(lifecycleScope)

        binding.composeViewBase.content { Surface {
            val answer2 = answer.value
            if (answer2 is LockedAtHours)
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(Res.string.quest_fee_answer_yes_but))
                    TimeRestrictionInput(
                        timeRestriction = answer2.timeRestriction,
                        onChange = { answer.value = LockedAtHours(it) },
                        countryInfo = countryInfo,
                        allowSelectNoRestriction = false,
                    )
                }
        } }
    }

    override fun onClickOk() {
        answer.value?.let { applyAnswer(it) }

    }

    override fun isRejectingClose(): Boolean = answer.value != null

    override fun isFormComplete() = (answer.value as? LockedAtHours)?.timeRestriction?.isComplete() == true
}
