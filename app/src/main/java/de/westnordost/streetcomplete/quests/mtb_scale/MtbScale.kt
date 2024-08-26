package de.westnordost.streetcomplete.quests.mtb_scale

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import de.westnordost.streetcomplete.R

import de.westnordost.streetcomplete.view.image_select.GroupableDisplayItem
import de.westnordost.streetcomplete.view.image_select.Item
import de.westnordost.streetcomplete.quests.mtb_scale.MtbScale.*

enum class MtbScale(
    val osmValue: String,
    @DrawableRes val imageResId: Int?,
    @StringRes val titleResId: Int,
    @StringRes val descriptionResId: Int,
) {
    ZERO(
        osmValue = "0",
        imageResId = R.drawable.mtb_scale_0,
        titleResId = R.string.quest_mtbScale_zero,
        descriptionResId = R.string.quest_mtbScale_zero_description
    ),
    ONE(
        osmValue = "1",
        imageResId = R.drawable.mtb_scale_1,
        titleResId = R.string.quest_mtbScale_one,
        descriptionResId = R.string.quest_mtbScale_one_description
    ),
    TWO(
        osmValue = "2",
        imageResId = R.drawable.mtb_scale_2,
        titleResId = R.string.quest_mtbScale_two,
        descriptionResId = R.string.quest_mtbScale_two_description
    ),
    THREE(
        osmValue = "3",
        imageResId = R.drawable.mtb_scale_3,
        titleResId = R.string.quest_mtbScale_three,
        descriptionResId = R.string.quest_mtbScale_three_description
    ),
    FOUR(
        osmValue = "4",
        imageResId = R.drawable.mtb_scale_4,
        titleResId = R.string.quest_mtbScale_four,
        descriptionResId = R.string.quest_mtbScale_four_description
    ),
    FIVE(
        osmValue = "5",
        imageResId = R.drawable.mtb_scale_5,
        titleResId = R.string.quest_mtbScale_five,
        descriptionResId = R.string.quest_mtbScale_five_description
    ),
    SIX(
        osmValue = "6",
        imageResId = R.drawable.mtb_scale_6,
        titleResId = R.string.quest_mtbScale_six,
        descriptionResId = R.string.quest_mtbScale_six_description
    )
}

fun Collection<MtbScale>.toItems() = map { it.asItem() }

fun MtbScale.asItem(): GroupableDisplayItem<MtbScale> {
    return Item(
        this,
        drawableId = imageResId,
        titleId = titleResId,
        descriptionId = descriptionResId
    )
}
