# Checklist — Unlock the app with a fingerprint

Issue: #8 — Unlock the app with a fingerprint (https://github.com/YasarMahomedAbbas/paperless-companion/issues/8)
Branch: `feat/8-fingerprint-unlock`
Problem: a phone left unlocked opens straight into the app, and the stored API token can be decrypted by the app at any time without the owner being there.

## Done when

- Opening the app on a phone with a fingerprint set up asks for it (PIN / pattern as fallback), and the token can't be decrypted without that unlock.
- After more than 1 minute in the background, the app asks again.
- Uploads shared from another app ask too, and are only sent after the unlock.
- A "Lock with fingerprint" switch turns it off and on again.
- A phone with no screen lock keeps working exactly as today.
- Tested on both our phones (GrapheneOS included).

## References

- Paperless API: none. No call is added or changed; this is Keystore, BiometricPrompt and lifecycle work only.
- Android: `androidx.biometric:biometric:1.1.0` (latest stable; `BiometricPrompt`, `BiometricManager.Authenticators`); `KeyGenParameterSpec.Builder.setUserAuthenticationRequired` with `setUserAuthenticationParameters(timeout, AUTH_BIOMETRIC_STRONG or AUTH_DEVICE_CREDENTIAL)` on API 30+ and `setUserAuthenticationValidityDurationSeconds(timeout)` on API 26–29; `KeyguardManager.isDeviceSecure`; `UserNotAuthenticatedException`, `KeyPermanentlyInvalidatedException`.
- `androidx.fragment:fragment-ktx:1.8.9` (BiometricPrompt needs a `FragmentActivity`).
- Design: `docs/design/README.md` (rules 3, 5, 7, 9). The lock screen is a centred single-action screen; the switch goes in an overflow menu on the home top bar, next to Sign out.

## Context

Today `KeystoreSettingsRepository` keeps the server URL in prefs and the token AES-GCM-sealed with a Keystore key (`paperless-token`) that needs no user authentication, and `MainActivity` reads it on every launch. This ticket adds a second, auth-bound key (`paperless-token-locked`) that only works for 10 s after the user authenticates (decided: time-limited key, option 3a). The token is decrypted once right after the app's own prompt and kept in memory until the app locks again. The app re-locks after 1 minute in the background (decided: fixed timeout, 1a). The switch lives in a small top-bar menu (2a). Lock is on by default when the phone has a screen lock. Without a screen lock the switch is hidden and the plain key is used, as today. The data layer is split so its decision logic (which key, migrating from the old key, voided keys) runs in unit tests against a fake vault and a fake key-value store. Only the thin Keystore and SharedPreferences adapters need a device. The domain gets the lock in its own words (`StoredSignIn`, `AppLockRepository`, `AppLock`). A `GateViewModel` in `ui/lock/` turns those into one screen state. Scanned, photographed, picked and shared documents all go into a buffer that MainActivity owns and hands to Home only while unlocked. Home stays composed under an opaque lock overlay when it re-locks, so an activity result (e.g. a long scan) that arrives while locked is not lost.

## Test plan

- Unit: `SealedSettingsRepository` (data, fake vault + store): no sign-in → None; lock on and nothing in memory → Locked without touching the vault; `unlock()` decrypts → Ready and caches; vault says not-authenticated → Locked; vault says voided → clears prefs → Voided; a token sealed with the plain key and lock on is re-sealed with the locked key on unlock (migration); `save()` with lock active and no recent auth keeps the token in memory, marks it unsaved, and `unlock()` seals it; `setLockEnabled(false/true)` re-seals with the other key; no screen lock → plain key regardless of the switch; `forget()` drops the in-memory token; `clear()` wipes both.
- Unit: `AppLock` (domain, fake clock): inactive without a screen lock or when off; no re-lock under 1 minute; re-lock at ≥ 1 minute calls `forget()`; foreground without a prior background doesn't lock.
- Unit: `GateViewModel`: initial state for each `StoredSignIn`; unlock success / not-authenticated / voided; sign-in with lock active → Protect → Home after unlock; sign-in with lock inactive → Home directly; background/foreground re-lock to `Locked(overlay = true)`; lock switch on/off; sign-out → SignIn.
- MockWebServer: none (no API change).
- Real server: none. Self-verification records it as not applicable.
- Device only: the prompt itself (fingerprint, PIN fallback, cancel), the auth-bound key really refusing to decrypt without the prompt, re-lock after 1 minute, a share from another app while locked, a scan that returns after the re-lock, adding a fingerprint (voids the key → sign in again with the notice), a phone with no screen lock, and both our phones including GrapheneOS.

