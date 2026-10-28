package io.github.yasarmahomedabbas.paperlesscompanion.ui.documents

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.ActionOptionRow
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppBottomSheet
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppIcons
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing

/** The Add sheet (the canvas's "Add — choose" board): Scan is the main option, then Take photo and Choose file. */
@Composable
fun AddSheet(onDismiss: () -> Unit, onScan: () -> Unit, onTakePhoto: () -> Unit, onChooseFile: () -> Unit) {
    AppBottomSheet("Add a document", onDismiss) {
        ActionOptionRow(AppIcons.Scan, "Scan document", "Finds the edges, crops, several pages", main = true, onClick = onScan)
        ActionOptionRow(AppIcons.Camera, "Take photo", "Works without Google Play services", main = false, onClick = onTakePhoto)
        ActionOptionRow(AppIcons.File, "Choose file", "PDFs and images already on the phone", main = false, onClick = onChooseFile)
        Text(
            "You can also share PDFs and photos to Paperless Scan from any other app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.s8).padding(top = Spacing.s12),
        )
    }
}
