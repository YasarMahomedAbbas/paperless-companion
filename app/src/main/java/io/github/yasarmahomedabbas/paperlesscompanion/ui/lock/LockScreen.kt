package io.github.yasarmahomedabbas.paperlesscompanion.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The unlock screen: opaque, on the page ground, and it takes every touch, so whatever is
 * composed underneath (Home, on a re-lock) can't be seen or used. Asks once by itself.
 */
@Composable
fun LockScreen(protect: Boolean, message: String?, onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    // A Surface that isn't clickable still consumes touches, so nothing reaches the screen below.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Paperless Scan", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                if (protect) "Confirm it's you to keep this sign-in" else "Unlock to see your documents",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Unlock") }
        }
    }
}
