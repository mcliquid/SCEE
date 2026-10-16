package de.westnordost.streetcomplete.data.meta

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.ui.ktx.readYamlOrNull
import de.westnordost.streetcomplete.util.SynchronizedLazyCache
import de.westnordost.streetcomplete.util.countryboundaries.CountryBoundaries
import kotlinx.coroutines.runBlocking

class CountryInfos internal constructor(
    /** loads the country info for the given region code, or null if there is none */
    private val load: (regionCode: String) -> IncompleteCountryInfo?
) {
    constructor(res: Res) : this({ regionCode -> load(res, regionCode) })

    // used concurrently by quest types during quest creation, hence synchronized
    private val countryInfos = SynchronizedLazyCache<String, IncompleteCountryInfo?>(load)
    private val default: IncompleteCountryInfo by lazy { load("default")!! }

    /** Get the info by a list of country codes sorted by size. E.g. DE-NI,DE gets the info
     *  for Lower Saxony in Germany and uses defaults from Germany */
    fun get(regionCode: List<String>): CountryInfo =
        CountryInfo(regionCode.firstOrNull(), regionCode.mapNotNull { get(it) } + default)

    private fun get(regionCode: String): IncompleteCountryInfo? = countryInfos.get(regionCode)
}

private val yaml = Yaml(
    configuration = YamlConfiguration(
        strictMode = false, // ignore unknown properties
    )
)

private fun load(res: Res, regionCode: String): IncompleteCountryInfo? =
    runBlocking {
        res.readYamlOrNull<IncompleteCountryInfo>("files/country_metadata/$regionCode.yml", yaml)
    }

fun CountryInfos.get(countryBoundaries: CountryBoundaries, position: LatLon): CountryInfo =
    get(countryBoundaries.getIds(position))