---

## Checkpoint 1 — Seal the token behind the phone's lock · commit: `feat(auth): seal the token behind the phone's lock`

Goal: the data layer can seal the token with an auth-bound key, reports Locked / Voided / Ready in domain terms, and its decision logic is unit-tested. The app's behaviour is unchanged until checkpoint 3 wires the gate (`MainActivity` keeps calling `load()`).

### [x] 1.1 — Domain: what a stored sign-in can be

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/model/Models.kt`
Add:
```kotlin
/** What reading the saved sign-in found. */
sealed interface StoredSignIn {
    /** Nobody has signed in on this phone. */
    data object None : StoredSignIn
    /** The token is sealed behind the phone's lock: unlock, then call AppLockRepository.unlock(). */
    data object Locked : StoredSignIn
    /** The phone voided the key (a new fingerprint, or the screen lock was removed); the sign-in is gone. */
    data object Voided : StoredSignIn
    data class Ready(val config: ServerConfig) : StoredSignIn
}
```

### [x] 1.2 — Domain: the lock's repository

File: `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/domain/repository/Repositories.kt`
- `SettingsRepository` gains `fun read(): StoredSignIn`. `load()` becomes a default method: `fun load(): ServerConfig? = (read() as? StoredSignIn.Ready)?.config`. `save` / `clear` stay. `save` doc: "with the lock active and no recent unlock, the token is held in memory only until `AppLockRepository.unlock()` seals it."
- New interface:
```kotlin
/** The phone's own lock (fingerprint, PIN, pattern) guarding the saved sign-in. */
interface AppLockRepository {
    /** True when the phone has a screen lock at all, so the app can be locked. */
    fun canLock(): Boolean
    /** Whether the user wants the app locked. True until turned off. */
    fun isEnabled(): Boolean
    /** Call right after the user unlocked: reads (and if needed seals or re-seals) the sign-in. */
    fun unlock(): StoredSignIn
    /** Call right after the user unlocked: turns the lock on or off and re-seals the token to match. */
    fun setEnabled(enabled: Boolean)
    /** Drops the token held in memory, so the next read() is Locked again. */
    fun forget()
}
```

### [x] 1.3 — Data: the vault and the store, device-only adapters

Files (new): `app/src/main/java/io/github/yasarmahomedabbas/paperlesscompanion/data/TokenVault.kt`, `.../data/KeystoreTokenVault.kt`, `.../data/KeyValueStore.kt`
- `TokenVault.kt`: `interface TokenVault { fun seal(plain: String, locked: Boolean): String; fun open(sealed: String, locked: Boolean): String; fun delete(locked: Boolean) }` (`seal` never throws `VaultVoidedException`: on a voided key it deletes it and seals with a fresh one), plus `class VaultLockedException : Exception()` (the user hasn't unlocked recently) and `class VaultVoidedException : Exception()` (the key is gone for good).
- `KeystoreTokenVault(keyguard: KeyguardManager) : TokenVault`: move the AES-GCM code out of `KeystoreSettingsRepository`. Plain alias stays `paperless-token` (so existing installs still decrypt). Locked alias `paperless-token-locked`, built with `setUserAuthenticationRequired(true)`; on API 30+ `setUserAuthenticationParameters(UNLOCK_SECONDS, AUTH_BIOMETRIC_STRONG or AUTH_DEVICE_CREDENTIAL)`, below that `setUserAuthenticationValidityDurationSeconds(UNLOCK_SECONDS)`, with `UNLOCK_SECONDS = 10`. Keep `setInvalidatedByBiometricEnrollment` at its default (true). Map `UserNotAuthenticatedException` → `VaultLockedException`. Map `KeyPermanentlyInvalidatedException` (thrown at `init`) → delete that alias, then throw `VaultVoidedException`. Expose `fun isDeviceSecure() = keyguard.isDeviceSecure`.
- `KeyValueStore.kt`: `interface KeyValueStore { fun getString(key: String): String?; fun getBoolean(key: String): Boolean?; fun edit(block: MutableMap<String, Any?>.() -> Unit) }` (a null value removes the key; `clear()` as `fun clear()`), and `class PrefsStore(prefs: SharedPreferences) : KeyValueStore`.

### [x] 1.4 — Data: the sealed settings repository

File: rename `.../data/KeystoreSettingsRepository.kt` → `.../data/SealedSettingsRepository.kt`, as `class SealedSettingsRepository(private val store: KeyValueStore, private val vault: TokenVault, private val deviceSecure: () -> Boolean) : SettingsRepository, AppLockRepository`.
Prefs keys: `url`, `token` (unchanged), `token_locked` (Boolean: which key sealed `token`; absent = false, i.e. old installs), `lock_enabled` (absent = true).
In memory: `private var unlocked: ServerConfig?` and `private var unsaved: Boolean`.
- `canLock() = deviceSecure()`; `isEnabled() = store.getBoolean("lock_enabled") ?: true`; `private fun active() = canLock() && isEnabled()`.
- `read()`: `unlocked` → Ready. No `url`/`token` → None. `token_locked` but no screen lock any more → clear → Voided (removing the screen lock voids the key). If `active()`, or `token_locked` is true → Locked (never touch the vault here: a recent screen unlock would otherwise open the time-limited key without the app's prompt). Else `vault.open(token, false)` → cache → Ready; `VaultVoidedException` or any other failure → clear → Voided.
- `unlock()`: if `unsaved` → seal `unlocked` with the key `active()` asks for, write it, `unsaved = false`, Ready. If `unlocked` is already set → Ready. If nothing is stored → None. Else `vault.open(token, token_locked)`. Then if `token_locked != active()`, re-seal with the right key and delete the old one (migration and screen-lock changes); if that re-seal is refused, keep the old seal and still return Ready. Cache → Ready. `VaultLockedException` → Locked. `VaultVoidedException` → clear → Voided.
- `save(config)`: `unlocked = config`. Try `vault.seal(token, active())` and write `url`, `token`, `token_locked`. On `VaultLockedException`, write only `url`, remove `token`, and set `unsaved = true` (the gate's Protect step seals it).
- `setEnabled(enabled)`: if `unlocked` is set and not `unsaved`, re-seal it with `canLock() && enabled` and write `token`/`token_locked`. `VaultLockedException` → keep the old seal and rethrow as `IllegalStateException("Unlock first")`. Then write `lock_enabled` and `vault.delete` the key no longer used.
- `hasUnsavedSignIn() = unsaved` (moved here from 2.2: the save tests need it).
- `forget()`: if `active()` and not `unsaved`, `unlocked = null`.
- `clear()`: `store.clear()` (keeps nothing; `lock_enabled` resets to default), `unlocked = null`, `unsaved = false`, `vault.delete(true)`.

File: `.../AppContainer.kt`: `private val sealed = SealedSettingsRepository(PrefsStore(context.getSharedPreferences("server", MODE_PRIVATE)), KeystoreTokenVault(context.getSystemService(KeyguardManager::class.java)), ...isDeviceSecure)`; `val settings: SettingsRepository = sealed`; `val lock: AppLockRepository = sealed`.
File: `app/build.gradle.kts` kover excludes: replace `*.data.KeystoreSettingsRepository*` with `*.data.KeystoreTokenVault*` and `*.data.PrefsStore*`. `SealedSettingsRepository` **is** counted.

### [x] 1.5 — Tests

Files: `app/src/test/java/.../data/SealedSettingsRepositoryTest.kt` (new), `app/src/test/java/.../Fakes.kt`
- Fakes: `FakeVault : TokenVault` (seals as `"<locked|plain>:<plain>"`; `lockedUntilUnlock: Boolean` makes locked seal/open throw `VaultLockedException`; `voided: Boolean` throws `VaultVoidedException`; records `deleted`). `MapStore : KeyValueStore`. Make `FakeSettings` implement `read()` (Ready/None from `config`) so existing tests compile unchanged.
- Cover every bullet in the Test plan's `SealedSettingsRepository` line.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*SealedSettingsRepositoryTest' --tests '*LoginUseCaseTest' --tests '*HomeViewModelTest'`
- `./build.sh ./gradlew --console=plain assembleDebug` compiles.

