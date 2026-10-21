package de.westnordost.streetcomplete.quests

import com.github.difflib.text.DiffRow.Tag
import com.github.difflib.text.DiffRowGenerator

internal actual fun questSettingsDiffHtml(original: String, revised: String): String {
    val generator = DiffRowGenerator.create()
        .showInlineDiffs(true)
        .mergeOriginalRevised(true)
        .inlineDiffByWord(true)
        .ignoreWhiteSpaces(true)
        .oldTag { opening -> if (opening) "<b><i><del>" else "</del></i></b>" }
        .newTag { opening -> if (opening) "<b><u><ins>" else "</ins></u></b>" }
        .build()
    val space = " "
    val oldLines = original.replace("|", "$space|$space").split("\n")
    val newLines = revised.replace("|", "$space|$space").split("\n")
    return generator.generateDiffRows(oldLines, newLines)
        .filter { it.tag != Tag.EQUAL }
        .joinToString("<br>") { it.oldLine }
}
