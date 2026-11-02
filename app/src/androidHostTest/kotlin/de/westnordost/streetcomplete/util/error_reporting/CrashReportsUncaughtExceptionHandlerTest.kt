package de.westnordost.streetcomplete.util.error_reporting

import android.app.Application
import android.content.Context
import de.westnordost.streetcomplete.data.logs.LogsController
import de.westnordost.streetcomplete.data.logs.LogsDao
import dev.mokkery.mock
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CrashReportsUncaughtExceptionHandlerTest {

    @Test fun `takeCrashReport returns saved stack trace once`() {
        val context = RuntimeEnvironment.getApplication()
        val logs = LogsController(LogsDao(mock()))
        val builder = ErrorReportBuilder(logs)
        val fileName = "crashreport-test.txt"
        val handler = CrashReportsUncaughtExceptionHandler(context, builder, fileName)
        context.deleteFile(fileName)
        context.openFileOutput(fileName, Context.MODE_PRIVATE).bufferedWriter().use {
            it.write("test crash report")
        }

        assertEquals("test crash report", handler.takeCrashReport())
        assertNull(handler.takeCrashReport())
        context.deleteFile(fileName)
    }
}
