package de.westnordost.streetcomplete.screens.settings

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon

/** All track points of all segments of all tracks, in file order. Throws if the text is not a valid GPX file. */
internal expect fun parseGpxTrackPoints(gpx: String): List<LatLon>
