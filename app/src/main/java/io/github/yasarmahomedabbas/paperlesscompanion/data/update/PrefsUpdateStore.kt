package io.github.yasarmahomedabbas.paperlesscompanion.data.update

import android.content.Context
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ApkFile
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.AppRelease
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.UpdateStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * [UpdateStore] in the `updates` SharedPreferences: the offer as JSON (unreadable reads as none),
 * the last version code announced, and whether the notification permission was asked for.
 */
class PrefsUpdateStore(context: Context) : UpdateStore {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val _offer = MutableStateFlow(prefs.getString(OFFER, null)?.let(::decode))
    override val offer: StateFlow<AppRelease?> = _offer.asStateFlow()

    override fun saveOffer(release: AppRelease?) {
        prefs.edit().apply { if (release == null) remove(OFFER) else putString(OFFER, encode(release)) }.apply()
        _offer.value = release
    }

    override fun notifiedVersionCode(): Long = prefs.getLong(NOTIFIED, Long.MIN_VALUE)

    override fun markNotified(versionCode: Long) {
        prefs.edit().putLong(NOTIFIED, versionCode).apply()
    }

    override fun askedForNotifications(): Boolean = prefs.getBoolean(ASKED, false)

    override fun markAskedForNotifications() {
        prefs.edit().putBoolean(ASKED, true).apply()
    }

    private fun encode(release: AppRelease): String = JsonObject(
        mapOf(
            "version" to JsonPrimitive(release.version),
            "versionCode" to JsonPrimitive(release.versionCode),
            "notes" to JsonPrimitive(release.notes),
            "apk" to JsonObject(
                mapOf(
                    "name" to JsonPrimitive(release.apk.name),
                    "size" to JsonPrimitive(release.apk.size),
                    "sha256" to JsonPrimitive(release.apk.sha256),
                    "location" to JsonPrimitive(release.apk.location),
                ),
            ),
        ),
    ).toString()

    private fun decode(text: String): AppRelease? = try {
        val json = Json.parseToJsonElement(text).jsonObject
        val apk = json.getValue("apk").jsonObject
        AppRelease(
            version = json.getValue("version").jsonPrimitive.content,
            versionCode = json.getValue("versionCode").jsonPrimitive.long,
            notes = json.getValue("notes").jsonPrimitive.content,
            apk = ApkFile(
                name = apk.getValue("name").jsonPrimitive.content,
                size = apk.getValue("size").jsonPrimitive.long,
                sha256 = apk.getValue("sha256").jsonPrimitive.content,
                location = apk.getValue("location").jsonPrimitive.content,
            ),
        )
    } catch (_: IllegalArgumentException) {
        null // includes SerializationException
    } catch (_: NoSuchElementException) {
        null
    }

    private companion object {
        const val OFFER = "offer"
        const val NOTIFIED = "notified"
        const val ASKED = "asked_notifications"
    }
}
