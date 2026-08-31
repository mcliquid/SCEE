package de.westnordost.streetcomplete.util.ktx

import android.graphics.Point
import android.view.View
import androidx.core.view.doOnPreDraw
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

suspend fun View.awaitPreDraw() = suspendCancellableCoroutine { cont ->
    val listener = doOnPreDraw { cont.resume(Unit) }
    cont.invokeOnCancellation { listener.removeListener() }
}

fun View.getLocationInWindow(): Point {
    val pos = IntArray(2)
    getLocationInWindow(pos)
    return Point(pos[0], pos[1])
}
