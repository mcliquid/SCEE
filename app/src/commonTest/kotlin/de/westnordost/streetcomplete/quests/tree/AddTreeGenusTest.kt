package de.westnordost.streetcomplete.quests.tree

import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.quest_tree_disabled_msg
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.rel
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddTreeGenusTest {
    private val quest = AddTreeGenus()

    @BeforeTest fun setup() { Prefs.preferences = inMemoryPrefs() }

    @Test fun `plain tree and non-taxonomic tags remain eligible`() {
        for (extra in listOf(emptyMap(), mapOf("wikidata" to "Q1"), mapOf("name" to "Old oak"),
            mapOf("leaf_type" to "broadleaved", "leaf_cycle" to "deciduous"))) {
            assertTrue(quest.isApplicableTo(node(tags = mapOf("natural" to "tree") + extra)))
        }
    }

    @Test fun `existing taxonomy including legacy botanical names excludes tree`() {
        val keys = listOf("genus", "species", "taxon", "genus:de", "species:de", "taxon:de",
            "genus:wikidata", "species:wikidata", "taxon:wikidata", "taxon:genus", "name:botanical")
        for (key in keys) {
            assertFalse(quest.isApplicableTo(node(tags = mapOf("natural" to "tree", key to "existing"))), key)
        }
    }

    @Test fun `only tree nodes are eligible`() {
        assertFalse(quest.isApplicableTo(node(tags = mapOf("natural" to "tree_stump"))))
        assertFalse(quest.isApplicableTo(way(tags = mapOf("natural" to "tree"))))
        assertFalse(quest.isApplicableTo(rel(tags = mapOf("natural" to "tree"))))
    }

    @Test fun `every visual answer adds only scientific genus and preserves other tags`() {
        val original = mapOf("natural" to "tree", "name" to "Test", "leaf_type" to "broadleaved")
        for (genus in TreeGenus.entries) {
            val tags = original.toMutableMap()
            quest.answerAppliedTo(genus.toAnswer("Translated title"), original).forEach { it.applyTo(tags) }
            assertEquals(original + ("genus" to genus.osmValue), tags)
        }
    }

    @Test fun `explicit species and stump keep their existing behavior`() {
        val original = mapOf("natural" to "tree", "leaf_cycle" to "deciduous")
        val species = original.toMutableMap()
        quest.answerAppliedTo(Tree("Quercus robur", true, "Eiche"), original).forEach { it.applyTo(species) }
        assertEquals(original + ("species" to "Quercus robur"), species)
        val stump = original.toMutableMap()
        quest.answerAppliedTo(NotTreeButStump, original).forEach { it.applyTo(stump) }
        assertEquals(original + ("natural" to "tree_stump"), stump)
    }

    @Test fun `catalog has the planned unique genera and provenance`() {
        val expected = setOf(
            "Acer", "Quercus", "Fagus", "Carpinus",
            "Betula", "Tilia", "Fraxinus", "Alnus",
            "Ulmus", "Salix", "Populus", "Aesculus",
            "Castanea", "Platanus", "Picea", "Abies",
            "Pinus", "Larix", "Taxus", "Pseudotsuga",
            "Malus", "Pyrus", "Prunus", "Juglans",
            "Robinia", "Ginkgo",
        )
        assertEquals(26, TreeGenus.entries.size)
        assertEquals(expected, TreeGenus.entries.map { it.osmValue }.toSet())
    }

    @Test fun `search matches translated names aliases accents case and scientific names`() {
        assertTrue(TreeGenus.PINUS.matches(" FÖHRE ", "Kiefer", "Föhre; Foehre"))
        assertTrue(TreeGenus.PINUS.matches("fohre", "Kiefer", "Föhre; Foehre"))
        assertTrue(TreeGenus.LARIX.matches("larche", "Lärche", "Larix"))
        assertTrue(TreeGenus.QUERCUS.matches("quERC", "Eiche", "Quercus"))
        assertTrue(TreeGenus.QUERCUS.matches("eiche", "Eiche", "Quercus"))
        assertFalse(TreeGenus.QUERCUS.matches("birke", "Eiche", "Quercus"))
        assertFalse(TreeGenus.PRUNUS.matches("Steinobst", "Kirsche, Pflaume und verwandte Prunus", "Aprikose; Mandel"))
    }

    @Test fun `ambiguous chestnut search keeps both genera and Prunus aliases share one answer`() {
        assertTrue(TreeGenus.AESCULUS.matches("Kastanie", "Rosskastanie", "Kastanie"))
        assertTrue(TreeGenus.CASTANEA.matches("Kastanie", "Edelkastanie", "Esskastanie; Marone; Kastanie"))
        for (name in listOf("Kirsche", "Pflaume", "Zwetschge", "Pfirsich", "Aprikose", "Mandel")) {
            assertTrue(TreeGenus.PRUNUS.matches(name, "Prunus", "Kirsche; Pflaume; Zwetschge; Pfirsich; Aprikose; Mandel"))
            assertEquals("Prunus", TreeGenus.PRUNUS.toAnswer(name).name)
        }
    }

    @Test fun `visual coverage is limited to the three planned countries`() {
        for (code in listOf("DE", "AT", "CH")) assertTrue(hasVisualTreeCatalog(code))
        for (code in listOf(null, "", "FR", "US")) assertFalse(hasVisualTreeCatalog(code))
        assertEquals(Res.string.quest_tree_disabled_msg, quest.defaultDisabledMessage)
    }
}
