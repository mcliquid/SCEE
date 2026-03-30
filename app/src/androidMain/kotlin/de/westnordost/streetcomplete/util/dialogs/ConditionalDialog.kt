package de.westnordost.streetcomplete.util.dialogs

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatSpinner
import androidx.appcompat.widget.SwitchCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.osm.opening_hours.HierarchicOpeningHours
import de.westnordost.streetcomplete.osm.opening_hours.toOpeningHours
import de.westnordost.streetcomplete.ui.common.DropdownButton
import de.westnordost.streetcomplete.ui.common.dialogs.ScrollableAlertDialog
import de.westnordost.streetcomplete.ui.common.opening_hours.OpeningHoursTable
import de.westnordost.streetcomplete.ui.common.opening_hours.TimeMode
import de.westnordost.streetcomplete.util.ktx.showKeyboard
import de.westnordost.streetcomplete.util.setViewWithDefaultPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 *  Allows selecting a key from [keys] and a value from [values] for a restriction like key=value @ (<conditional restrictions>).
 *  If [values] is null, a text field is shown instead of a selection dropdown, with the input restricted to [valueInputType].
 *  The conditional restrictions are weight, length, width and time.
 */
private val valueRegex = "[a-zA-Z0-9]+ @ \\(.+\\)".toRegex()
@Composable
fun AddConditionalDialog(
    onDismissRequest: () -> Unit,
    keys: List<String>,
    values: List<String>?,
    numberOnly: Boolean,
    countryInfo: CountryInfo,
    onClickOk: (String, String) -> Unit
) {
    var isValid by remember { mutableStateOf(false) }
    var fullValue by remember { mutableStateOf(TextFieldValue()) }
    var key by remember { mutableStateOf(keys.first()) }
    var value by remember { mutableStateOf(values?.first() ?: "") }
    var weight by remember { mutableStateOf(TextFieldValue("")) }
    var weightGtLt by remember { mutableStateOf("<") }
    var length by remember { mutableStateOf(TextFieldValue("")) }
    var lengthGtLt by remember { mutableStateOf("<") }
    var width by remember { mutableStateOf(TextFieldValue("")) }
    var widthGtLt by remember { mutableStateOf("<") }
    var hours by remember { mutableStateOf(HierarchicOpeningHours()) }
    val valueOptions = KeyboardOptions(keyboardType = if (numberOnly) KeyboardType.Number else KeyboardType.Text)

    LaunchedEffect(fullValue) {
        isValid = fullValue.text.matches(valueRegex) && fullValue.text.length < 255
    }
    LaunchedEffect(value, weight, weightGtLt, length, lengthGtLt, width, widthGtLt, hours) {
        val conditions = mutableListOf<String>()
        if (weight.text != "") conditions.add("weight $weightGtLt ${weight.text}")
        if (length.text != "") conditions.add("length $lengthGtLt ${length.text}")
        if (width.text != "") conditions.add("width $widthGtLt ${width.text}")
        if (hours.isComplete()) conditions.add(hours.toOpeningHours().toString())
        fullValue = TextFieldValue("$value @ (${conditions.joinToString(" AND ")})")
    }

    ScrollableAlertDialog(
        onDismissRequest = onDismissRequest,
        buttons = {
            TextButton(onDismissRequest) { Text(stringResource(R.string.cancel)) }
            TextButton({ onClickOk("$key:conditional", fullValue.text) }, enabled = isValid) {
                Text(stringResource(R.string.ok))
            }
        },
        content = {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), Arrangement.spacedBy(6.dp)) {
                // key dropdown
                DropdownButton(
                    items = keys,
                    onSelectedItem = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    selectedItem = keys.first(),
                    itemContent = { Text(it, Modifier.weight(1f)) }
                )
                // value dropdown or input field
                if (values != null)
                    DropdownButton(
                        items = values,
                        onSelectedItem = { value = it },
                        modifier = Modifier.fillMaxWidth(),
                        selectedItem = values.first(),
                        itemContent = { Text(it, Modifier.weight(1f)) }
                    )
                else
                    TextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text("value") },
                        keyboardOptions = valueOptions
                    )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        weight,
                        { weight = it },
                        label = { Text("weight") },
                        modifier = Modifier.weight(0.5f),
                        keyboardOptions = valueOptions
                    )
                    Text(weightGtLt)
                    Switch(weightGtLt != "<", { weightGtLt = if (it) ">" else "<" })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        length,
                        { length = it },
                        label = { Text("length") },
                        modifier = Modifier.weight(0.5f),
                        keyboardOptions = valueOptions
                    )
                    Text(lengthGtLt)
                    Switch(lengthGtLt != "<", { lengthGtLt = if (it) ">" else "<" })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        width,
                        { width = it },
                        label = { Text("width") },
                        modifier = Modifier.weight(0.5f),
                        keyboardOptions = valueOptions
                    )
                    Text(widthGtLt)
                    Switch(widthGtLt != "<", { widthGtLt = if (it) ">" else "<" })
                }
                OpeningHoursTable(
                    openingHours = hours,
                    onChange = { hours = it },
                    timeMode = TimeMode.Spans,
                    countryInfo = countryInfo,
                    addButtonContent = { Text(stringResource(R.string.quest_fee_add_times)) },
                    locale = countryInfo.userPreferredLocale,
                    userLocale = Locale.current,
                )
                Divider()
                TextField(fullValue, { fullValue = it })
            }
        }
    )
}

