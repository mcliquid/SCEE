package de.westnordost.streetcomplete.data.osm.edits.waterway_crossing

import de.westnordost.streetcomplete.data.osm.mapdata.*
import de.westnordost.streetcomplete.testutils.*

/** OpenStreetMap API way/full snapshots, 2026-10-13; OSM contributors, ODbL. */
internal fun osmCrossingPracticeData(vararg ids: Long): MutableMapDataWithGeometry {
    val data = MutableMapDataWithGeometry()
    for (id in ids) when (id) {
        785221627L -> {
            data.putElement(node(469979598L, LatLon(47.7572524, 9.1187644), timestamp = 0))
            data.putElement(node(7337306685L, LatLon(47.7575124, 9.1184908), timestamp = 0))
            data.putElement(node(7337306688L, LatLon(47.7575574, 9.1184774), timestamp = 0))
            data.putElement(node(7337306691L, LatLon(47.7574303, 9.1186812), timestamp = 0))
            data.putElement(node(7337306693L, LatLon(47.7576680, 9.1184620), timestamp = 0))
            data.putElement(node(7337306695L, LatLon(47.7574781, 9.1185498), timestamp = 0))
            data.putElement(node(7337306696L, LatLon(47.7577315, 9.1184130), timestamp = 0))
            data.putElement(node(7337306697L, LatLon(47.7573055, 9.1187747), timestamp = 0))
            data.putElement(node(7337306699L, LatLon(47.7573762, 9.1187469), timestamp = 0))
            data.putElement(node(10008347208L, LatLon(47.7577343, 9.1184091), timestamp = 0))
            data.putElement(node(10008347209L, LatLon(47.7577380, 9.1184040), timestamp = 0))
            data.putElement(node(11640683700L, LatLon(47.7572369, 9.1187419), timestamp = 0))
            data.putElement(way(785221627L, listOf(10008347209L, 10008347208L, 7337306696L, 7337306693L, 7337306688L, 7337306685L, 7337306695L, 7337306691L, 7337306699L, 7337306697L, 469979598L, 11640683700L), mapOf("bicycle" to "no", "foot" to "yes", "highway" to "path", "hiking" to "yes", "surface" to "dirt"), timestamp = 0))
        }
        1385223361L -> {
            data.putElement(node(12824936031L, LatLon(47.7575313, 9.1193915), timestamp = 0))
            data.putElement(node(12824936032L, LatLon(47.7574520, 9.1191754), timestamp = 0))
            data.putElement(node(12824936033L, LatLon(47.7573871, 9.1189098), timestamp = 0))
            data.putElement(node(12824936034L, LatLon(47.7572717, 9.1187298), timestamp = 0))
            data.putElement(way(1385223361L, listOf(12824936034L, 12824936033L, 12824936032L, 12824936031L), mapOf("waterway" to "stream"), timestamp = 0))
        }
        982009820L -> {
            data.putElement(node(260659133L, LatLon(47.7597947, 9.1125580), timestamp = 0))
            data.putElement(node(260659134L, LatLon(47.7597323, 9.1136141), timestamp = 0))
            data.putElement(node(260659137L, LatLon(47.7594081, 9.1140586), timestamp = 0))
            data.putElement(node(260659138L, LatLon(47.7592698, 9.1142116), timestamp = 0))
            data.putElement(node(260659139L, LatLon(47.7591683, 9.1146120), timestamp = 0))
            data.putElement(node(260659140L, LatLon(47.7592206, 9.1160228), timestamp = 0))
            data.putElement(node(260786136L, LatLon(47.7598202, 9.1121213), timestamp = 0))
            data.putElement(node(307983914L, LatLon(47.7591950, 9.1154409), timestamp = 0))
            data.putElement(node(461164787L, LatLon(47.7598028, 9.1129526), timestamp = 0))
            data.putElement(node(461164789L, LatLon(47.7597840, 9.1132760), timestamp = 0))
            data.putElement(node(461164790L, LatLon(47.7596432, 9.1138467), timestamp = 0))
            data.putElement(node(461164792L, LatLon(47.7591914, 9.1143902), timestamp = 0))
            data.putElement(node(461164801L, LatLon(47.7591717, 9.1148457), timestamp = 0))
            data.putElement(node(461164803L, LatLon(47.7591836, 9.1152145), timestamp = 0))
            data.putElement(node(469979590L, LatLon(47.7592073, 9.1156634), timestamp = 0))
            data.putElement(node(1595400850L, LatLon(47.7595337, 9.1139690), timestamp = 0))
            data.putElement(way(982009820L, listOf(260786136L, 260659133L, 461164787L, 461164789L, 260659134L, 461164790L, 1595400850L, 260659137L, 260659138L, 461164792L, 260659139L, 461164801L, 461164803L, 307983914L, 469979590L, 260659140L), mapOf("highway" to "track", "name" to "St. Katharinaweg", "surface" to "compacted", "tracktype" to "grade2"), timestamp = 0))
        }
        1385223359L -> {
            data.putElement(node(12824915076L, LatLon(47.7569764, 9.1080609), timestamp = 0))
            data.putElement(node(12824915084L, LatLon(47.7584428, 9.1126441), timestamp = 0))
            data.putElement(node(12824915089L, LatLon(47.7569543, 9.1079664), timestamp = 0))
            data.putElement(node(12824915090L, LatLon(47.7570697, 9.1084599), timestamp = 0))
            data.putElement(node(12824915091L, LatLon(47.7573366, 9.1094255), timestamp = 0))
            data.putElement(node(12824915092L, LatLon(47.7574881, 9.1098976), timestamp = 0))
            data.putElement(node(12824915093L, LatLon(47.7579208, 9.1104984), timestamp = 0))
            data.putElement(node(12824915094L, LatLon(47.7583247, 9.1108203), timestamp = 0))
            data.putElement(node(12824915095L, LatLon(47.7584762, 9.1113031), timestamp = 0))
            data.putElement(node(12824915096L, LatLon(47.7585050, 9.1117108), timestamp = 0))
            data.putElement(node(12824915097L, LatLon(47.7598427, 9.1147919), timestamp = 0))
            data.putElement(node(12824915098L, LatLon(47.7595977, 9.1145378), timestamp = 0))
            data.putElement(node(12824915099L, LatLon(47.7594499, 9.1142642), timestamp = 0))
            data.putElement(node(12824915100L, LatLon(47.7593092, 9.1139317), timestamp = 0))
            data.putElement(node(12824936001L, LatLon(47.7591397, 9.1136527), timestamp = 0))
            data.putElement(node(12824936002L, LatLon(47.7590460, 9.1136098), timestamp = 0))
            data.putElement(node(12824936003L, LatLon(47.7588404, 9.1135776), timestamp = 0))
            data.putElement(node(12824936004L, LatLon(47.7586457, 9.1135883), timestamp = 0))
            data.putElement(node(12824936005L, LatLon(47.7585735, 9.1135079), timestamp = 0))
            data.putElement(node(12824936006L, LatLon(47.7584509, 9.1130680), timestamp = 0))
            data.putElement(node(12824936007L, LatLon(47.7584365, 9.1123170), timestamp = 0))
            data.putElement(way(1385223359L, listOf(12824915089L, 12824915076L, 12824915090L, 12824915091L, 12824915092L, 12824915093L, 12824915094L, 12824915095L, 12824915096L, 12824936007L, 12824915084L, 12824936006L, 12824936005L, 12824936004L, 12824936003L, 12824936002L, 12824936001L, 12824915100L, 12824915099L, 12824915098L, 12824915097L), mapOf("waterway" to "stream"), timestamp = 0))
        }
        1385223360L -> {
            data.putElement(node(12824936008L, LatLon(47.7597096, 9.1153208), timestamp = 0))
            data.putElement(node(12824936009L, LatLon(47.7595238, 9.1151869), timestamp = 0))
            data.putElement(node(12824936010L, LatLon(47.7594228, 9.1151628), timestamp = 0))
            data.putElement(node(12824936011L, LatLon(47.7593363, 9.1151413), timestamp = 0))
            data.putElement(node(12824936012L, LatLon(47.7592353, 9.1150662), timestamp = 0))
            data.putElement(node(12824936013L, LatLon(47.7591127, 9.1150072), timestamp = 0))
            data.putElement(node(12824936014L, LatLon(47.7589468, 9.1148007), timestamp = 0))
            data.putElement(node(12824936015L, LatLon(47.7587448, 9.1147363), timestamp = 0))
            data.putElement(node(12824936016L, LatLon(47.7585790, 9.1146505), timestamp = 0))
            data.putElement(node(12824936017L, LatLon(47.7583662, 9.1146129), timestamp = 0))
            data.putElement(node(12824936018L, LatLon(47.7582544, 9.1146076), timestamp = 0))
            data.putElement(node(12824936019L, LatLon(47.7581859, 9.1146854), timestamp = 0))
            data.putElement(node(12824936020L, LatLon(47.7581083, 9.1147283), timestamp = 0))
            data.putElement(node(12824936021L, LatLon(47.7580488, 9.1146988), timestamp = 0))
            data.putElement(node(12824936022L, LatLon(47.7580056, 9.1145807), timestamp = 0))
            data.putElement(node(12824936023L, LatLon(47.7579064, 9.1144413), timestamp = 0))
            data.putElement(node(12824936024L, LatLon(47.7577549, 9.1143152), timestamp = 0))
            data.putElement(node(12824936025L, LatLon(47.7575656, 9.1140926), timestamp = 0))
            data.putElement(node(12824936026L, LatLon(47.7569760, 9.1135347), timestamp = 0))
            data.putElement(node(12824936027L, LatLon(47.7567740, 9.1134274), timestamp = 0))
            data.putElement(node(12824936028L, LatLon(47.7563629, 9.1134381), timestamp = 0))
            data.putElement(node(12824936029L, LatLon(47.7559373, 9.1136312), timestamp = 0))
            data.putElement(node(12824936030L, LatLon(47.7553314, 9.1141570), timestamp = 0))
            data.putElement(way(1385223360L, listOf(12824936030L, 12824936029L, 12824936028L, 12824936027L, 12824936026L, 12824936025L, 12824936024L, 12824936023L, 12824936022L, 12824936021L, 12824936020L, 12824936019L, 12824936018L, 12824936017L, 12824936016L, 12824936015L, 12824936014L, 12824936013L, 12824936012L, 12824936011L, 12824936010L, 12824936009L, 12824936008L), mapOf("waterway" to "stream"), timestamp = 0))
        }
    }
    return data
}
