package de.westnordost.streetcomplete.data.osm.edits

import de.westnordost.streetcomplete.R

actual val tagEdit = object : ElementEditType {
    override val changesetComment = "Edit element"
    override val icon = R.drawable.ic_edit_tags
    override val title = R.string.quest_generic_answer_show_edit_tags
    override val wikiLink: String? = null
    override val name = "TagEdit"
}

actual val addNodeEdit = object : ElementEditType {
    override val icon: Int = R.drawable.ic_add_poi
    override val title: Int = R.string.create_poi
    override val wikiLink: String? = null
    override val changesetComment: String = "Add node"
    override val name: String = "AddNode"
}
