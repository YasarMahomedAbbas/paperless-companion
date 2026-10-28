package io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.SavedViews
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DocumentLibrary
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.SettingsRepository

/** The views saved in the signed-in server's web interface. Throws ServerException. */
class SavedViewsUseCase(private val library: DocumentLibrary, private val settings: SettingsRepository) {
    suspend operator fun invoke(): SavedViews = library.savedViews(settings.signedIn())
}
