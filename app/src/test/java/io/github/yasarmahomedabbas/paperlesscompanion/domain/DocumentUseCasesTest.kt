package io.github.yasarmahomedabbas.paperlesscompanion.domain

import io.github.yasarmahomedabbas.paperlesscompanion.CONFIG
import io.github.yasarmahomedabbas.paperlesscompanion.FakeExports
import io.github.yasarmahomedabbas.paperlesscompanion.FakeLibrary
import io.github.yasarmahomedabbas.paperlesscompanion.FakeSettings
import io.github.yasarmahomedabbas.paperlesscompanion.doc
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.BulkEdit
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Change
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentChanges
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentFileKind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentQuery
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSort
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Labels
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.BrowseDocumentsUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.BulkEditUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.DeleteDocumentUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.ExportDocumentUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.GetDocumentUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.LoadLabelsUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.NotesUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.OpenDocumentFileUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.TrashUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.UpdateDocumentUseCase
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

    @Test
    fun `open file fetches the asked-for kind`() = runTest {
        assertSame(library.file, OpenDocumentFileUseCase(library, signedIn)(4, DocumentFileKind.ORIGINAL))
        assertEquals(listOf("file 4 ORIGINAL"), library.calls)
        assertEquals(listOf(CONFIG), library.configs)
    }

    @Test
    fun `open file passes the progress through`() = runTest {
        library.progress = listOf(0L to 10L, 10L to 10L)
        val heard = mutableListOf<Pair<Long, Long?>>()

        OpenDocumentFileUseCase(library, signedIn)(4, DocumentFileKind.PREVIEW) { done, total -> heard += done to total }

        assertEquals(listOf(0L to 10L, 10L to 10L), heard)
    }

    @Test
    fun `export hands each call to its port`() = runTest {
        val exports = FakeExports(canSaveDirectly = false)
        val export = ExportDocumentUseCase(exports)
        val file = library.file

        assertEquals("content://test/bill.pdf", export.shareLocation(file))
        assertEquals(false, export.canSaveDirectly)
        exports.canSaveDirectly = true
        assertEquals(true, export.canSaveDirectly)
        exports.savedAs = "bill (1).pdf"
        assertEquals("bill (1).pdf", export.saveToDownloads(file))
        export.saveTo(file, "content://picked/1")

        assertEquals(listOf("share bill.pdf", "downloads bill.pdf", "saveTo bill.pdf content://picked/1"), exports.calls)
    }

    @Test
    fun `export passes a failure through`() = runTest {
        val exports = FakeExports()
        val failure = ServerException(Kind.UNREADABLE, "Couldn't save bill.pdf")
        exports.failure = failure
        try {
            ExportDocumentUseCase(exports).saveToDownloads(library.file)
            fail()
        } catch (e: ServerException) {
            assertSame(failure, e)
        }
    }

    @Test
    fun `open file signed out is not signed in`() = runTest {
        expectSignedOut { OpenDocumentFileUseCase(library, signedOut)(4, DocumentFileKind.PREVIEW) }
    }

    @Test
    fun `open file passes a server error through`() = runTest {
        expectPassedThrough { OpenDocumentFileUseCase(library, signedIn)(4, DocumentFileKind.PREVIEW) }
    }

    // --- edits ---

    private val rename = DocumentChanges(title = Change.To("New"))

    @Test
    fun `update saves the changes and returns the document`() = runTest {
        library.document = doc(3, "New")

        assertEquals(doc(3, "New"), UpdateDocumentUseCase(library, signedIn)(3, rename))
        assertEquals(listOf("update 3 $rename"), library.calls)
        assertEquals(listOf(CONFIG), library.configs)
    }

    @Test
    fun `update with nothing to change is refused without a call`() = runTest {
        assertTrue(DocumentChanges().isEmpty)
        try {
            UpdateDocumentUseCase(library, signedIn)(3, DocumentChanges())
            fail()
        } catch (e: IllegalArgumentException) {
            assertTrue(library.calls.isEmpty())
        }
    }

    @Test
    fun `clearing a field is a change`() {
        assertTrue(!DocumentChanges(correspondentId = Change.To(null)).isEmpty)
    }

    @Test
    fun `update signed out is not signed in`() = runTest {
        expectSignedOut { UpdateDocumentUseCase(library, signedOut)(3, rename) }
    }

    @Test
    fun `update passes a server error through`() = runTest {
        expectPassedThrough { UpdateDocumentUseCase(library, signedIn)(3, rename) }
    }

    @Test
    fun `delete moves one document to the trash`() = runTest {
        DeleteDocumentUseCase(library, signedIn)(3)
        assertEquals(listOf("delete 3"), library.calls)
    }

    @Test
    fun `delete signed out is not signed in`() = runTest {
        expectSignedOut { DeleteDocumentUseCase(library, signedOut)(3) }
    }

    @Test
    fun `delete passes a server error through`() = runTest {
        expectPassedThrough { DeleteDocumentUseCase(library, signedIn)(3) }
    }

    @Test
    fun `trash lists a page and restores`() = runTest {
        val trash = TrashUseCase(library, signedIn)

        assertSame(library.page, trash.list())
        trash.list(page = 2, pageSize = 10)
        trash.restore(listOf(3, 4))

        assertEquals(listOf("trash 1 25", "trash 2 10", "restore [3, 4]"), library.calls)
    }

    @Test
    fun `trash signed out is not signed in`() = runTest {
        expectSignedOut { TrashUseCase(library, signedOut).list() }
        expectSignedOut { TrashUseCase(library, signedOut).restore(listOf(3)) }
    }

    @Test
    fun `trash passes a server error through`() = runTest {
        expectPassedThrough { TrashUseCase(library, signedIn).list() }
        expectPassedThrough { TrashUseCase(library, signedIn).restore(listOf(3)) }
    }

    @Test
    fun `bulk edit passes the ids and the edit`() = runTest {
        BulkEditUseCase(library, signedIn)(listOf(1, 2), BulkEdit.Delete)
        assertEquals(listOf("bulkEdit [1, 2] Delete"), library.calls)
    }

    @Test
    fun `bulk edit signed out is not signed in`() = runTest {
        expectSignedOut { BulkEditUseCase(library, signedOut)(listOf(1), BulkEdit.SetCorrespondent(null)) }
    }

    @Test
    fun `bulk edit passes a server error through`() = runTest {
        expectPassedThrough { BulkEditUseCase(library, signedIn)(listOf(1), BulkEdit.ModifyTags(setOf(1), emptySet())) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a query starts at page 1`() {
        DocumentQuery(page = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a query asks for at most 100 documents`() {
        DocumentQuery(pageSize = 101)
    }

    // --- notes ---

    @Test
    fun `notes load, add trimmed text and say whether they're on`() = runTest {
        val notes = NotesUseCase(library, signedIn)

        assertEquals(library.notes, notes.load(4))
        val after = notes.add(4, "  Paid  ")
        assertEquals("Paid", after.first().text)
        library.notesEnabled = false
        assertEquals(false, notes.enabled())

        assertEquals(listOf("notes 4", "addNote 4 Paid", "notesEnabled"), library.calls)
        assertTrue(library.configs.all { it == CONFIG })
    }

    @Test
    fun `a blank note is refused without a call`() = runTest {
        try {
            NotesUseCase(library, signedIn).add(4, "   ")
            fail()
        } catch (e: IllegalArgumentException) {
            assertTrue(library.addedNotes.isEmpty())
        }
    }

    @Test
    fun `notes signed out are not signed in`() = runTest {
        expectSignedOut { NotesUseCase(library, signedOut).load(4) }
        expectSignedOut { NotesUseCase(library, signedOut).add(4, "x") }
        expectSignedOut { NotesUseCase(library, signedOut).enabled() }
    }

    @Test
    fun `notes pass a server error through`() = runTest {
        expectPassedThrough { NotesUseCase(library, signedIn).load(4) }
        expectPassedThrough { NotesUseCase(library, signedIn).add(4, "x") }
        expectPassedThrough { NotesUseCase(library, signedIn).enabled() }
    }

    @Test
    fun `browse passes a similar-to query through`() = runTest {
        val query = DocumentQuery(similarTo = 7)
        BrowseDocumentsUseCase(library, signedIn)(query)
        assertEquals(listOf(query), library.queries)
    }
}
