package io.github.yasarmahomedabbas.paperlesscompanion.domain

import io.github.yasarmahomedabbas.paperlesscompanion.doc
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.BulkEdit
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.with
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DocumentEditTest {
    private val document = doc(1).copy(tagIds = listOf(3, 1, 2), correspondentId = 4, documentTypeId = 5, storagePathId = 6)

    @Test
    fun `adding tags appends the ones it lacks, keeping the order`() {
        assertEquals(listOf(3, 1, 2, 9), document.with(BulkEdit.ModifyTags(add = setOf(1, 9), remove = emptySet())).tagIds)
    }

    @Test
    fun `removing tags drops them and keeps the rest in order`() {
        assertEquals(listOf(3, 2), document.with(BulkEdit.ModifyTags(add = emptySet(), remove = setOf(1, 7))).tagIds)
    }

    @Test
    fun `adding and removing in one go`() {
        assertEquals(listOf(1, 2, 8), document.with(BulkEdit.ModifyTags(add = setOf(8), remove = setOf(3))).tagIds)
    }

    @Test
    fun `correspondent, type and storage path are set or cleared`() {
        assertEquals(7, document.with(BulkEdit.SetCorrespondent(7)).correspondentId)
        assertEquals(null, document.with(BulkEdit.SetCorrespondent(null)).correspondentId)
        assertEquals(8, document.with(BulkEdit.SetDocumentType(8)).documentTypeId)
        assertEquals(null, document.with(BulkEdit.SetDocumentType(null)).documentTypeId)
        assertEquals(9, document.with(BulkEdit.SetStoragePath(9)).storagePathId)
        assertEquals(null, document.with(BulkEdit.SetStoragePath(null)).storagePathId)
    }

    @Test
    fun `only the field the edit names changes`() {
        assertEquals(document.copy(correspondentId = 7), document.with(BulkEdit.SetCorrespondent(7)))
    }

    @Test
    fun `delete leaves it unchanged`() {
        assertSame(document, document.with(BulkEdit.Delete))
    }
}
