package de.westnordost.streetcomplete.screens.main

import android.app.Application
import android.content.Context
import de.westnordost.streetcomplete.androidModule
import de.westnordost.streetcomplete.commonModule
import de.westnordost.osmfeatures.FeatureDictionary
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MainBottomSheetViewModelKoinTest {
    @Test
    fun `production Koin resolves MainBottomSheetViewModel with FeatureDictionaryLazy`() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        stopKoin()
        startKoin { androidContext(context); modules(androidModule, commonModule) }
        try {
            val koin = org.koin.mp.KoinPlatformTools.defaultContext().get()
            assertNotNull(koin.get<Lazy<FeatureDictionary>>(named("FeatureDictionaryLazy")))
            assertNotNull(koin.get<MainBottomSheetViewModel>())
        } finally {
            stopKoin()
        }
    }
}