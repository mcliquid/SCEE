package de.westnordost.streetcomplete.quests.railway_platform_ref

import android.os.Bundle
import android.view.View
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.TextField
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.ComposeViewBinding
import de.westnordost.streetcomplete.quests.AbstractOsmQuestForm
import de.westnordost.streetcomplete.ui.theme.extraLargeInput
import de.westnordost.streetcomplete.ui.util.content

class AddRailwayPlatformRefForm : AbstractOsmQuestForm<String>() {
    override val contentLayoutResId = R.layout.compose_view
    private val binding by contentViewBinding(ComposeViewBinding::bind)

    private val ref: MutableState<String> = mutableStateOf("")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.composeViewBase.content { Surface {
            TextField(
                value = ref.value,
                onValueChange = {
                    ref.value = it
                    checkIsFormComplete()
                },
                textStyle = MaterialTheme.typography.extraLargeInput,
            )
        } }
    }

    override fun onClickOk() {
        applyAnswer(ref.value)
    }

    override fun isFormComplete() = ref.value.isNotEmpty()
}
