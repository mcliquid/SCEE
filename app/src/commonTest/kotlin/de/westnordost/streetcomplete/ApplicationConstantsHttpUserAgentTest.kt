package de.westnordost.streetcomplete

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ApplicationConstantsHttpUserAgentTest {

    private lateinit var previousName: String

    @BeforeTest fun saveName() {
        previousName = ApplicationConstants.NAME
    }

    @AfterTest fun restoreName() {
        ApplicationConstants.NAME = previousName
    }

    @Test fun `HTTP user agent stays StreetComplete_ee when app name is SCEE-mc`() {
        ApplicationConstants.NAME = "SCEE-mc"

        assertEquals("SCEE-mc", ApplicationConstants.NAME)
        assertTrue(ApplicationConstants.USER_AGENT.startsWith("SCEE-mc "))
        assertEquals("StreetComplete_ee", ApplicationConstants.HTTP_USER_AGENT_NAME)
        assertTrue(ApplicationConstants.HTTP_USER_AGENT.startsWith("StreetComplete_ee "))
        assertEquals(
            "${ApplicationConstants.HTTP_USER_AGENT_NAME} ${BuildConfig.VERSION_NAME}",
            ApplicationConstants.HTTP_USER_AGENT
        )
        assertNotEquals(ApplicationConstants.USER_AGENT, ApplicationConstants.HTTP_USER_AGENT)
    }

    @Test fun `changeset user agent follows NAME independently of HTTP user agent`() {
        ApplicationConstants.NAME = "SCEE-mc"
        assertEquals("SCEE-mc ${BuildConfig.VERSION_NAME}", ApplicationConstants.USER_AGENT)

        ApplicationConstants.NAME = "StreetComplete_ee"
        assertEquals(ApplicationConstants.HTTP_USER_AGENT, ApplicationConstants.USER_AGENT)
    }
}
