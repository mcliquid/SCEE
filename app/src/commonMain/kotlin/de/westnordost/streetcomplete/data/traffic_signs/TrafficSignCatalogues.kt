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
        val json = loadJson(country) ?: return@SynchronizedLazyCache null
        try {
            TrafficSignCatalogue.parse(json)
        } catch (e: IllegalArgumentException) {
            // also kotlinx.serialization.SerializationException is an IllegalArgumentException
            Log.e(TAG, "Unable to parse traffic sign catalogue of $country", e)
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
