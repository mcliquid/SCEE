package de.westnordost.streetcomplete.quests.tree

import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.tree_genus_acer
import de.westnordost.streetcomplete.resources.tree_genus_acer_aliases
import de.westnordost.streetcomplete.resources.tree_genus_quercus
import de.westnordost.streetcomplete.resources.tree_genus_quercus_aliases
import de.westnordost.streetcomplete.resources.tree_genus_fagus
import de.westnordost.streetcomplete.resources.tree_genus_fagus_aliases
import de.westnordost.streetcomplete.resources.tree_genus_carpinus
import de.westnordost.streetcomplete.resources.tree_genus_carpinus_aliases
import de.westnordost.streetcomplete.resources.tree_genus_betula
import de.westnordost.streetcomplete.resources.tree_genus_betula_aliases
import de.westnordost.streetcomplete.resources.tree_genus_tilia
import de.westnordost.streetcomplete.resources.tree_genus_tilia_aliases
import de.westnordost.streetcomplete.resources.tree_genus_fraxinus
import de.westnordost.streetcomplete.resources.tree_genus_fraxinus_aliases
import de.westnordost.streetcomplete.resources.tree_genus_alnus
import de.westnordost.streetcomplete.resources.tree_genus_alnus_aliases
import de.westnordost.streetcomplete.resources.tree_genus_ulmus
import de.westnordost.streetcomplete.resources.tree_genus_ulmus_aliases
import de.westnordost.streetcomplete.resources.tree_genus_salix
import de.westnordost.streetcomplete.resources.tree_genus_salix_aliases
import de.westnordost.streetcomplete.resources.tree_genus_populus
import de.westnordost.streetcomplete.resources.tree_genus_populus_aliases
import de.westnordost.streetcomplete.resources.tree_genus_aesculus
import de.westnordost.streetcomplete.resources.tree_genus_aesculus_aliases
import de.westnordost.streetcomplete.resources.tree_genus_castanea
import de.westnordost.streetcomplete.resources.tree_genus_castanea_aliases
import de.westnordost.streetcomplete.resources.tree_genus_platanus
import de.westnordost.streetcomplete.resources.tree_genus_platanus_aliases
import de.westnordost.streetcomplete.resources.tree_genus_picea
import de.westnordost.streetcomplete.resources.tree_genus_picea_aliases
import de.westnordost.streetcomplete.resources.tree_genus_abies
import de.westnordost.streetcomplete.resources.tree_genus_abies_aliases
import de.westnordost.streetcomplete.resources.tree_genus_pinus
import de.westnordost.streetcomplete.resources.tree_genus_pinus_aliases
import de.westnordost.streetcomplete.resources.tree_genus_larix
import de.westnordost.streetcomplete.resources.tree_genus_larix_aliases
import de.westnordost.streetcomplete.resources.tree_genus_taxus
import de.westnordost.streetcomplete.resources.tree_genus_taxus_aliases
import de.westnordost.streetcomplete.resources.tree_genus_pseudotsuga
import de.westnordost.streetcomplete.resources.tree_genus_pseudotsuga_aliases
import de.westnordost.streetcomplete.resources.tree_genus_malus
import de.westnordost.streetcomplete.resources.tree_genus_malus_aliases
import de.westnordost.streetcomplete.resources.tree_genus_pyrus
import de.westnordost.streetcomplete.resources.tree_genus_pyrus_aliases
import de.westnordost.streetcomplete.resources.tree_genus_prunus
import de.westnordost.streetcomplete.resources.tree_genus_prunus_aliases
import de.westnordost.streetcomplete.resources.tree_genus_juglans
import de.westnordost.streetcomplete.resources.tree_genus_juglans_aliases
import de.westnordost.streetcomplete.resources.tree_genus_robinia
import de.westnordost.streetcomplete.resources.tree_genus_robinia_aliases
import de.westnordost.streetcomplete.resources.tree_genus_ginkgo
import de.westnordost.streetcomplete.resources.tree_genus_ginkgo_aliases
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern

