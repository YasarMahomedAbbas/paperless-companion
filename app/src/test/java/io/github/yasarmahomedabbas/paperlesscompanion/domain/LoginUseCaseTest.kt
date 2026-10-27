package io.github.yasarmahomedabbas.paperlesscompanion.domain

import io.github.yasarmahomedabbas.paperlesscompanion.FakePaperless
import io.github.yasarmahomedabbas.paperlesscompanion.FakeSettings
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.PaperlessException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.LoginUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.normalizeServerUrl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginUseCaseTest {
    private val paperless = FakePaperless()
    private val settings = FakeSettings()
    private val login = LoginUseCase(paperless, settings)

    @Test
    fun `signs in, saves the server and token`() = runTest {
        val result = login(" paperless:8000/ ", " anna ", "pw")

        val expected = ServerConfig("http://paperless:8000", "token-123")
        assertEquals(expected, result.getOrThrow())
        assertEquals(expected, settings.config)
        assertEquals(Triple("http://paperless:8000", "anna", "pw"), paperless.logins.single())
    }

    @Test
    fun `rejects an empty server address without calling Paperless`() = runTest {
        val result = login("  ", "anna", "pw")

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertTrue(paperless.logins.isEmpty())
    }

    @Test
    fun `rejects missing credentials`() = runTest {
        assertTrue(login("paperless", "", "pw").isFailure)
        assertTrue(login("paperless", "anna", "").isFailure)
        assertTrue(paperless.logins.isEmpty())
    }

    @Test
    fun `passes Paperless errors through and saves nothing`() = runTest {
        paperless.loginResult = { throw PaperlessException(PaperlessException.Kind.UNAUTHORIZED, "nope") }

        val error = login("paperless", "anna", "wrong").exceptionOrNull()

        assertEquals(PaperlessException.Kind.UNAUTHORIZED, (error as PaperlessException).kind)
        assertNull(settings.config)
    }
}

class NormalizeServerUrlTest {
    @Test
    fun `adds http when no scheme is given`() {
        assertEquals("http://192.168.1.5:8000", normalizeServerUrl("192.168.1.5:8000"))
    }

    @Test
    fun `keeps https and paths`() {
        assertEquals("https://box.tail1234.ts.net/paperless", normalizeServerUrl("https://box.tail1234.ts.net/paperless/"))
    }

    @Test
    fun `drops a trailing api segment`() {
        assertEquals("http://paperless:8000", normalizeServerUrl("http://paperless:8000/api/"))
    }

    @Test
    fun `returns null for unusable input`() {
        assertNull(normalizeServerUrl(""))
        assertNull(normalizeServerUrl("http://"))
        assertNull(normalizeServerUrl("my server"))
    }
}
