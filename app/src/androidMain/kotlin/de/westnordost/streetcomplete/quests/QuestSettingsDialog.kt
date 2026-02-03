package de.westnordost.streetcomplete.quests

import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.util.TypedValue
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osm.osmquests.OsmFilterQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuestController
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.util.dialogs.setViewWithDefaultPadding

// restarts are typically necessary on changes of element selection because the filter is created by lazy
// quests settings should follow the pattern: qs_<quest_name>_<something>, e.g. "qs_AddLevel_more_levels"
// when to call reloadQuestTypes: if whatever is changed is not read from settings every time, or if dynamic quest creation is enabled

/** for setting values of a single key, comma separated */
fun singleTypeElementSelectionDialog(
    context: Context,
    prefs: Preferences,
    pref: String,
    defaultValue: String,
    messageId: Int,
    onChanged: () -> Unit = { OsmQuestController.reloadQuestTypes() }
): AlertDialog {
    val textInput = EditText(context)
    val dialog = dialog(context, messageId, prefs.getString(pref, defaultValue)!!.replace("|",", "), textInput)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            val prefText = textInput.text.toString().split(",").joinToString("|") { it.trim() }
            if (prefs.getString(pref, defaultValue) == prefText) return@setPositiveButton
            prefs.putString(pref, prefText)
            onChanged()
        }
        .setNeutralButton(R.string.quest_settings_reset) { _, _ ->
            prefs.remove(pref)
            onChanged()
        }
        .create()
    textInput.doAfterTextChanged {
        val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        button?.isEnabled = textInput.text.toString().let {
            it.lowercase().matches(valueRegex)
                && !it.trim().endsWith(',')
                && !it.contains(",,")
                && it.isNotEmpty() }
    }
    dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled = prefs.contains(pref) }
    return dialog
}

/** for setting values of a single number */
fun numberSelectionDialog(context: Context, prefs: Preferences, pref: String, defaultValue: Int, messageId: Int): AlertDialog {
    val numberInput = EditText(context)
    numberInput.inputType = InputType.TYPE_CLASS_NUMBER
    numberInput.setText(prefs.getInt(pref, defaultValue).toString())
    val dialog = AlertDialog.Builder(context)
        .setMessage(messageId)
        .setViewWithDefaultPadding(numberInput)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(android.R.string.ok) { _,_ ->
            numberInput.text.toString().toIntOrNull()?.let {
                if (it == prefs.getInt(pref, it)) return@setPositiveButton
                prefs.putInt(pref, it)
                if (prefs.getBoolean(Prefs.DYNAMIC_QUEST_CREATION, false))
                    OsmQuestController.reloadQuestTypes()
            }
        }
        .setNeutralButton(R.string.quest_settings_reset) { _, _ ->
            prefs.remove(pref)
            if (prefs.getBoolean(Prefs.DYNAMIC_QUEST_CREATION, false))
                OsmQuestController.reloadQuestTypes()
        }
        .create()
    numberInput.doAfterTextChanged {
        val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        button?.isEnabled = numberInput.text.toString().let { it.toIntOrNull() != null }
    }
    dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled = prefs.contains(pref) }
    return dialog
}

fun getLabelOrElementSelectionDialog(context: Context, questType: OsmFilterQuestType<*>, prefs: Preferences): AlertDialog {
    val description = TextView(context).apply {
        setText(R.string.quest_settings_dot_labels_message)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
    }
    val prefWithPrefix = getPrefixedLabelSourcePref(questType, prefs)
    val labels = EditText(context).apply {
        setText(prefs.getString(prefWithPrefix, questType.dotLabelSources.joinToString(", ")))
    }
    var d: AlertDialog? = null
    d = AlertDialog.Builder(context)
        .setViewWithDefaultPadding(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(description)
            addView(labels)
            addView(Button(context).apply {
                setText(R.string.element_selection_button)
                setOnClickListener {
                    fullElementSelectionDialog(context, prefs, questType.getPrefixedFullElementSelectionPref(prefs), R.string.quest_settings_element_selection, questType.elementFilter).show()
                    d?.dismiss()
                }
            })
        })
        .setPositiveButton(android.R.string.ok) { _, _ ->
            labels.text.toString().split(",")
            prefs.putString(prefWithPrefix, labels.text.toString())
            OsmQuestController.reloadQuestTypes()
        }
        .setNegativeButton(android.R.string.cancel, null)
        .setNeutralButton(R.string.quest_settings_reset) { _, _ ->
            prefs.remove(prefWithPrefix)
            OsmQuestController.reloadQuestTypes()
        }.create()
    d.setOnShowListener { d.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled = prefs.contains(prefWithPrefix) } // disable reset button if setting is default
    return d
}

private val valueRegex = "[a-z\\d_?,/\\s]+".toRegex()