/** Curated genus answers; photograph provenance is retained with the asset tests. */
@kotlinx.serialization.Serializable
enum class TreeGenus(
    val osmValue: String,
    val title: StringResource,
    val aliases: StringResource,
    val image: DrawableResource,
) {
    ACER(
        "Acer", Res.string.tree_genus_acer,
        Res.string.tree_genus_acer_aliases,
        Res.drawable.tree_genus_acer,
    ),
    QUERCUS(
        "Quercus", Res.string.tree_genus_quercus,
        Res.string.tree_genus_quercus_aliases,
        Res.drawable.tree_genus_quercus,
    ),
    FAGUS(
        "Fagus", Res.string.tree_genus_fagus,
        Res.string.tree_genus_fagus_aliases,
        Res.drawable.tree_genus_fagus,
    ),
    CARPINUS(
        "Carpinus", Res.string.tree_genus_carpinus,
        Res.string.tree_genus_carpinus_aliases,
        Res.drawable.tree_genus_carpinus,
    ),
    BETULA(
        "Betula", Res.string.tree_genus_betula,
        Res.string.tree_genus_betula_aliases,
        Res.drawable.tree_genus_betula,
    ),
    TILIA(
        "Tilia", Res.string.tree_genus_tilia,
        Res.string.tree_genus_tilia_aliases,
        Res.drawable.tree_genus_tilia,
    ),
    FRAXINUS(
        "Fraxinus", Res.string.tree_genus_fraxinus,
        Res.string.tree_genus_fraxinus_aliases,
        Res.drawable.tree_genus_fraxinus,
    ),
    ALNUS(
        "Alnus", Res.string.tree_genus_alnus,
        Res.string.tree_genus_alnus_aliases,
        Res.drawable.tree_genus_alnus,
    ),
    ULMUS(
        "Ulmus", Res.string.tree_genus_ulmus,
        Res.string.tree_genus_ulmus_aliases,
        Res.drawable.tree_genus_ulmus,
    ),
    SALIX(
        "Salix", Res.string.tree_genus_salix,
        Res.string.tree_genus_salix_aliases,
        Res.drawable.tree_genus_salix,
    ),
    POPULUS(
        "Populus", Res.string.tree_genus_populus,
        Res.string.tree_genus_populus_aliases,
        Res.drawable.tree_genus_populus,
    ),
    AESCULUS(
        "Aesculus", Res.string.tree_genus_aesculus,
        Res.string.tree_genus_aesculus_aliases,
        Res.drawable.tree_genus_aesculus,
    ),
    CASTANEA(
        "Castanea", Res.string.tree_genus_castanea,
        Res.string.tree_genus_castanea_aliases,
        Res.drawable.tree_genus_castanea,
    ),
    PLATANUS(
        "Platanus", Res.string.tree_genus_platanus,
        Res.string.tree_genus_platanus_aliases,
        Res.drawable.tree_genus_platanus,
    ),
    PICEA(
        "Picea", Res.string.tree_genus_picea,
        Res.string.tree_genus_picea_aliases,
        Res.drawable.tree_genus_picea,
    ),
    ABIES(
        "Abies", Res.string.tree_genus_abies,
        Res.string.tree_genus_abies_aliases,
        Res.drawable.tree_genus_abies,
    ),
    PINUS(
        "Pinus", Res.string.tree_genus_pinus,
        Res.string.tree_genus_pinus_aliases,
        Res.drawable.tree_genus_pinus,
    ),
    LARIX(
        "Larix", Res.string.tree_genus_larix,
        Res.string.tree_genus_larix_aliases,
        Res.drawable.tree_genus_larix,
    ),
    TAXUS(
        "Taxus", Res.string.tree_genus_taxus,
        Res.string.tree_genus_taxus_aliases,
        Res.drawable.tree_genus_taxus,
    ),
    PSEUDOTSUGA(
        "Pseudotsuga", Res.string.tree_genus_pseudotsuga,
        Res.string.tree_genus_pseudotsuga_aliases,
        Res.drawable.tree_genus_pseudotsuga,
    ),
    MALUS(
        "Malus", Res.string.tree_genus_malus,
        Res.string.tree_genus_malus_aliases,
        Res.drawable.tree_genus_malus,
    ),
    PYRUS(
        "Pyrus", Res.string.tree_genus_pyrus,
        Res.string.tree_genus_pyrus_aliases,
        Res.drawable.tree_genus_pyrus,
    ),
    PRUNUS(
        "Prunus", Res.string.tree_genus_prunus,
        Res.string.tree_genus_prunus_aliases,
        Res.drawable.tree_genus_prunus,
    ),
    JUGLANS(
        "Juglans", Res.string.tree_genus_juglans,
        Res.string.tree_genus_juglans_aliases,
        Res.drawable.tree_genus_juglans,
    ),
    ROBINIA(
        "Robinia", Res.string.tree_genus_robinia,
        Res.string.tree_genus_robinia_aliases,
        Res.drawable.tree_genus_robinia,
    ),
    GINKGO(
        "Ginkgo", Res.string.tree_genus_ginkgo,
        Res.string.tree_genus_ginkgo_aliases,
        Res.drawable.tree_genus_ginkgo,
    );

    fun toAnswer(localizedName: String) = Tree(osmValue, false, localizedName)

    fun matches(search: String, localizedName: String, localizedAliases: String): Boolean {
        val query = canonicalize(search.trim())
        return listOf(osmValue, localizedName, localizedAliases).any { canonicalize(it).contains(query) }
    }
}

internal fun hasVisualTreeCatalog(countryCode: String?) = countryCode in setOf("DE", "AT", "CH")

private val FIND_DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")

internal fun canonicalize(str: String): String =
    FIND_DIACRITICS.matcher(Normalizer.normalize(str, Normalizer.Form.NFD)).replaceAll("").lowercase(Locale.US)
