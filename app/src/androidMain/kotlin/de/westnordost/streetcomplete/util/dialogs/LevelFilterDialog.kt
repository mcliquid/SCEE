package de.westnordost.streetcomplete.util.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.externalsource.ExternalSourceQuest
import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuest
import de.westnordost.streetcomplete.data.overlays.SelectedOverlayController
import de.westnordost.streetcomplete.data.overlays.SelectedOverlaySource
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.quest.VisibleQuestsSource
import de.westnordost.streetcomplete.data.visiblequests.LevelFilter
import de.westnordost.streetcomplete.data.visiblequests.VisibleEditTypeController
import de.westnordost.streetcomplete.databinding.DialogLevelFilterBinding
import de.westnordost.streetcomplete.osm.level.LevelTypes
import de.westnordost.streetcomplete.osm.level.parseSelectableLevels
import de.westnordost.streetcomplete.screens.main.map.maplibre.CameraPosition
import de.westnordost.streetcomplete.util.math.enclosingBoundingBox
import kotlin.math.ceil
import kotlin.math.floor

fun showLevelFilterDialog(
    context: Context,
    camera: CameraPosition?,
    levelFilter: LevelFilter,
    prefs: Preferences,
    visibleEditTypeController: VisibleEditTypeController,
    visibleQuestsSource: VisibleQuestsSource,
    selectedOverlaySource: SelectedOverlaySource,
    mapDataSource: MapDataWithEditsSource
) {
    val builder = AlertDialog.Builder(context)
    val binding = DialogLevelFilterBinding.inflate(LayoutInflater.from(context))
    builder.setTitle(R.string.level_filter_title)
    binding.level.setText(prefs.getString(Prefs.ALLOWED_LEVEL, ""))
    binding.enableSwitch.isChecked = levelFilter.isEnabled
    val levelTags = prefs.getString(Prefs.ALLOWED_LEVEL_TAGS, "level,repeat_on,level:ref").split(",")
    val allowedLevelTypes = LevelTypes.entries.filter { levelTags.contains(it.tag) }
    binding.plus.setOnClickListener {
        val selectableLevels = getLevelsInView(camera?.position?.enclosingBoundingBox(50.0), allowedLevelTypes, visibleQuestsSource, mapDataSource)
        val oldText = binding.level.text?.toString()
        val currentLevel = oldText?.let { "[\\d.+-]+".toRegex().find(it)?.value }
        val currentLevelNumber = currentLevel?.toDoubleOrNull()
        val newLevel = if (currentLevelNumber == null) {
            selectableLevels.find { it >= 0 } ?: selectableLevels.firstOrNull() ?: 0.0
        } else {
            val nextInt = floor(currentLevelNumber + 1.0)
            selectableLevels.find { it > currentLevelNumber && it < nextInt } ?: nextInt
        }
        binding.level.setText(oldText?.replace(currentLevel ?: oldText, newLevel.toNiceString()) ?: newLevel.toNiceString())
    }
    binding.minus.setOnClickListener {
        val selectableLevels = getLevelsInView(camera?.position?.enclosingBoundingBox(50.0), allowedLevelTypes, visibleQuestsSource, mapDataSource)
        val oldText = binding.level.text?.toString()
        val currentLevel = oldText?.let { "[\\d.+-]+".toRegex().find(it)?.value }
        val currentLevelNumber = currentLevel?.toDoubleOrNull()
        val newLevel = if (currentLevelNumber == null) {
            selectableLevels.findLast { it <= 0 } ?: selectableLevels.firstOrNull() ?: 0.0
        } else {
            val prevInt = ceil(currentLevelNumber - 1.0)
            selectableLevels.findLast { it < currentLevelNumber && it > prevInt } ?: prevInt
        }
        binding.level.setText(oldText?.replace(currentLevel ?: oldText, newLevel.toNiceString()) ?: newLevel.toNiceString())
    }

    binding.levelBox.isChecked = allowedLevelTypes.contains(LevelTypes.LEVEL)
    binding.repeatOnBox.isChecked = allowedLevelTypes.contains(LevelTypes.REPEAT_ON)
    binding.levelRefBox.isChecked = allowedLevelTypes.contains(LevelTypes.LEVEL_REF)
    binding.addrFloorBox.isChecked = allowedLevelTypes.contains(LevelTypes.ADDR_FLOOR)

    builder.setView(ScrollView(context).apply { addView(binding.root) })
    builder.setNegativeButton(android.R.string.cancel, null)
    builder.setPositiveButton(android.R.string.ok) { _, _ ->
        val levelTagList = mutableListOf<String>()
        if (binding.levelBox.isChecked) levelTagList.add("level")
        if ( binding.repeatOnBox.isChecked) levelTagList.add("repeat_on")
        if (binding.levelRefBox.isChecked) levelTagList.add("level:ref")
        if (binding.addrFloorBox.isChecked) levelTagList.add("addr:floor")
        prefs.putString(Prefs.ALLOWED_LEVEL_TAGS, levelTagList.joinToString(","))
        prefs.putString(Prefs.ALLOWED_LEVEL, binding.level.text.toString())
        levelFilter.isEnabled = binding.enableSwitch.isChecked
        levelFilter.reload()

        val overlayController = selectedOverlaySource as? SelectedOverlayController
        val tempOverlay = overlayController?.selectedOverlay
        if (tempOverlay != null) {
            // reload overlay (if enabled), also triggers quest reload unless HIDE_OVERLAY_QUESTS disabled
            overlayController.selectedOverlay = null
            overlayController.selectedOverlay = tempOverlay
            if (!prefs.getBoolean(Prefs.HIDE_OVERLAY_QUESTS, true))
                visibleEditTypeController.setVisibilities(emptyMap()) // trigger reload
        } else {
            visibleEditTypeController.setVisibilities(emptyMap()) // trigger reload
        }
    }
    builder.show()
}

private fun getLevelsInView(displayedArea: BoundingBox?, allowed: List<LevelTypes>, visibleQuestsSource: VisibleQuestsSource, mapDataSource: MapDataWithEditsSource): List<Double> {
    val tags = if (displayedArea != null) {
        visibleQuestsSource.getAll(displayedArea).mapNotNull {
            when (it) {
                is OsmQuest -> mapDataSource.get(it.elementType, it.elementId)
                is ExternalSourceQuest -> it.elementKey?.let { mapDataSource.get(it.type, it.id) }
                else -> null
            }?.tags
        }
    } else emptyList()
    return parseSelectableLevels(tags, allowed)
}

private fun Double.toNiceString(): String {
    if (toInt().toDouble() == this) return toInt().toString()
    return toString()
}
