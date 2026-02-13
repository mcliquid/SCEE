package de.westnordost.streetcomplete.quests.bench_material

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.AItemSelectQuestForm
import de.westnordost.streetcomplete.quests.AnswerItem
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import kotlinx.serialization.serializer

class AddBenchMaterialForm : AItemSelectQuestForm<BenchMaterial, BenchMaterial>() {

    override val items = BenchMaterial.entries.filterNot { it == BenchMaterial.PICNIC }
    override val serializer = serializer<BenchMaterial>()

    override val otherAnswers by lazy {
        if (element.tags["amenity"] == "bench")
            listOf(AnswerItem(R.string.quest_bench_answer_picnic_table) { applyAnswer(BenchMaterial.PICNIC, true) })
        else emptyList()
    }

    @Composable override fun ItemContent(item: BenchMaterial) {
        ImageWithLabel(painterResource(item.icon!!), stringResource(item.title!!))
    }

    override val itemsPerRow = 3

    override fun onClickOk(selectedItem: BenchMaterial) {
        applyAnswer(selectedItem)
    }
}
