package de.westnordost.streetcomplete.overlays.restriction

import de.westnordost.streetcomplete.ApplicationConstants.EE_QUEST_OFFSET
import de.westnordost.streetcomplete.data.meta.IncompleteCountryInfo
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.meta.WeightMeasurementUnit
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.osm.mapdata.RelationMember
import de.westnordost.streetcomplete.data.overlays.OverlayColor
import de.westnordost.streetcomplete.data.overlays.OverlayStyle
import de.westnordost.streetcomplete.overlays.overlaysRegistry
import de.westnordost.streetcomplete.quests.max_weight.Weight
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.rel
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RestrictionOverlayRegressionTest {
    private val metric = WeightMeasurementUnit.METRIC_TON

    @Test fun `overlay is selectable with its historical ordinal and node creation enabled`() {
        val registry = overlaysRegistry({ CountryInfo(null, listOf(IncompleteCountryInfo())) }, { null }, { null }, inMemoryPrefs())
        val overlay = assertIs<RestrictionOverlay>(registry.getByName("RestrictionOverlay"))
        assertEquals(EE_QUEST_OFFSET + 1, registry.getOrdinalOf(overlay))
        assertTrue(overlay in registry)
        assertTrue(overlay.isCreateNodeEnabled)
    }

    @Test fun `roads without restrictions remain selectable while areas and paths are excluded`() {
        val road = way(1, tags = mapOf("highway" to "residential"))
        val area = way(2, nodes = listOf(1, 2, 3, 1), tags = mapOf("highway" to "residential", "area" to "yes"))
        val path = way(3, tags = mapOf("highway" to "path"))
        val styles = RestrictionOverlay().getStyledElements(TestMapDataWithGeometry(listOf(road, area, path))).toList()
        assertEquals(listOf(road), styles.map { it.first })
        assertEquals(OverlayColor.Invisible, assertIs<OverlayStyle.Polyline>(styles.single().second).stroke!!.color)
    }

    @Test fun `conditional weight is highlighted and parsed without becoming unconditional`() {
        val road = way(tags = mapOf("highway" to "secondary", "maxweight:conditional" to "7.5 @ (wet)"))
        val restriction = getWeightRestrictions(road).single()
        val style = RestrictionOverlay().getStyledElements(TestMapDataWithGeometry(listOf(road))).single().second
        assertEquals(OverlayColor.Teal, assertIs<OverlayStyle.Polyline>(style).stroke!!.color)
        assertEquals(Weight(7.5, metric), parseWeight(restriction.weight, listOf(metric)))
        val edited = withWeight(restriction, Weight(5.0, metric))
        assertEquals("5 @ (wet)", edited.way.tags["maxweight:conditional"])
        assertNull(edited.way.tags["maxweight"])
    }

    @Test fun `editing base weight preserves the exception and unrelated tags`() {
        val road = way(tags = mapOf("maxweight" to "7.5", "maxweight:conditional" to "none @ (delivery)", "name" to "Road"))
        val edited = withWeight(getWeightRestrictions(road).single(), Weight(8.0, metric))
        assertEquals("8", edited.way.tags["maxweight"])
        assertEquals("none @ (delivery)", edited.way.tags["maxweight:conditional"])
        assertEquals("Road", edited.way.tags["name"])
    }

    @Test fun `weight form rejects empty and unchanged states and accepts a new limit`() {
        val draft = WeightRestriction(way(), MaxWeightType.MAX_WEIGHT, "")
        assertFalse(isRestrictionComplete(null, null))
        assertFalse(isRestrictionComplete(draft, null))
        val filled = withWeight(draft, Weight(5.0, metric))
        assertTrue(isRestrictionComplete(filled, null))
        assertFalse(isRestrictionComplete(filled, filled))
        assertFalse(isRestrictionComplete(withWeight(filled, null), filled))
    }

    @Test fun `conditional-only tag edits can be submitted without a numeric input edit`() {
        val road = way(tags = mapOf("maxweight:conditional" to "5 @ (wet)"))
        val original = getWeightRestrictions(road).single()
        val changed = original.copy(way = road.copy(tags = mapOf("maxweight:conditional" to "5 @ (snow)")))
        assertTrue(isRestrictionComplete(changed, original))
        assertNull(changed.way.tags["maxweight"])
    }

    @Test fun `adding a conditional limit refreshes the displayed value and subsequent numeric edits`() {
        val original = getWeightRestrictions(way(tags = mapOf("maxweight" to "7.5"))).single()
        val changed = withWeightConditional(original, "5 @ (wet)")
        assertEquals(Weight(5.0, metric), parseWeightForDisplay(changed.weight, "DE", listOf(metric)))
        assertNull(changed.way.tags["maxweight"])
        assertTrue(isRestrictionComplete(changed, original))
        val edited = withWeight(changed, Weight(4.0, metric))
        assertEquals("4 @ (wet)", edited.way.tags["maxweight:conditional"])
        assertNull(edited.way.tags["maxweight"])
    }

    @Test fun `new conditional-only weight is complete and exceptions retain the base input`() {
        val draft = WeightRestriction(way(), MaxWeightType.MAX_WEIGHT, "")
        assertTrue(isRestrictionComplete(withWeightConditional(draft, "5 @ (wet)"), null))
        val original = getWeightRestrictions(way(tags = mapOf("maxweight" to "7.5"))).single()
        val exception = withWeightConditional(original, "none @ (delivery)")
        assertEquals("7.5", exception.weight)
        assertEquals("7.5", exception.way.tags["maxweight"])
        assertEquals("none @ (delivery)", exception.way.tags["maxweight:conditional"])
    }

    @Test fun `new turn selection displays its draft type rather than the previously selected relation`() {
        val old = rel(tags = mapOf("restriction" to "no_left_turn"))
        assertEquals("only_right_turn", turnRestrictionEditorType(old, "only_right_turn", true))
        assertEquals("no_left_turn", turnRestrictionEditorType(old, "only_right_turn", false))
        assertEquals("only_right_turn", turnRestrictionEditorType(null, "only_right_turn", true))
    }

    @Test fun `reentering the same conditional weight cannot submit an empty tag diff`() {
        val road = way(tags = mapOf("maxweight:conditional" to "5 @ (wet)"))
        val original = getWeightRestrictions(road).single()
        val reentered = withWeight(original, Weight(5.0, metric))
        assertFalse(hasRestrictionChanges(reentered, original))
        assertFalse(isRestrictionComplete(reentered, original))
        assertTrue(hasRestrictionChanges(withWeight(original, null), original))
    }

    @Test fun `removing weight conditional restores its base value`() {
        val road = way(tags = mapOf("maxweight:conditional" to "5 @ (wet)", "name" to "Road"))
        val result = assertIs<WeightRestriction>(removeConditional(getWeightRestrictions(road).single()))
        assertEquals(mapOf("maxweight" to "5", "name" to "Road"), result.way.tags)
    }

    @Test fun `unitless and explicit metric OSM values mean tonnes even in imperial countries`() {
        val units = listOf(WeightMeasurementUnit.SHORT_TON, WeightMeasurementUnit.POUND)
        assertEquals(Weight(5.0, metric), parseWeight("5", units))
        assertEquals(Weight(5.0, metric), parseWeight("5 t", units))
        assertEquals(Weight(5.0, WeightMeasurementUnit.SHORT_TON), parseWeight("5 st", units))
        assertEquals(Weight(1000.0, WeightMeasurementUnit.POUND), parseWeight("1000 lbs", units))
        assertNull(parseWeight("unknown", units))
    }

    @Test fun `imperial values are converted for metric signs without changing stored tags`() {
        val weight = parseWeightForDisplay("1000 lbs", "DE", listOf(metric))!!
        assertEquals(metric, weight.unit)
        assertEquals(0.45359237, weight.value, 0.00000001)
        assertEquals(Weight(1000.0, WeightMeasurementUnit.POUND), parseWeightForDisplay("1000 lbs", "US", listOf(WeightMeasurementUnit.POUND)))
    }

    @Test fun `node insertion filter permits bicycle roads and rejects unsuitable ways`() {
        for (highway in listOf("residential", "cycleway")) {
            assertTrue(restrictionNodeWaysFilter.matches(way(tags = mapOf("highway" to highway))))
        }
        assertTrue(restrictionNodeWaysFilter.matches(way(tags = mapOf("highway" to "path", "bicycle" to "designated"))))
        assertFalse(restrictionNodeWaysFilter.matches(way(tags = mapOf("highway" to "path"))))
        assertFalse(restrictionNodeWaysFilter.matches(way(tags = mapOf("highway" to "residential", "area" to "yes"))))
        assertFalse(restrictionNodeWaysFilter.matches(way(tags = mapOf("waterway" to "stream"))))
    }

    @Test fun `vehicle-only conditional survives type changes moving and removing condition`() {
        val relation = rel(tags = mapOf("type" to "restriction", "restriction:hgv:conditional" to "no_left_turn @ (Mo-Fr)"))
        val moved = withOnlyFor(withTurnRestrictionType(relation, "no_right_turn"), "bus")
        assertEquals("no_right_turn @ (Mo-Fr)", moved.tags["restriction:bus:conditional"])
        assertNull(moved.tags["restriction:hgv:conditional"])
        val plain = assertIs<TurnRestriction>(removeConditional(TurnRestriction(moved)))
        assertEquals("no_right_turn", plain.relation.tags["restriction:bus"])
        assertNull(plain.relation.tags["restriction:bus:conditional"])
    }

    @Test fun `new turn draft and unchanged or unsupported relation states`() {
        val draft = TurnRestriction(createTurnRestrictionRelation(way(1), way(2), node(3), "no_left_turn", true))
        assertTrue(isRestrictionComplete(draft, null))
        assertFalse(isRestrictionComplete(draft, draft))
        assertFalse(isRestrictionComplete(TurnRestriction(draft.relation.copy(tags = mapOf("type" to "restriction", "restriction" to "no_entry"))), draft))
    }

    @Test fun `turn relation role colors preserve from to and via distinctions`() {
        val roads = (1L..3L).map { way(it, tags = mapOf("highway" to "secondary")) }
        val relation = rel(members = listOf(
            RelationMember(ElementType.WAY, 1, "from"),
            RelationMember(ElementType.WAY, 2, "to"),
            RelationMember(ElementType.WAY, 3, "via"),
        ), tags = mapOf("type" to "restriction", "restriction" to "no_left_turn"))
        val colors = RestrictionOverlay().getStyledElements(TestMapDataWithGeometry(roads + relation))
            .associate { it.first.id to assertIs<OverlayStyle.Polyline>(it.second).stroke!!.color }
        assertEquals(OverlayColor.Orange, colors[1])
        assertTrue(colors[2] != colors[1])
        assertEquals(OverlayColor.Lime, colors[3])
    }
}
