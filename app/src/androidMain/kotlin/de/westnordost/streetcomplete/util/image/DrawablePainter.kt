package de.westnordost.streetcomplete.util.image

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalResources
import androidx.core.graphics.drawable.toBitmap
import de.westnordost.streetcomplete.util.ktx.dpToPx

@Composable
fun Drawable.toPainter(sizeDp: Int = 130): Painter {
    val px = LocalResources.current.dpToPx(sizeDp).toInt()
    return BitmapPainter(toBitmap(px, px).asImageBitmap())
}
