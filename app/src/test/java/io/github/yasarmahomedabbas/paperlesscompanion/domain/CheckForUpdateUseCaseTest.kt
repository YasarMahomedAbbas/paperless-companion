package io.github.yasarmahomedabbas.paperlesscompanion.domain

import io.github.yasarmahomedabbas.paperlesscompanion.FakeReleases
import io.github.yasarmahomedabbas.paperlesscompanion.FakeUpdateStore
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.AppRelease
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UpdateException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UpdateException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.CheckForUpdateUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.CheckForUpdateUseCase.Outcome
import io.github.yasarmahomedabbas.paperlesscompanion.release
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckForUpdateUseCaseTest {
    private val releases = FakeReleases()
    private val store = FakeUpdateStore()
    private val announced = mutableListOf<AppRelease>()
    private var installed = 104L
    private val check = CheckForUpdateUseCase(releases, store, { installed }, { announced += it })

    @Test
    fun `a newer release is offered and announced once`() = runTest {
        releases.latest = release("0.2.0", 200)

        assertEquals(Outcome.Done, check.run())
        assertEquals(release("0.2.0", 200), store.offer.value)
        assertEquals(listOf(release("0.2.0", 200)), announced)
        assertEquals(200L, store.notified)

        assertEquals(Outcome.Done, check.run())
        assertEquals(1, announced.size)
        assertEquals(release("0.2.0", 200), store.offer.value)
    }

    @Test
    fun `a still newer release is announced again`() = runTest {
        store.notified = 200
        releases.latest = release("0.3.0", 300)

        check.run()

        assertEquals(listOf(release("0.3.0", 300)), announced)
        assertEquals(300L, store.notified)
    }

    @Test
    fun `the same version clears the offer`() = runTest {
        store.saveOffer(release("0.1.4", 104))
        releases.latest = release("0.1.4", 104)

        assertEquals(Outcome.Done, check.run())
        assertNull(store.offer.value)
        assertTrue(announced.isEmpty())
    }

    @Test
    fun `an older release clears the offer`() = runTest {
        installed = 300
        store.saveOffer(release("0.2.0", 200))
        releases.latest = release("0.2.0", 200)

        check.run()

        assertNull(store.offer.value)
        assertTrue(announced.isEmpty())
    }

    @Test
    fun `no usable release clears the offer`() = runTest {
        store.saveOffer(release())

        assertEquals(Outcome.Done, check.run())
        assertNull(store.offer.value)
    }

    @Test
    fun `offline tries again and keeps the offer`() = runTest {
        store.saveOffer(release())
        releases.failure = UpdateException(Kind.OFFLINE, "no network")

        assertEquals(Outcome.TryAgain, check.run())
        assertEquals(release(), store.offer.value)
    }

    @Test
    fun `a server error tries again and keeps the offer`() = runTest {
        store.saveOffer(release())
        releases.failure = UpdateException(Kind.SERVER, "HTTP 502", status = 502)

        assertEquals(Outcome.TryAgain, check.run())
        assertEquals(release(), store.offer.value)
    }

    @Test
    fun `a server failure without a status tries again`() = runTest {
        releases.failure = UpdateException(Kind.SERVER, "nothing")
        assertEquals(Outcome.TryAgain, check.run())
    }

    @Test
    fun `a rate limit is left to the next check`() = runTest {
        store.saveOffer(release())
        releases.failure = UpdateException(Kind.SERVER, "HTTP 403", status = 403)

        assertEquals(Outcome.Done, check.run())
        assertEquals(release(), store.offer.value)
        assertTrue(announced.isEmpty())
    }
}
