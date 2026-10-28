package io.github.yasarmahomedabbas.paperlesscompanion.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerConfig
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppButton
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppIcons
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppTextField
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.NoticeBanner
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.NoticeTone
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.lead

@Composable
fun LoginScreen(viewModel: LoginViewModel, notice: String?, onSignedIn: (ServerConfig) -> Unit) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.signedIn) {
        state.signedIn?.let {
            onSignedIn(it)
            viewModel.onSignedInHandled()
        }
    }
    LoginContent(
        state = state,
        notice = notice,
        onServerUrl = viewModel::onServerUrl,
        onUsername = viewModel::onUsername,
        onPassword = viewModel::onPassword,
        onSubmit = viewModel::submit,
    )
}

/**
 * Sign in (docs/design/layouts.md § Sign in): a form. Logo tile, name and one line about the
 * network, an info notice when the app sent the user back here (#8), a notice over the fields
 * when signing in failed (with the field it's about marked),
 * the three fields, the note about the token, and Sign in pinned above the keyboard.
 */
@Composable
private fun LoginContent(
    state: LoginUiState,
    onServerUrl: (String) -> Unit,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSubmit: () -> Unit,
    notice: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter),
        ) {
            Spacer(Modifier.height(Spacing.s40))
            Box(
                Modifier.size(56.dp).background(colors.primaryContainer, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Document, contentDescription = null, tint = colors.primary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(Spacing.s24))
            Text(
                "Paperless Scan",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Spacing.s8))
            Text(
                "Sign in to your Paperless server. You need to be on the home network or the VPN.",
                style = MaterialTheme.typography.lead,
                color = colors.onSurfaceVariant,
            )
            // Why the app is back at sign-in (the stored sign-in was voided, #8): something to know, not a refusal.
            notice?.let {
                Spacer(Modifier.height(Spacing.s24))
                NoticeBanner(it, tone = NoticeTone.Info)
            }
            state.error?.let { error ->
                Spacer(Modifier.height(Spacing.s24))
                NoticeBanner(error, tone = if (state.unreachable) NoticeTone.Unreachable else NoticeTone.Refused)
            }
            Spacer(Modifier.height(Spacing.s24))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.fieldGap)) {
                AppTextField(
                    label = "Server address",
                    value = state.serverUrl,
                    onValueChange = onServerUrl,
                    placeholder = "http://paperless:8000",
                    isError = state.errorField == LoginField.SERVER,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                )
                AppTextField(
                    label = "Username",
                    value = state.username,
                    onValueChange = onUsername,
                    isError = state.errorField == LoginField.USERNAME,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                AppTextField(
                    label = "Password",
                    value = state.password,
                    onValueChange = onPassword,
                    password = true,
                    isError = state.errorField == LoginField.PASSWORD,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                )
            }
            Spacer(Modifier.height(Spacing.s16))
            Text(
                "The app swaps your password for a sign-in token and keeps only that, encrypted on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.s24))
        }
        AppButton(
            text = "Sign in",
            onClick = onSubmit,
            busy = state.busy,
            onlyControl = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.actionTop, bottom = Spacing.actionBottomLarge),
        )
    }
}

private val filledIn = LoginUiState(serverUrl = "http://paperless:8000", username = "yasar", password = "correcthorse")

@Preview(name = "Sign in — view", widthDp = 412, heightDp = 915)
@Composable
private fun LoginViewPreview() = PaperlessTheme { LoginContent(filledIn, {}, {}, {}, {}) }

@Preview(name = "Sign in — signing in", widthDp = 412, heightDp = 915)
@Composable
private fun LoginBusyPreview() = PaperlessTheme { LoginContent(filledIn.copy(busy = true), {}, {}, {}, {}) }

@Preview(name = "Sign in — can't reach", widthDp = 360, heightDp = 800)
@Composable
private fun LoginUnreachablePreview() = PaperlessTheme {
    LoginContent(
        filledIn.copy(
            serverUrl = "http://paperles:8000",
            error = "Can't reach Paperless (timeout). Are you on the home network or the VPN?",
            errorField = LoginField.SERVER,
            unreachable = true,
        ),
        {}, {}, {}, {},
    )
}

@Preview(name = "Sign in — wrong password", widthDp = 412, heightDp = 915)
@Composable
private fun LoginRefusedPreview() = PaperlessTheme {
    LoginContent(filledIn.copy(error = "Wrong username or password", errorField = LoginField.PASSWORD), {}, {}, {}, {})
}
