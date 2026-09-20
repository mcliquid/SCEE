package de.westnordost.streetcomplete.ui.common.feature

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import de.westnordost.osmfeatures.Feature
import de.westnordost.osmfeatures.FeatureDictionary
import de.westnordost.osmfeatures.GeometryType
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.ui.common.dialogs.AlertDialogLayout
import org.koin.compose.koinInject

/** A search field and a list of results for features below, in a dialog */
@Composable
fun FeatureSearchDialog(
    onDismissRequest: () -> Unit,
    onSelectedFeature: (Feature) -> Unit,
    featureDictionary: FeatureDictionary,
    modifier: Modifier = Modifier,
    geometryType: GeometryType? = null,
    countryCode: String? = null,
    officialLanguages: List<String> = emptyList(),
    filterFn: (Feature) -> Boolean = { true },
    codesOfDefaultFeatures: List<String> = emptyList(),
) {
    val prefs: Preferences = koinInject()
    val searchMoreLanguages = remember { prefs.getBoolean(Prefs.SEARCH_MORE_LANGUAGES, false) }
    Dialog(onDismissRequest = onDismissRequest) {
        AlertDialogLayout(
            modifier = modifier,
            content = {
                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.high) {
                    FeatureSearch(
                        onSelectedFeature = {
                            onDismissRequest()
                            onSelectedFeature(it)
                        },
                        featureDictionary = featureDictionary,
                        modifier = Modifier.fillMaxHeight(),
                        geometryType = geometryType,
                        countryCode = countryCode,
                        officialLanguages = officialLanguages,
                        searchMoreLanguages = searchMoreLanguages,
                        filterFn = filterFn,
                        codesOfDefaultFeatures = codesOfDefaultFeatures,
                    )
                }
            },
        )
    }
}
