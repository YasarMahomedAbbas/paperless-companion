package io.github.yasarmahomedabbas.paperlesscompanion.domain

import io.github.yasarmahomedabbas.paperlesscompanion.CONFIG
import io.github.yasarmahomedabbas.paperlesscompanion.FakeServer
import io.github.yasarmahomedabbas.paperlesscompanion.FakeSettings
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.LabelKind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.DocumentLinkUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.LabelsLinkUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentLinkUseCaseTest {
    private val server = FakeServer()

    @Test
    fun `signed in, the server builds the link`() {
        val link = DocumentLinkUseCase(server, FakeSettings(CONFIG))

        assertEquals("http://paperless:8000/documents/5", link(5))
    }

    @Test
    fun `signed out, there is no link`() {
        val link = DocumentLinkUseCase(server, FakeSettings())

        assertNull(link(5))
    }

    @Test
    fun `signed in, the server builds the labels link`() {
        val link = LabelsLinkUseCase(server, FakeSettings(CONFIG))

        assertEquals("http://paperless:8000/labels/TAG", link(LabelKind.TAG))
    }

    @Test
    fun `signed out, there is no labels link`() {
        val link = LabelsLinkUseCase(server, FakeSettings())

        assertNull(link(LabelKind.CORRESPONDENT))
    }
}