---

## Checkpoint 2 — Decide when the app is locked · commit: `feat(auth): lock the app again after a minute away`

Goal: the domain policy for the timeout and a `GateViewModel` that turns the stored sign-in and the lifecycle into a single screen state, all unit-tested.

### [ ] 2.1 — Domain: `AppLock`

File (new): `app/src/main/java/.../domain/usecase/AppLock.kt`
```kotlin
class AppLock(
    private val lock: AppLockRepository,
    private val now: () -> Long,          // millis, e.g. SystemClock::elapsedRealtime
    private val timeout: Duration = 1.minutes,
) {
    val isActive: Boolean get() = lock.canLock() && lock.isEnabled()
    fun onBackground()                     // remembers now()
    /** True (and the token forgotten) when the app was away for timeout or longer while the lock is active. */
    fun onForeground(): Boolean
}
```

### [ ] 2.2 — UI: `GateViewModel`

File (new): `app/src/main/java/.../ui/lock/GateViewModel.kt`
```kotlin
sealed interface GateState {
    data class SignIn(val notice: String? = null) : GateState
    /** Ask for the unlock. [overlay]: Home stays composed underneath (a re-lock), [protect]: sealing a fresh sign-in. */
    data class Locked(val overlay: Boolean = false, val protect: Boolean = false, val message: String? = null) : GateState
    data class Home(val config: ServerConfig, val lockAvailable: Boolean, val lockEnabled: Boolean) : GateState
}
class GateViewModel(private val settings: SettingsRepository, private val lock: AppLockRepository, private val appLock: AppLock) : ViewModel()
```
`state: StateFlow<GateState>`. Functions: `onUnlocked()` (calls `lock.unlock()`: Ready → Home, Locked → stays Locked with message "Unlock with your PIN or pattern", Voided → SignIn(VOIDED_NOTICE), None → SignIn()), `onUnlockFailed(message: String?)` (stays Locked, shows message), `onSignedIn(config)` (lock active and `read()` not Ready-and-saved → Locked(protect = true); else Home), `onBackground()` / `onForeground()` (Home → Locked(overlay = true) when `appLock.onForeground()`), `setLockEnabled(enabled)` (called after a successful prompt; updates Home), `onSignedOut()` → SignIn().
`VOIDED_NOTICE = "Your fingerprints or screen lock changed, so sign in again."`
Initial state from `settings.read()`: None → SignIn(), Voided → SignIn(VOIDED_NOTICE), Locked → Locked(), Ready → Home.
To tell a protect-pending save from a sealed one, add `fun hasUnsavedSignIn(): Boolean` to `AppLockRepository` (true while `unsaved`), implemented in `SealedSettingsRepository` and the fake.

