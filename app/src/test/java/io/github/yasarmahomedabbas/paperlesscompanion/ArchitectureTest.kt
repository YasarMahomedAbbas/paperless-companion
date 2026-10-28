package io.github.yasarmahomedabbas.paperlesscompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private const val P = "io.github.yasarmahomedabbas.paperlesscompanion"
private val IMPORT = Regex("""^\s*import\s+([\w.]+)""")

private val DOMAIN_FORBIDDEN = listOf("$P.data.", "$P.ui.", "android.", "androidx.", "okhttp3.", "kotlinx.serialization.")

/**
 * The layer rules from CLAUDE.md § Layers, for one source file. [relativePath] is relative to the
 * `paperlesscompanion` package directory, e.g. `ui/home/HomeScreen.kt`. Returns one message per offending line.
 */
internal fun layerViolations(relativePath: String, source: String): List<String> {
    val inData = relativePath.startsWith("data/")
    val mayNameData = inData || relativePath == "AppContainer.kt"
    val forbidden = buildList {
        if (relativePath.startsWith("domain/")) addAll(DOMAIN_FORBIDDEN.map { it to "domain is plain Kotlin in the app's own words" })
        if (relativePath.startsWith("ui/")) add("$P.data." to "ui sees only domain")
        if (!mayNameData) add("$P.data." to "only data/ and AppContainer name a data class")
    }
    val violations = mutableListOf<String>()
    source.lines().forEachIndexed { index, line ->
        val at = "$relativePath:${index + 1}"
        val imported = IMPORT.find(line)?.groupValues?.get(1)
        if (imported != null) {
            forbidden.firstOrNull { (prefix, _) -> imported.startsWith(prefix) }
                ?.let { (_, rule) -> violations += "$at imports $imported ($rule)" }
        } else if (!mayNameData && !line.trimStart().startsWith("package ") && line.contains("$P.data.")) {
            val named = Regex("""${Regex.escape("$P.data.")}[\w.]*""").find(line)!!.value
            violations += "$at imports $named inline (only data/ and AppContainer name a data class)"
        }
    }
    return violations
}

/** Fails the build when an import crosses the layers in CLAUDE.md § Layers: move the code, don't loosen the rule. */
class ArchitectureTest {

    private fun caught(path: String, import: String) = layerViolations(path, "package x\n\nimport $import\n")

    @Test
    fun `ui importing data is caught`() {
        val violations = caught("ui/home/HomeScreen.kt", "$P.data.UriDocuments")
        assertEquals(1, violations.size)
        assertTrue(violations.single().startsWith("ui/home/HomeScreen.kt:3 imports $P.data.UriDocuments"))
    }

    @Test
    fun `domain importing data, ui, android, androidx, okhttp or serialization is caught`() {
        for (prefix in DOMAIN_FORBIDDEN) {
            assertTrue(prefix, caught("domain/usecase/LoginUseCase.kt", "${prefix}Thing").isNotEmpty())
        }
    }

    @Test
    fun `outside data and AppContainer, importing data is caught`() {
        assertTrue(caught("MainActivity.kt", "$P.data.UriDocuments").isNotEmpty())
    }

    @Test
    fun `a fully qualified data reference is caught`() {
        val source = "package $P.ui.home\n\nval docs = $P.data.UriDocuments(resolver)\n"
        assertTrue(layerViolations("ui/home/HomeScreen.kt", source).isNotEmpty())
        assertTrue(layerViolations("MainActivity.kt", source).isNotEmpty())
    }

    @Test
    fun `allowed imports pass`() {
        val allowed = listOf(
            "data/PaperlessApi.kt" to "okhttp3.OkHttpClient",
            "data/PaperlessApi.kt" to "kotlinx.serialization.json.Json",
            "data/PaperlessApi.kt" to "$P.domain.model.ServerConfig",
            "AppContainer.kt" to "$P.data.PaperlessApi",
            "ui/home/HomeScreen.kt" to "android.net.Uri",
            "ui/home/HomeScreen.kt" to "androidx.compose.runtime.Composable",
            "ui/home/HomeScreen.kt" to "$P.domain.repository.DocumentFiles",
            "domain/model/Models.kt" to "java.io.InputStream",
            "domain/usecase/UploadDocumentUseCase.kt" to "kotlinx.coroutines.flow.Flow",
        )
        for ((path, import) in allowed) {
            assertEquals("$path importing $import", emptyList<String>(), caught(path, import))
        }
        assertEquals(emptyList<String>(), layerViolations("data/UriDocuments.kt", "package $P.data\n"))
    }

    @Test
    fun `the app's sources keep to the layers`() {
        // Gradle runs unit tests from the module directory.
        val root = File("src/main/java/io/github/yasarmahomedabbas/paperlesscompanion")
        assertTrue("source root not found: ${root.absolutePath}", root.isDirectory)

        val files = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .associate { it.relativeTo(root).invariantSeparatorsPath to it.readText() }
        for (layer in listOf("ui/", "domain/", "data/")) {
            assertTrue("no sources found under $layer", files.keys.any { it.startsWith(layer) })
        }

        val violations = files.flatMap { (path, source) -> layerViolations(path, source) }
        assertTrue("Imports cross the layers:\n" + violations.joinToString("\n"), violations.isEmpty())
    }
}
