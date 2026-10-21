package de.westnordost.streetcomplete

import de.westnordost.osmfeatures.FeatureDictionary
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.screens.settings.parseGpxTrackPoints
import de.westnordost.streetcomplete.util.readBundledAsset
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.files.SystemFileSystem
import java.io.File
import java.net.URI

/** Invoked with only the copied distribution's JARs, outside the checkout. */
internal object DesktopDistributionProbe {
    @JvmStatic fun main(args: Array<String>) = runBlocking<Unit> {
        val resource = URI(Res.getUri("files/osmfeatures/default/presets.json")).toURL()
        check(resource.protocol == "jar") { "Probe must use packaged resources: $resource" }
        val directory = desktopMetadataDirectory(File(args.single()), resource)
        val dictionary = FeatureDictionary.create(SystemFileSystem, File(directory, "default").path, File(directory, "brands").path)
        check(dictionary.getById("amenity/bench", listOf("de", "en", null)) != null)
        Buffer().apply { write(readBundledAsset("boundaries.ser")) }.use {
            de.westnordost.countryboundaries.CountryBoundaries.deserializeFrom(it)
        }
        check(parseGpxTrackPoints("<gpx><trk><trkseg><trkpt lat=\"1\" lon=\"2\"/></trkseg></trk></gpx>").size == 1)
        check(readBundledAsset("tag_editor/keySuggestionsForFeature.json").isNotEmpty())
        println("PACKAGED_RESOURCES_OK feature dictionary, boundaries, GPX and tag suggestions; source=$resource")
    }
}
