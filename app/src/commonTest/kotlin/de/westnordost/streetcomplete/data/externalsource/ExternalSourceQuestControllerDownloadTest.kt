package de.westnordost.streetcomplete.data.externalsource

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.edits.ElementEdit
import de.westnordost.streetcomplete.data.osm.edits.ElementEditsController
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.quest.QuestTypeRegistry
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement
import de.westnordost.streetcomplete.data.visiblequests.VisibleEditTypeSource
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.quest_address_title
import de.westnordost.streetcomplete.resources.quest_bridge
import de.westnordost.streetcomplete.util.countryboundaries.AllCountries
import de.westnordost.streetcomplete.util.countryboundaries.CountryBoundaries
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class ExternalSourceQuestControllerDownloadTest {

    private val bbox = BoundingBox(0.0, 0.0, 0.01, 0.01)

    @Test fun `invisible external quest is not downloaded even when download enabled`() = runBlocking {
        val type = CountingExternalQuestType()
        type.downloadEnabled = true
        val controller = controller(type, visible = false)

        controller.download(bbox)

        assertEquals(0, type.downloadCalls)
    }

    @Test fun `visible external quest with download enabled is downloaded`() = runBlocking {
        val type = CountingExternalQuestType()
        type.downloadEnabled = true
        val controller = controller(type, visible = true)

        controller.download(bbox)

        assertEquals(1, type.downloadCalls)
    }

    @Test fun `visible external quest without download enabled is not downloaded`() = runBlocking {
        val type = CountingExternalQuestType()
        type.downloadEnabled = false
        val controller = controller(type, visible = true)

        controller.download(bbox)

        assertEquals(0, type.downloadCalls)
    }

    private fun controller(type: CountingExternalQuestType, visible: Boolean): ExternalSourceQuestController {
        val countryBoundaries: CountryBoundaries = mock {
            every { intersects(any<BoundingBox>(), any()) } returns true
        }
        val registry = QuestTypeRegistry({ listOf(0 to type) })
        val elementEditsController: ElementEditsController = mock {
            every { addListener(any()) } returns Unit
        }
        val externalSourceDao: ExternalSourceDao = mock()
        val visibleEditTypeSource: VisibleEditTypeSource = mock {
            every { isVisible(type) } returns visible
        }
        return ExternalSourceQuestController(
            lazy { countryBoundaries },
            registry,
            externalSourceDao,
            elementEditsController,
            visibleEditTypeSource,
        )
    }
}

private class CountingExternalQuestType : ExternalSourceQuestType {
    override val source = "counting"
    override val icon = Res.drawable.quest_bridge
    override val title = Res.string.quest_address_title
    override val wikiLink: String? = null
    override val changesetComment = "test"
    override val achievements = emptyList<EditTypeAchievement>()
    override val enabledInCountries = AllCountries

    override var downloadEnabled = false
    var downloadCalls = 0

    @Composable
    override fun Form(on: (de.westnordost.streetcomplete.data.osm.osmquests.ExternalAction) -> Unit, quest: ExternalSourceQuest, countryInfo: CountryInfo) {}

    override suspend fun download(bbox: BoundingBox): Collection<ExternalSourceQuest> {
        downloadCalls++
        return emptyList()
    }

    override suspend fun upload() {}
    override fun getQuests(bbox: BoundingBox) = emptyList<ExternalSourceQuest>()
    override fun get(id: String): ExternalSourceQuest? = null
    override fun onAddedEdit(edit: ElementEdit, id: String) {}
    override fun onDeletedEdit(edit: ElementEdit, id: String?) {}
    override suspend fun onSyncedEdit(edit: ElementEdit, id: String?) {}
    override fun onSyncEditFailed(edit: ElementEdit, id: String?) {}
    override suspend fun onUpload(edit: ElementEdit, id: String?) = true
    override fun deleteQuest(id: String) = false
    override fun deleteMetadataOlderThan(timestamp: Long) {}
}
