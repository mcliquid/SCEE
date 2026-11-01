package de.westnordost.streetcomplete.data.download.strategy

import de.westnordost.streetcomplete.data.download.tiles.DownloadedTilesSource
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataSource

/** Download strategy if user is on mobile data */
class MobileDataAutoDownloadStrategy(
    mapDataSource: MapDataSource,
    downloadedTilesSource: DownloadedTilesSource
) : AVariableRadiusStrategy(mapDataSource, downloadedTilesSource) {

    // 2.25 times the area and element budget gives 1.5 times the radius at every density.
    override val maxDownloadAreaInKm2 = 22.5 // radius about 2.68 km
    override val desiredScoredMapDataCountInVicinity = 16875
}
