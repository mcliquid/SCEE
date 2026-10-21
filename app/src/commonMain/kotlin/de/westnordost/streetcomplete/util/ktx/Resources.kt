package de.westnordost.streetcomplete.util.ktx

import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.allDrawableResources
import org.jetbrains.compose.resources.DrawableResource


val DrawableResource.name: String get() {
    drawableResourceToName[this]?.let { return it }
    val name = Res.allDrawableResources.entries.first { it.value == this }.key
    drawableResourceToName[this] = name
    return name
}

private val drawableResourceToName = hashMapOf<DrawableResource, String>()
