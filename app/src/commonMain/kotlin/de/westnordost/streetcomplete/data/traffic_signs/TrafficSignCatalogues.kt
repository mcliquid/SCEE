package de.westnordost.streetcomplete.data.traffic_signs

import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignCatalogue
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.ui.ktx.readBytesOrNull
import de.westnordost.streetcomplete.util.SynchronizedLazyCache
import de.westnordost.streetcomplete.util.logs.Log
import kotlinx.coroutines.runBlocking

/** Provides the traffic sign catalogues bundled with the app (files/traffic_signs/{country}.json,
 *  see UpdateTrafficSignsTask). Only [SUPPORTED_COUNTRIES] are available. */
class TrafficSignCatalogues internal constructor(
    /** loads the catalogue JSON for the given country, or null if there is none */
    private val loadJson: (country: String) -> String?
) {
    constructor(res: Res) : this({ country -> load(res, country) })

    private val catalogues = SynchronizedLazyCache<String, TrafficSignCatalogue?> { country ->
        try {
            val json = loadJson(country)
            if (json == null) {
                null
            } else {
                // a broken sign is left out and logged, the others stay usable (see parseLenient)
                val result = TrafficSignCatalogue.parseLenient(json)
                for (problem in result.problems) Log.w(TAG, "Problem in traffic sign catalogue of $country: $problem")
                if (result.catalogue == null) Log.e(TAG, "Unable to use traffic sign catalogue of $country")
                result.catalogue
            }
        } catch (e: Exception) {
            // never an error for the one who opens the quest or overlay
            Log.e(TAG, "Unable to load traffic sign catalogue of $country", e)
            null
        }
    }

    /** Get the catalogue of the given country (e.g. "DE"), null if there is none. Loads it
     *  synchronously on first access, so this should not be called on the main thread for the
     *  first time. */
    fun get(country: String): TrafficSignCatalogue? =
        if (country in SUPPORTED_COUNTRIES) catalogues.get(country) else null

    companion object {
        val SUPPORTED_COUNTRIES = setOf("DE")
        private const val TAG = "TrafficSignCatalogues"
    }
}

private fun load(res: Res, country: String): String? =
    runBlocking { res.readBytesOrNull("files/traffic_signs/$country.json")?.decodeToString() }
