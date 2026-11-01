package de.westnordost.streetcomplete.data.download.strategy

import de.westnordost.streetcomplete.data.download.tiles.DownloadedTilesSource
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataSource

/** Download strategy if user is on wifi */
class WifiAutoDownloadStrategy(
    mapDataSource: MapDataSource,
    downloadedTilesSource: DownloadedTilesSource
) : AVariableRadiusStrategy(mapDataSource, downloadedTilesSource) {

    /** Let's assume that if the user is on wifi, he is either at home, at work, in the hotel, at a
     * café,... in any case, somewhere that would act as a "base" from which he can go on an
     * excursion. Let's make sure he can, even if there is no or bad internet.
     */

    // 2.25 times the area and element budget gives 1.5 times the radius at every density.
    override val maxDownloadAreaInKm2 = 45.0 // radius about 3.78 km
    override val desiredScoredMapDataCountInVicinity = 33750
}
