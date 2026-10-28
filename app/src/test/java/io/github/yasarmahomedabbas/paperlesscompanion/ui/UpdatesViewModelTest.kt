package io.github.yasarmahomedabbas.paperlesscompanion.ui

import io.github.yasarmahomedabbas.paperlesscompanion.FakeDownloader
import io.github.yasarmahomedabbas.paperlesscompanion.FakeInstaller
import io.github.yasarmahomedabbas.paperlesscompanion.FakeReleases
import io.github.yasarmahomedabbas.paperlesscompanion.FakeUpdateStore
import io.github.yasarmahomedabbas.paperlesscompanion.MainDispatcherRule
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.InstallEvent
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UpdateException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UpdateException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.CheckForUpdateUseCase
import io.github.yasarmahomedabbas.paperlesscompanion.release
import io.github.yasarmahomedabbas.paperlesscompanion.ui.updates.Phase
import io.github.yasarmahomedabbas.paperlesscompanion.ui.updates.UpdatesViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdatesViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val offer = release("0.2.0", 200)
    private val store = FakeUpdateStore(offer)
    private val releases = FakeReleases(offer)
    private val downloader = FakeDownloader()
    private val installer = FakeInstaller(versionCode = 104)
    private val check = CheckForUpdateUseCase(releases, store, installer::installedVersionCode) {}

    private fun viewModel() = UpdatesViewModel("0.1.4", store, downloader, installer, check)

    @Test
    fun `shows the installed version and the newer release`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("0.1.4", vm.state.value.installedVersion)
        assertEquals(offer, vm.state.value.offer)
        assertEquals(Phase.Idle, vm.state.value.phase)
    }

    @Test
    fun `an offer that isn't newer than what's installed is up to date`() = runTest(main.dispatcher) {
        installer.versionCode = 200
        val vm = viewModel()
        advanceUntilIdle()

        assertNull(vm.state.value.offer)
    }

    @Test
    fun `no offer is up to date`() = runTest(main.dispatcher) {
        store.saveOffer(null)
        val vm = viewModel()
        advanceUntilIdle()

        assertNull(vm.state.value.offer)
        vm.install()
        advanceUntilIdle()
        assertTrue(downloader.downloads.isEmpty())
    }

    @Test
    fun `install downloads with progress, then hands the file to Android`() = runTest(main.dispatcher) {
        downloader.progress = listOf(500)
        downloader.gate = CompletableDeferred()
        val vm = viewModel()
        advanceUntilIdle()

        vm.install()
        runCurrent()
        assertEquals(Phase.Downloading(500, 1234), vm.state.value.phase)
        assertTrue(vm.state.value.busy)

        downloader.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(offer.apk), downloader.downloads)
        assertEquals(listOf(downloader.file), installer.installs)
        assertEquals(Phase.Installing, vm.state.value.phase)
    }

    @Test
    fun `a second install while busy is ignored`() = runTest(main.dispatcher) {
        downloader.gate = CompletableDeferred()
        val vm = viewModel()
        advanceUntilIdle()

        vm.install()
        runCurrent()
        vm.install()
        runCurrent()

        assertEquals(1, downloader.downloads.size)
    }

    @Test
    fun `installed drops the offer`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.install()
        advanceUntilIdle()

        installer.versionCode = 200
        installer.events.value = InstallEvent.Installed
        advanceUntilIdle()

        assertNull(vm.state.value.offer)
        assertEquals(Phase.Idle, vm.state.value.phase)
    }

    @Test
    fun `refused by Android says so with its message`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.install()
        advanceUntilIdle()

        installer.events.value = InstallEvent.Failed("INSTALL_FAILED_UPDATE_INCOMPATIBLE")
        advanceUntilIdle()

        assertEquals(Phase.Failed("Android didn't install the update: INSTALL_FAILED_UPDATE_INCOMPATIBLE"), vm.state.value.phase)
        assertEquals(offer, vm.state.value.offer)
    }

    @Test
    fun `cancelled at Android's confirmation says so plainly`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.install()
        advanceUntilIdle()

        installer.events.value = InstallEvent.Failed("")
        advanceUntilIdle()

        assertEquals(Phase.Failed("Android didn't install the update."), vm.state.value.phase)
    }

    @Test
    fun `an old outcome never answers a new install`() = runTest(main.dispatcher) {
        installer.events.value = InstallEvent.Failed("old")
        val vm = viewModel()
        advanceUntilIdle()

        vm.install()
        advanceUntilIdle()

        assertEquals(Phase.Installing, vm.state.value.phase)
    }

    @Test
    fun `a download that doesn't match is refused in words`() = runTest(main.dispatcher) {
        expectFailure(Kind.CHECKSUM, "The download didn't match the release, so it wasn't installed.", onDownload = true)
    }

    @Test
    fun `offline is said in words`() = runTest(main.dispatcher) {
        expectFailure(Kind.OFFLINE, "Couldn't reach GitHub. Check the connection and try again.", onDownload = true)
    }

    @Test
    fun `no space is said in words`() = runTest(main.dispatcher) {
        expectFailure(Kind.STORAGE, "Not enough space to download the update.", onDownload = true)
    }

    @Test
    fun `a server refusal is said in words`() = runTest(main.dispatcher) {
        expectFailure(Kind.SERVER, "GitHub didn't send the update. Try again later.", onDownload = true)
    }

    @Test
    fun `another signer is refused in words`() = runTest(main.dispatcher) {
        expectFailure(Kind.NOT_OURS, "This update isn't signed with the app's key, so it wasn't installed.", onDownload = false)
    }

    @Test
    fun `a session Android refuses is said with its message`() = runTest(main.dispatcher) {
        expectFailure(Kind.REFUSED, "Android didn't install the update: session broken", onDownload = false, message = "session broken")
    }

    private suspend fun kotlinx.coroutines.test.TestScope.expectFailure(
        kind: Kind,
        reason: String,
        onDownload: Boolean,
        message: String = "x",
    ) {
        val failure = UpdateException(kind, message)
        if (onDownload) downloader.failure = failure else installer.failure = failure
        val vm = viewModel()
        advanceUntilIdle()

        vm.install()
        advanceUntilIdle()

        assertEquals(Phase.Failed(reason), vm.state.value.phase)
        assertEquals(offer, vm.state.value.offer)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `needing the permission keeps the download and installs it once allowed`() = runTest(main.dispatcher) {
        installer.failure = UpdateException(Kind.NEEDS_PERMISSION, "not allowed")
        installer.allowed = false
        val vm = viewModel()
        advanceUntilIdle()

        vm.install()
        advanceUntilIdle()
        assertEquals(Phase.NeedsPermission, vm.state.value.phase)

        // Back from the settings screen without allowing it: still waiting.
        vm.resumed()
        advanceUntilIdle()
        assertEquals(Phase.NeedsPermission, vm.state.value.phase)

        installer.failure = null
        installer.allowed = true
        vm.resumed()
        advanceUntilIdle()

        assertEquals(1, downloader.downloads.size)
        assertEquals(listOf(downloader.file), installer.installs)
        assertEquals(Phase.Installing, vm.state.value.phase)
    }

    @Test
    fun `install again after needing the permission downloads afresh`() = runTest(main.dispatcher) {
        installer.failure = UpdateException(Kind.NEEDS_PERMISSION, "not allowed")
        val vm = viewModel()
        advanceUntilIdle()
        vm.install()
        advanceUntilIdle()

        installer.failure = null
        vm.install()
        advanceUntilIdle()
        vm.resumed()
        advanceUntilIdle()

        assertEquals(2, downloader.downloads.size)
        assertEquals(1, installer.installs.size)
    }

    @Test
    fun `stop ends the download`() = runTest(main.dispatcher) {
        downloader.gate = CompletableDeferred()
        val vm = viewModel()
        advanceUntilIdle()
        vm.install()
        runCurrent()

        vm.stop()
        advanceUntilIdle()

        assertEquals(Phase.Idle, vm.state.value.phase)
        assertTrue(installer.installs.isEmpty())
        assertEquals(offer, vm.state.value.offer)
    }

    @Test
    fun `stop does nothing unless downloading`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.install()
        advanceUntilIdle()

        vm.stop()

        assertEquals(Phase.Installing, vm.state.value.phase)
    }

    @Test
    fun `check now finds a newer release`() = runTest(main.dispatcher) {
        store.saveOffer(null)
        val vm = viewModel()
        advanceUntilIdle()
        releases.latest = release("0.3.0", 300)

        vm.checkNow()
        assertTrue(vm.state.value.checking)
        advanceUntilIdle()

        assertFalse(vm.state.value.checking)
        assertFalse(vm.state.value.checkFailed)
        assertEquals(release("0.3.0", 300), vm.state.value.offer)
    }

    @Test
    fun `check now that can't reach GitHub says so and keeps the offer`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        releases.failure = UpdateException(Kind.OFFLINE, "no network")

        vm.checkNow()
        advanceUntilIdle()

        assertTrue(vm.state.value.checkFailed)
        assertFalse(vm.state.value.checking)
        assertEquals(offer, vm.state.value.offer)
    }

    @Test
    fun `the offer follows the store`() = runTest(main.dispatcher) {
        store.saveOffer(null)
        val vm = viewModel()
        advanceUntilIdle()

        store.saveOffer(offer)
        advanceUntilIdle()

        assertEquals(offer, vm.state.value.offer)
    }
}