@Suppress("KotlinConstantConditions") // because this is simply incorrect...
@SuppressLint("SetTextI18n") // this is the value, and should absolutely not be translated
fun showAddConditionalDialog(context: Context, keys: List<String>, values: List<String>?, valueInputType: Int?, onClickOk: (String, String) -> Unit) {
    var key = ""
    var value = ""
    val conditions = mutableMapOf<String, String>() // key is time, weight, length,... and values are the limitation strings
    var dialog: AlertDialog? = null

    fun isOk(text: String): Boolean =
        key.isNotBlank()
            && ((values != null && text.substringBefore(" @") in values) || text.substringBefore("@").isNotBlank())
            && text.contains('@')
            && text.count { c -> c == '('} == 1 && text.count { c -> c == ')'} == 1
            && "()" !in text

    val valueEditText = EditText(context).apply {
        doAfterTextChanged {
            dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = isOk(it.toString())
        }
    }

    fun createFullValue() {
        valueEditText.setText("$value @ (${conditions.values.joinToString(" AND ")})")
    }

    val keySpinner = AppCompatSpinner(context).apply {
        adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, keys)
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long, ) {
                key = keys[position]
            }
            override fun onNothingSelected(p0: AdapterView<*>?) { }
        }
    }

    val valueView = if (values == null)
            EditText(context).apply {
                hint = "value"
                valueInputType?.let { inputType = it }
                doAfterTextChanged {
                    value = it.toString()
                    createFullValue()
                }
            }
        else
            AppCompatSpinner(context).apply {
                adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, values)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long, ) {
                        value = values[position]
                        createFullValue()
                    }
                    override fun onNothingSelected(p0: AdapterView<*>?) { }
                }
            }

    fun numericBox(type: String, textResId: Int): View {
        val box = CheckBox(context)
        var conditionText = ""
        val switch = SwitchCompat(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 0.4f)
            isEnabled = false
            text = "<"
            setOnCheckedChangeListener { _, b ->
                text = if (b) ">" else "<"
                conditions[type] = "$type$text$conditionText"
                createFullValue()
            }
        }
        box.apply {
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 0.6f)
            setText(textResId)
            setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    // allow selecting < and >? just let the user type it manually for now
                    var textDialog: AlertDialog? = null
                    val text = EditText(context).apply {
                        hint = type
                        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        doAfterTextChanged { textDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = it?.toString()?.toFloatOrNull() != null }
                    }
                    textDialog = AlertDialog.Builder(context)
                        .setView(text)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            conditionText = text.text.toString()
                            conditions[type] = "$type${switch.text}${text.text}"
                            createFullValue()
                        }
                        .setOnCancelListener { isChecked = false }
                        .create()
                    textDialog.setOnShowListener {
                        dialog?.lifecycleScope?.launch {
                            delay(20) // without this, the keyboard sometimes isn't showing
                            text.requestFocus()
                            text.showKeyboard()
                        }
                        textDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
                    }
                    textDialog.show()
                } else {
                    conditions.remove(type)
                    createFullValue()
                }
                switch.isEnabled = checked
            }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(box)
            addView(switch)
        }
    }
/*
    val timeBox = CheckBox(context).apply {
        setText(de.westnordost.streetcomplete.R.string.access_time_limit)
        setOnCheckedChangeListener { _, checked ->
            if (checked && "time" !in conditions) {
                // todo: use user preferred locale?
                val dW = WeekdaysPickerDialog.show(context, null, /*countryInfo.userPreferredLocale*/ Locale.current) { weekdays ->
                    val dT = TimeRangePickerDialog(
                        context,
                        context.getString(de.westnordost.streetcomplete.R.string.time_limited_from),
                        context.getString(de.westnordost.streetcomplete.R.string.time_limited_to),
                        TimeRange(8 * 60, 18 * 60, false),
                        DateFormat.is24HourFormat(context)
                    ) { timeRange ->
                        val oh = listOf(OpeningWeekdaysRow(weekdays, timeRange)).toOpeningHours()
                        conditions["time"] = oh.toString()
                        createFullValue()
                    }
                    dT.setOnDismissListener { isChecked = !conditions["time"].isNullOrBlank() }
                    dT.show()
                }
                dW.setOnDismissListener { isChecked = !conditions["time"].isNullOrBlank() }
                dW.show()
            } else if (!checked) {
                conditions.remove("time")
                createFullValue()
            }
        }
    }*/
    val layout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(keySpinner)
        addView(valueView)
        // todo: more numeric things? there is stay, but this is not really numeric... has hours / minutes
        //  though this could be translated and only take minutes?
        addView(numericBox("weight", de.westnordost.streetcomplete.R.string.access_weight_limit))
        addView(numericBox("length", de.westnordost.streetcomplete.R.string.access_length_limit))
        addView(numericBox("width", de.westnordost.streetcomplete.R.string.access_width_limit))
