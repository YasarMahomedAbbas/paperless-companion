package io.github.yasarmahomedabbas.paperlesscompanion.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The code-checkable lines of docs/design/checklist.md § Theme and tokens: one theme, and
 * colours, corners and text sizes only from ui/theme (ui/components may size its own parts).
 */
class DesignRulesTest {
    private val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()

    @Test
    fun `the sources are found`() {
        assertTrue("no Kotlin sources under ${File("src/main/java").absolutePath}", sources.size > 5)
    }

    @Test
    fun `there is one theme, with no dark branch and no dynamic colour`() {
        val banned = listOf("isSystemInDarkTheme", "dynamicDarkColorScheme", "dynamicLightColorScheme", "lightColorScheme(")
        val hits = sources.flatMap { file -> banned.filter { it in file.readText() }.map { "${file.name}: $it" } }
        assertTrue("found $hits", hits.isEmpty())
    }

    @Test
    fun `screens take colours, corners and type sizes from the theme`() {
        val banned = listOf(Regex("""Color\(0x"""), Regex("""RoundedCornerShape\(\d"""), Regex("""fontSize\s*="""), Regex("""\d\.sp\b|\d+\.sp\b"""))
        val hits = sources
            .filterNot { it.invariantSeparatorsPath.contains("/ui/theme/") || it.invariantSeparatorsPath.contains("/ui/components/") }
            .flatMap { file -> banned.filter { it.containsMatchIn(file.readText()) }.map { "${file.name}: ${it.pattern}" } }
        assertTrue("found $hits", hits.isEmpty())
    }
}
