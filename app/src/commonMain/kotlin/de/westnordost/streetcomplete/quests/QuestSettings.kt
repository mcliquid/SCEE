package de.westnordost.streetcomplete.quests

import android.content.Context
import android.text.InputType
import android.text.method.LinkMovementMethod
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import androidx.core.widget.doAfterTextChanged
import com.github.difflib.text.DiffRow.Tag
import com.github.difflib.text.DiffRowGenerator
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.elementfilter.ParseException
import de.westnordost.streetcomplete.data.elementfilter.toElementFilterExpression
import de.westnordost.streetcomplete.data.osm.osmquests.OsmFilterQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuestController
import de.westnordost.streetcomplete.util.ktx.dpToPx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.PatternSyntaxException

// todo: all this is not multi-platform at all, but it compiles for android, so...

fun OsmElementQuestType<*>.getPrefixedFullElementSelectionPref(prefs: Preferences) = "${questPrefix(prefs)}qs_${name}_full_element_selection"

fun questPrefix(prefs: Preferences) = if (prefs.getBoolean(Prefs.QUEST_SETTINGS_PER_PRESET, false))
    prefs.getLong(Preferences.SELECTED_EDIT_TYPE_PRESET, 0).toString() + "_"
else
    ""

fun getLabelSources(defaultValue: String, questType: OsmFilterQuestType<*>, prefs: Preferences) =
    prefs.getString(getPrefixedLabelSourcePref(questType, prefs),
        defaultValue
    ).split(",").map { it.trim() }

fun getPrefixedLabelSourcePref(questType: OsmElementQuestType<*>, prefs: Preferences) = "${questPrefix(prefs)}qs_${questType.name}_label_sources"

/** For setting full element selection.
 *  This will check validity of input and only allow saving selection can be parsed.
 */
fun fullElementSelectionDialog(context: Context, prefs: Preferences, pref: String, messageId: Int, defaultValue: String): AlertDialog {
    val textInput = EditText(context)
    val checkPrefix = if (pref.endsWith("_full_element_selection")) "" else "nodes with "

    val message = HtmlCompat.fromHtml(context.getString(messageId), HtmlCompat.FROM_HTML_MODE_LEGACY)

    val dialog = dialog(context, messageId, prefs.getString(pref, defaultValue.trimIndent()), textInput)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            if (textInput.text.toString() == prefs.getString(pref, defaultValue.trimIndent())) return@setPositiveButton
            prefs.putString(pref, textInput.text.toString())
            OsmQuestController.reloadQuestTypes()
        }
        .setNeutralButton(R.string.quest_settings_reset) { _, _ ->
            prefs.remove(pref)
            OsmQuestController.reloadQuestTypes()
        }
        .setMessage(message)
        .setView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(textInput)
            if (prefs.contains(pref))
                addView(getDiffButton(context, defaultValue) { textInput.text.toString() })
        })
        .create()
    textInput.doAfterTextChanged {
        val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val isValidFilterExpression by lazy {
            try {
                (checkPrefix + it).toElementFilterExpression()
                toastyJob?.cancel()
                true
            } catch(e: ParseException) {
                delayedToast(e.message, context)
                false
            } catch(e: PatternSyntaxException) {
                delayedToast(e.message, context)
                false
            }
        }
        button?.isEnabled = textInput.text.toString().let {
            // check other stuff first, because creation filter expression is relatively slow
            (checkPrefix.isEmpty() || it.lowercase().matches(elementSelectionRegex))
                && it.count { c -> c == '('} == it.count { c -> c == ')'}
                && (it.contains('=') || it.contains('~') || it.contains('!'))
                && isValidFilterExpression
        }
    }
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled = prefs.contains(pref) // disable reset button if setting is default
        dialog.findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance() // make the link actually open a browser
    }
    return dialog
}

private var toastyJob: Job? = null
private fun delayedToast(message: String?, context: Context) {
    toastyJob?.cancel()
    toastyJob = GlobalScope.launch(Dispatchers.IO) {
        delay(3000)
        withContext(Dispatchers.Main) { Toast.makeText(context, "Error: $message", Toast.LENGTH_LONG).show() }
    }
}

private fun getDiffButton(context: Context, defaultText: String, getCurrentText: () -> String) =
    Button(context).apply {
        setText(R.string.quest_settings_highlight_changes_button)
        setOnClickListener {
            val drg = DiffRowGenerator.create()
                .showInlineDiffs(true)
                .mergeOriginalRevised(true)
                .inlineDiffByWord(true)
                .ignoreWhiteSpaces(true)
                .oldTag { f -> if (f) "<b><i><del>" else "</del></i></b>" }
                .newTag { f -> if (f) "<b><u><ins>" else "</ins></u></b>" }
                .build()
            val thatSpace = " "
            val newDefault = defaultText.replace("|", "$thatSpace|$thatSpace") // replace with (nearly) invisible space, so word differences are used
            val newCurrent = getCurrentText().replace("|", "$thatSpace|$thatSpace")
            val diffRows = drg.generateDiffRows(newDefault.split("\n"), newCurrent.split("\n"))
            val diffText = diffRows.mapNotNull {
                if (it.tag == Tag.EQUAL) return@mapNotNull null
                it.oldLine
            }.joinToString("<br>")
            AlertDialog.Builder(context)
                .setMessage(HtmlCompat.fromHtml(diffText, HtmlCompat.FROM_HTML_MODE_LEGACY))
                .setNegativeButton(R.string.close, null)
                .show()
        }
    }

fun booleanQuestSettingsDialog(context: Context, prefs: Preferences, pref: String, messageId: Int, answerYes: Int, answerNo: Int): AlertDialog =
    AlertDialog.Builder(context)
        .setMessage(messageId)
        .setNeutralButton(android.R.string.cancel, null)
        .setPositiveButton(answerYes) { _,_ ->
            prefs.putBoolean(pref, true)
            OsmQuestController.reloadQuestTypes()
        }
        .setNegativeButton(answerNo) { _,_ ->
            prefs.putBoolean(pref, false)
            OsmQuestController.reloadQuestTypes()
        }
        .create()

fun dialog(context: Context, messageId: Int, initialValue: String, input: EditText): AlertDialog.Builder {
    input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
    val padding = context.resources.dpToPx(8).toInt()
    input.setPadding(2 * padding, padding, 2 * padding, padding) // should be less than default padding to allow more text per line
    input.setText(initialValue)
    input.maxLines = 15 // if lines are not limited, the edit text might get so big that buttons are off screen (thanks, google for allowing this)
    return AlertDialog.Builder(context)
        .setMessage(messageId)
        .setView(input)
        .setNegativeButton(android.R.string.cancel, null)
}

// relax a little bit? but e.g. A-Z is very uncommon and might lead to mistakes
private val elementSelectionRegex = "[a-z\\d_=!?\"~*\\[\\]()|:.,<>\\s+-]+".toRegex()
