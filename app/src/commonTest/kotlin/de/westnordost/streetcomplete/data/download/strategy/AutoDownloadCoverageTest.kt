package de.westnordost.streetcomplete.data.download.strategy

import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.data.download.tiles.TilesRect
import de.westnordost.streetcomplete.data.download.tiles.DownloadedTilesSource
import de.westnordost.streetcomplete.data.download.tiles.enclosingTilePos
import de.westnordost.streetcomplete.data.download.tiles.enclosingTilesRect
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataSource
import de.westnordost.streetcomplete.util.math.area
import de.westnordost.streetcomplete.util.math.enclosingBoundingBox
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AutoDownloadCoverageTest {
    private val position = LatLon(52.52, 13.4)
    private val zoom = ApplicationConstants.DOWNLOAD_TILE_ZOOM
    private val initialTile = position.enclosingTilePos(zoom)
    private val mapData = mock<MapDataSource>()
    private val tiles = mock<DownloadedTilesSource>()

    private fun strategies() = listOf(
        Triple(MobileDataAutoDownloadStrategy(mapData, tiles), 10.0, 7500),
        Triple(WifiAutoDownloadStrategy(mapData, tiles), 20.0, 15000),
    )

    @Test fun `initial density probe stays one tile`() = runTest {
        every { tiles.contains(any(), any()) } returns false
        for ((strategy) in strategies()) {
            assertEquals(initialTile.asBoundingBox(zoom), strategy.getDownloadBoundingBox(position))
        }
    }

    @Test fun `sparse and empty areas have fifty percent larger capped radius`() = runTest {
        every { tiles.contains(any(), any()) } calls { (rect: TilesRect, _: Long) -> rect == initialTile.toTilesRect() }
        for (nodes in listOf(0, 1)) {
            every { mapData.getElementCounts(any()) } returns MapDataSource.ElementCounts(nodes, 0, 0)
            for ((strategy, oldArea) in strategies()) {
                val expectedRadius = sqrt(oldArea * 2.25 * 1_000_000 / PI)
                assertEquals(position.enclosingBoundingBox(expectedRadius), strategy.getDownloadBoundingBox(position))
            }
        }
    }

    @Test fun `dense areas have fifty percent larger density limited radius`() = runTest {
        every { tiles.contains(any(), any()) } calls { (rect: TilesRect, _: Long) -> rect == initialTile.toTilesRect() }
        every { mapData.getElementCounts(any()) } returns MapDataSource.ElementCounts(10000, 0, 0)
        val density = 10000 / initialTile.asBoundingBox(zoom).area()
        for ((strategy, _, oldBudget) in strategies()) {
            val expectedRadius = sqrt(oldBudget * 2.25 / (PI * density))
            assertEquals(position.enclosingBoundingBox(expectedRadius), strategy.getDownloadBoundingBox(position))
        }
    }

    @Test fun `fully cached expanded boundary does not redownload`() = runTest {
        every { mapData.getElementCounts(any()) } returns MapDataSource.ElementCounts(0, 0, 0)
        for ((strategy, oldArea) in strategies()) {
            val boundary = position.enclosingBoundingBox(sqrt(oldArea * 2.25 * 1_000_000 / PI)).enclosingTilesRect(zoom)
            every { tiles.contains(any(), any()) } calls { (rect: TilesRect, _: Long) -> rect == initialTile.toTilesRect() || rect == boundary }
            assertNull(strategy.getDownloadBoundingBox(position))
        }
    }
}
