package de.westnordost.streetcomplete.data.preferences

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Safety net: every constant in `Prefs` needs a functional consumer, i.e. a reference outside of
 * `Prefs.kt`, the settings screens and tests. A setting that is only shown in the settings UI does
 * nothing. This happened when the legacy map and `MainActivity` were removed upstream.
 *
 * Limits: a reference does not prove that the code is reachable (a consumer without a caller
 * still counts), so this does not replace behaviour tests for the individual features.
 *
 * - A constant used through a delegate in [Preferences] (`var x by prefs.boolean(Prefs.X)`) counts
 *   only if that property is used somewhere outside of [Preferences] and the settings screens.
 * - Comments do not count. Tests are not scanned.
 * - Constants whose key is built dynamically are still referenced by name, so they need no entry.
 *
 * Unconsumed constants must be listed in [KNOWN_UNCONSUMED] with the reason. The test also fails
 * for entries that have a consumer again, so the list can only shrink.
 */
class PrefsConsumerGuardTest {

    /** Constant name to reason. Empty: every setting has a consumer. */
    private val KNOWN_UNCONSUMED = emptyMap<String, String>()

    @Test fun `every Prefs constant has a functional consumer or a documented exception`() {
        val consumed = consumedConstants()
        val all = constants().keys

        val unexplained = (all - consumed - KNOWN_UNCONSUMED.keys).sorted()
        val obsolete = (KNOWN_UNCONSUMED.keys intersect consumed).sorted()
        val unknown = (KNOWN_UNCONSUMED.keys - all).sorted()

        if (unexplained.isNotEmpty()) fail(
            "Prefs without functional consumer (outside settings screens): $unexplained. " +
            "Restore the consumer, or add them to KNOWN_UNCONSUMED with a reason."
        )
        assertTrue(obsolete.isEmpty(), "Remove from KNOWN_UNCONSUMED, they have a consumer again: $obsolete")
        assertTrue(unknown.isEmpty(), "KNOWN_UNCONSUMED lists constants that do not exist: $unknown")
    }

    @Test fun `scanner finds well known consumers`() {
        val consumed = consumedConstants()
        assertTrue("EXPERT_MODE" in consumed) // via delegate property expertMode
        assertTrue("SHOW_NEARBY_QUESTS" in consumed) // direct
        assertTrue("QUEST_MONITOR" in consumed) // androidApp
    }

    private fun constants(): Map<String, String> =
        Regex("""const val (\w+)\s*=\s*"([^"]+)"""")
            .findAll(prefsFile.readText()).associate { it.groupValues[1] to it.groupValues[2] }

    private fun consumedConstants(): Set<String> {
        val files = sourceFiles().map { it to stripComments(it.readText()) }
        val delegates = Regex("""va[lr]\s+(\w+)\s*:[^=\n]*by\s+prefs\.\w+\(\s*Prefs\.(\w+)""")
            .findAll(files.first { it.first.name == "Preferences.kt" }.second)
            .associate { it.groupValues[2] to it.groupValues[1] }

        val consumed = HashSet<String>()
        for (name in constants().keys) {
            val ref = Regex("""\b$name\b""")
            val property = delegates[name]
            val users = files.filter { (file, _) -> !file.isExcluded() }
            val direct = users.any { (file, text) -> file.name != "Preferences.kt" && ref.containsMatchIn(text) }
            val viaProperty = property != null &&
                users.any { (file, text) -> file.name != "Preferences.kt" && Regex("""\b$property\b""").containsMatchIn(text) }
            val onlyInPreferences = users.any { (file, text) -> file.name == "Preferences.kt" && ref.containsMatchIn(text) }
            if (direct || viaProperty || (onlyInPreferences && property == null)) consumed += name
        }
        return consumed
    }

    private fun File.isExcluded(): Boolean {
        val path = invariantSeparatorsPath
        return name == "Prefs.kt" || "/screens/settings/" in path
    }

    private fun stripComments(text: String) =
        text.replace(Regex("""/\*[\s\S]*?\*/"""), "").replace(Regex("""//[^\n]*"""), "")

    private val appDir: File by lazy {
        // Gradle runs tests in the project directory of :app; also support the repository root
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .flatMap { sequenceOf(it, File(it, "app")) }
            .firstOrNull { File(it, "src/commonMain/kotlin/de/westnordost/streetcomplete/Prefs.kt").exists() }
            ?: error("Cannot find the app source directory from ${System.getProperty("user.dir")}")
    }

    private val prefsFile get() = File(appDir, "src/commonMain/kotlin/de/westnordost/streetcomplete/Prefs.kt")

    private fun sourceFiles(): List<File> {
        val roots = listOf(
            "commonMain", "androidMain", "desktopMain", "iosMain",
            "jvmAndroidMain", "nonAndroidMain", "mobileMain", "nativeMain",
        ).map { File(appDir, "src/$it") } +
            File(appDir.parentFile, "androidApp/src") + File(appDir.parentFile, "desktopApp/src")
        return roots.filter { it.exists() }
            .flatMap { it.walkTopDown().filter { f -> f.isFile && f.extension == "kt" }.toList() }
    }
}
