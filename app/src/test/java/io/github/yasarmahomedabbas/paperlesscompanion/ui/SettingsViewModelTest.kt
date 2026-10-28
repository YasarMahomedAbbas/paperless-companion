package io.github.yasarmahomedabbas.paperlesscompanion.ui

import io.github.yasarmahomedabbas.paperlesscompanion.CONFIG
import io.github.yasarmahomedabbas.paperlesscompanion.FakeSettings
import io.github.yasarmahomedabbas.paperlesscompanion.FakeUpdateStore
import io.github.yasarmahomedabbas.paperlesscompanion.MainDispatcherRule
import io.github.yasarmahomedabbas.paperlesscompanion.release
import io.github.yasarmahomedabbas.paperlesscompanion.ui.settings.SettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val settings = FakeSettings(CONFIG)
    private val store = FakeUpdateStore()
    private var installed = 104L

    private fun viewModel() = SettingsViewModel(settings, store) { installed }

    @Test
    fun `shows the server address`() {
        assertEquals("http://paperless:8000", viewModel().serverUrl)
    }

    @Test
    fun `signed out shows no address`() {
        settings.config = null
        assertEquals("", viewModel().serverUrl)
    }

    @Test
    fun `sign out forgets the server`() {
        viewModel().signOut()
        assertNull(settings.config)
    }

    @Test
    fun `a newer release waiting shows its version`() = runTest(main.dispatcher) {
        store.saveOffer(release("0.4.0", 400))
        val vm = viewModel()

        assertEquals("0.4.0", vm.updateReady.value)
    }

    @Test
    fun `an offer that isn't newer shows nothing, and one that arrives later shows`() = runTest(main.dispatcher) {
        installed = 400
        store.saveOffer(release("0.4.0", 400))
        val vm = viewModel()
        assertNull(vm.updateReady.value)

        store.saveOffer(release("0.5.0", 500))
        advanceUntilIdle()

        assertEquals("0.5.0", vm.updateReady.value)
    }
}
