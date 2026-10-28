package io.github.yasarmahomedabbas.paperlesscompanion.domain

import io.github.yasarmahomedabbas.paperlesscompanion.CONFIG
import io.github.yasarmahomedabbas.paperlesscompanion.FakeLibrary
import io.github.yasarmahomedabbas.paperlesscompanion.FakeSettings
import io.github.yasarmahomedabbas.paperlesscompanion.doc
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentQuery
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSort
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Labels
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.BrowseDocumentsUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.GetDocumentUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.LoadLabelsUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DocumentUseCasesTest {
    private val library = FakeLibrary()
    private val signedIn = FakeSettings(CONFIG)
    private val signedOut = FakeSettings()

    private suspend fun expectError(kind: Kind, block: suspend () -> Unit): ServerException {
        try {
            block()
        } catch (e: ServerException) {
            assertEquals(kind, e.kind)
            return e
        }
        fail("expected ServerException($kind)")
        throw AssertionError()
    }

    private suspend fun expectSignedOut(block: suspend () -> Unit) {
        val e = expectError(Kind.UNAUTHORIZED, block)
        assertEquals("Not signed in", e.message)
        assertTrue("signed out, nothing is asked of the server", library.calls.isEmpty())
    }

    private suspend fun expectPassedThrough(block: suspend () -> Unit) {
        val failure = ServerException(Kind.NOT_FOUND, "Paperless returned HTTP 404: gone")
        library.failure = failure
        try {
            block()
            fail("expected the library's error")
        } catch (e: ServerException) {
            assertSame(failure, e)
        }
    }

    @Test
    fun `browse passes the signed-in server and the query`() = runTest {
        val query = DocumentQuery(text = "bill", sort = DocumentSort.TITLE)

        val page = BrowseDocumentsUseCase(library, signedIn)(query)

        assertSame(library.page, page)
        assertEquals(listOf("documents $query"), library.calls)
        assertEquals(listOf(CONFIG), library.configs)
    }

    @Test
    fun `browse signed out is not signed in`() = runTest {
        expectSignedOut { BrowseDocumentsUseCase(library, signedOut)(DocumentQuery()) }
    }

    @Test
    fun `browse passes a server error through`() = runTest {
        expectPassedThrough { BrowseDocumentsUseCase(library, signedIn)(DocumentQuery()) }
    }

    @Test
    fun `get loads one document`() = runTest {
        library.document = doc(9)

        assertEquals(doc(9), GetDocumentUseCase(library, signedIn)(9))
        assertEquals(listOf("document 9"), library.calls)
    }

    @Test
    fun `get signed out is not signed in`() = runTest {
        expectSignedOut { GetDocumentUseCase(library, signedOut)(9) }
    }

    @Test
    fun `get passes a server error through`() = runTest {
        expectPassedThrough { GetDocumentUseCase(library, signedIn)(9) }
    }

    @Test
    fun `labels loads all four lists`() = runTest {
        val labels = LoadLabelsUseCase(library, signedIn)()

        assertEquals(Labels(library.tags, library.correspondents, library.documentTypes, library.storagePaths), labels)
        assertEquals(setOf("tags", "correspondents", "documentTypes", "storagePaths"), library.calls.toSet())
        assertTrue(library.configs.all { it == CONFIG })
    }

    @Test
    fun `labels signed out is not signed in`() = runTest {
        expectSignedOut { LoadLabelsUseCase(library, signedOut)() }
    }

    @Test
    fun `labels passes a server error through`() = runTest {
        expectPassedThrough { LoadLabelsUseCase(library, signedIn)() }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a query starts at page 1`() {
        DocumentQuery(page = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a query asks for at most 100 documents`() {
        DocumentQuery(pageSize = 101)
    }
}
