package de.westnordost.streetcomplete.util.ktx

import android.content.res.Resources
import androidx.core.util.TypedValueCompat

fun Resources.dpToPx(dp: Number): Float = TypedValueCompat.dpToPx(dp.toFloat(), displayMetrics)