### [ ] 2.3 — Tests

Files (new): `app/src/test/java/.../domain/AppLockTest.kt`, `app/src/test/java/.../ui/GateViewModelTest.kt`. Add `FakeLock : AppLockRepository` to `Fakes.kt` (scripted `canLock`, `enabled`, `unlockResult`, counts `forget()` calls). Cover the Test plan's `AppLock` and `GateViewModel` lines.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*AppLockTest' --tests '*GateViewModelTest' --tests '*SealedSettingsRepositoryTest'`

---

## Checkpoint 3 — Ask for the fingerprint · commit: `feat(ui): unlock the app with a fingerprint`

Goal: the app asks for the fingerprint at launch, after a minute away and before a shared upload. The switch is in the top-bar menu. The full CI command is green.

Follow the `design-recipe` skill for the lock screen and the menu.

### [ ] 3.1 — Dependencies

Files: `gradle/libs.versions.toml`, `app/build.gradle.kts`. Add `biometric = "1.1.0"` (`androidx.biometric:biometric`), `fragment = "1.8.9"` (`androidx.fragment:fragment-ktx`) and `implementation` them. If `Icons.Default.MoreVert` isn't on the classpath through material3, add `androidx.compose.material:material-icons-core` (BOM-managed).

### [ ] 3.2 — The prompt

File (new): `app/src/main/java/.../ui/lock/UnlockPrompt.kt`
`fun FragmentActivity.askToUnlock(title: String, onSuccess: () -> Unit, onFailure: (String?) -> Unit)`. Authenticators: API 30+ `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`; API 26–29 `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` (the only combo with a credential fallback androidx supports there). No negative button (device credential provides it). `ERROR_USER_CANCELED` / `ERROR_CANCELED` / `ERROR_NEGATIVE_BUTTON` → `onFailure(null)`; other errors → `onFailure(errString)`. `onAuthenticationFailed` (one bad finger) is ignored: the prompt keeps going.

### [ ] 3.3 — The lock screen

File (new): `app/src/main/java/.../ui/lock/LockScreen.kt`
`@Composable fun LockScreen(protect: Boolean, message: String?, onUnlock: () -> Unit)`. Opaque full-screen on the page ground, consumes all touches. App name; one line: "Unlock to see your documents" (or "Confirm it's you to keep this sign-in" when `protect`); `message` beneath in secondary text; one filled primary "Unlock" button (≥ 48dp). Asks once automatically on first composition (`LaunchedEffect(Unit) { onUnlock() }`).

### [ ] 3.4 — MainActivity: the gate

File: `app/src/main/java/.../MainActivity.kt`
- `ComponentActivity` → `androidx.fragment.app.FragmentActivity`.
- `GateViewModel` through a new `container.gateViewModelFactory` (`AppContainer`: `AppLock(lock, SystemClock::elapsedRealtime)`).
- A `LifecycleEventObserver` on the activity: `ON_STOP` → `gate.onBackground()`, `ON_START` → `gate.onForeground()`. Leaving for the device-credential screen or the scanner and coming back within 1 minute doesn't re-lock.
- Rename `shared` → `pending` (the files waiting for an unlocked Home: shared, scanned, photographed, picked). `pending` is passed to Home only when the state is `Home`; otherwise Home gets `emptyList()`.
- Render: `SignIn` → `LoginScreen(notice = …)`. `Locked(overlay = false)` → `LockScreen`. `Home` or `Locked(overlay = true)` → `HomeScreen` (keyed by `baseUrl`, last known config held in the activity), with `LockScreen` drawn over it in a `Box` when locked.
- `onUnlock` → `askToUnlock(...)` → `gate.onUnlocked()` / `gate.onUnlockFailed(it)`.
- Lock switch → `askToUnlock` → `gate.setLockEnabled(it)`.

### [ ] 3.5 — HomeScreen and LoginScreen

Files: `.../ui/home/HomeScreen.kt`, `.../ui/login/LoginScreen.kt`
- HomeScreen: the scan / photo / file results call a new `onDocuments: (List<DocumentSource>) -> Unit` (MainActivity appends them to `pending`) instead of `viewModel.upload` directly; `shared` → `pending`. New params `lockAvailable: Boolean`, `lockEnabled: Boolean`, `onLockChange: (Boolean) -> Unit`. Top bar: keep "Sign out"; add an icon button (MoreVert, content description "More options") opening a `DropdownMenu` with one item "Lock with fingerprint" and a trailing `Switch`, shown only when `lockAvailable`. Sign out also calls `gate.onSignedOut()` via `onSignedOut`.
- LoginScreen: new `notice: String?` param, shown above the form in the warning colour when set.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest koverVerify lintDebug assembleDebug`
- Install on a phone (`./build.sh install`): launch → prompt; PIN fallback; cancel → lock screen with Unlock; background > 1 min → prompt; share a PDF while locked → prompt, then upload; switch off → no prompt next launch.

---

## Out of scope

- Hiding the app in the recents thumbnail (`FLAG_SECURE`) or blocking screenshots.
- A choice of timeout, or a full Settings screen (#6 decides where settings live).
- Locking behind fingerprint only (without the PIN/pattern fallback).
- Any change to how sign-in, uploads or the Paperless API work.
