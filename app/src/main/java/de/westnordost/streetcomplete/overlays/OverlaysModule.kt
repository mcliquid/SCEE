package de.westnordost.streetcomplete.overlays

import android.content.SharedPreferences
import de.westnordost.countryboundaries.CountryBoundaries
import de.westnordost.streetcomplete.ApplicationConstants.EE_QUEST_OFFSET
import de.westnordost.osmfeatures.Feature
import de.westnordost.osmfeatures.FeatureDictionary
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.meta.CountryInfos
import de.westnordost.streetcomplete.data.meta.getByLocation
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.overlays.OverlayRegistry
import de.westnordost.streetcomplete.overlays.custom.CustomOverlay
import de.westnordost.streetcomplete.overlays.address.AddressOverlay
import de.westnordost.streetcomplete.overlays.buildings.BuildingsOverlay
import de.westnordost.streetcomplete.overlays.cycleway.CyclewayOverlay
import de.westnordost.streetcomplete.overlays.restriction.RestrictionOverlay
import de.westnordost.streetcomplete.overlays.shops.ShopsOverlay
import de.westnordost.streetcomplete.overlays.sidewalk.SidewalkOverlay
import de.westnordost.streetcomplete.overlays.things.ThingsOverlay
import de.westnordost.streetcomplete.overlays.street_parking.StreetParkingOverlay
import de.westnordost.streetcomplete.overlays.surface.SurfaceOverlay
import de.westnordost.streetcomplete.overlays.way_lit.WayLitOverlay
import de.westnordost.streetcomplete.util.ktx.getFeature
import de.westnordost.streetcomplete.util.ktx.getIds
import org.koin.core.qualifier.named
import org.koin.dsl.module

/* Each overlay is assigned an ordinal. This is used for serialization and is thus never changed,
*  even if the order of overlays is changed.  */
val overlaysModule = module {
    single {
        overlaysRegistry(
            { location ->
                val countryInfos = get<CountryInfos>()
                val countryBoundaries = get<Lazy<CountryBoundaries>>(named("CountryBoundariesLazy")).value
                countryInfos.getByLocation(countryBoundaries, location.longitude, location.latitude)
            },
            { location ->
                val countryBoundaries = get<Lazy<CountryBoundaries>>(named("CountryBoundariesLazy")).value
                countryBoundaries.getIds(location).firstOrNull()
            },
            { element ->
                get<Lazy<FeatureDictionary>>(named("FeatureDictionaryLazy")).value.getFeature(element)
            },
            get(),
        )
    }
}

fun overlaysRegistry(
    getCountryInfoByLocation: (LatLon) -> CountryInfo,
    getCountryCodeByLocation: (LatLon) -> String?,
    getFeature: (Element) -> Feature?,
    prefs: SharedPreferences,
) = OverlayRegistry(listOf(

    0 to WayLitOverlay(),
    6 to SurfaceOverlay(),
    1 to SidewalkOverlay(),
    5 to CyclewayOverlay(getCountryInfoByLocation),
    2 to StreetParkingOverlay(),
    3 to AddressOverlay(getCountryCodeByLocation),
    4 to ShopsOverlay(getFeature),
    8 to ThingsOverlay(getFeature),
    7 to BuildingsOverlay(),
    (EE_QUEST_OFFSET + 1) to RestrictionOverlay(),
    (EE_QUEST_OFFSET + 0) to CustomOverlay(prefs),
))