//        addView(timeBox)
        addView(valueEditText)
    }
    dialog = AlertDialog.Builder(context)
        .setViewWithDefaultPadding(layout)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            val fullValue = valueEditText.text.toString()
            if (isOk(fullValue))
                onClickOk("$key:conditional", fullValue)
        }
        .setNegativeButton(android.R.string.cancel, null)
        .create()
    dialog.show()
}
/*
// similar, but with some access tags instead of numeric restrictions
// todo: maybe no key list necessary, and maybe no value list too?
fun showOtherConditionalDialog(context: Context, keys: List<String>, values: List<String>?, valueInputType: Int?, onClickOk: (String, String) -> Unit) {
    var key = ""
    var value = ""
    val conditions = mutableMapOf<String, String>() // key is time, weight, length,... and values are the limitation strings
    var dialog: AlertDialog? = null

    fun isOk(text: String): Boolean =
        key.isNotBlank()
            && ((values != null && text.substringBefore(" @") in values) || text.substringBefore("@").isNotBlank())
            && text.contains('@')
            && text.count { c -> c == '('} == 1 && text.count { c -> c == ')'} == 1
            && "()" !in text

    val valueEditText = EditText(context).apply {
        doAfterTextChanged {
            dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = isOk(it.toString())
        }
    }

    fun createFullValue() {
        valueEditText.setText("$value @ (${conditions.values.joinToString(" AND ")})")
    }

    val keySpinner = AppCompatSpinner(context).apply {
        adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, keys)
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long, ) {
                key = keys[position]
            }
            override fun onNothingSelected(p0: AdapterView<*>?) { }
        }
    }

    val valueView = if (values == null)
        EditText(context).apply {
            hint = "value, leave empty for none" // todo: string resource
            valueInputType?.let { inputType = it }
            doAfterTextChanged {
                value = it.toString().ifBlank { "none" }
                createFullValue()
            }
            value = "none"
        }
    else
        AppCompatSpinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, values)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long, ) {
                    value = values[position]
                    createFullValue()
                }
                override fun onNothingSelected(p0: AdapterView<*>?) { }
            }
        }

    val accessSpinner = AppCompatSpinner(context).apply {
        val v = listOf(context.getString(R.string.quest_select_hint), "destination", "delivery", "agricultural", "forestry", "private")
        adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, v)
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long, ) {
                if (position == 0)
                    conditions.remove("access")
                else
                    conditions["access"] = v[position]
                createFullValue()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) { }
        }
    }

    val timeBox = CheckBox(context).apply {
        setText(de.westnordost.streetcomplete.R.string.access_time_limit)
        setOnCheckedChangeListener { _, checked ->
            if (checked && "time" !in conditions) {
                val dW = WeekdaysPickerDialog.show(context, null, /*countryInfo.userPreferredLocale*/ Locale.current) { weekdays ->
                    val dT = TimeRangePickerDialog(
                        context,
                        context.getString(de.westnordost.streetcomplete.R.string.time_limited_from),
                        context.getString(de.westnordost.streetcomplete.R.string.time_limited_to),
                        TimeRange(8 * 60, 18 * 60, false),
                        DateFormat.is24HourFormat(context)
                    ) { timeRange ->
                        val oh = listOf(OpeningWeekdaysRow(weekdays, timeRange)).toOpeningHours()
                        conditions["time"] = oh.toString()
                        createFullValue()
                    }
                    dT.setOnDismissListener { isChecked = !conditions["time"].isNullOrBlank() }
                    dT.show()
                }
                dW.setOnDismissListener { isChecked = !conditions["time"].isNullOrBlank() }
                dW.show()
            } else if (!checked) {
                conditions.remove("time")
                createFullValue()
            }
        }
    }
    val layout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(keySpinner)
        addView(valueView)
        addView(timeBox)
        addView(accessSpinner)
        addView(valueEditText)
    }
    dialog = AlertDialog.Builder(context)
        .setViewWithDefaultPadding(layout)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            val fullValue = valueEditText.text.toString()
            if (isOk(fullValue))
                onClickOk("$key:conditional", fullValue)
        }
        .setNegativeButton(android.R.string.cancel, null)
        .create()
    dialog.show()
    createFullValue()
}
*/
