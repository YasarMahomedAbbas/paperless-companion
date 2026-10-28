package io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.Labels
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DocumentLibrary
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.SettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Every tag, correspondent, document type and storage path, loaded side by side. The first failure is thrown. */
class LoadLabelsUseCase(private val library: DocumentLibrary, private val settings: SettingsRepository) {
    suspend operator fun invoke(): Labels {
        val config = settings.signedIn()
        return coroutineScope {
            val tags = async { library.tags(config) }
            val correspondents = async { library.correspondents(config) }
            val documentTypes = async { library.documentTypes(config) }
            val storagePaths = async { library.storagePaths(config) }
            Labels(tags.await(), correspondents.await(), documentTypes.await(), storagePaths.await())
        }
    }
}
