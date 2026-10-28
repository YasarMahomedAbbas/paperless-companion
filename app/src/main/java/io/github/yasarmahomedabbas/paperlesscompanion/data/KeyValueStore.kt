package io.github.yasarmahomedabbas.paperlesscompanion.data

import android.content.SharedPreferences

/** A small key-value store, so the settings logic can run against a map in tests. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun getBoolean(key: String): Boolean?

    /** Applies the puts in [block] together. A null value removes the key. */
    fun edit(block: MutableMap<String, Any?>.() -> Unit)
    fun clear()
}

class PrefsStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun getBoolean(key: String): Boolean? = if (prefs.contains(key)) prefs.getBoolean(key, false) else null

    override fun edit(block: MutableMap<String, Any?>.() -> Unit) {
        val changes = mutableMapOf<String, Any?>().apply(block)
        val editor = prefs.edit()
        changes.forEach { (key, value) ->
            when (value) {
                null -> editor.remove(key)
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                else -> error("Unsupported value for $key")
            }
        }
        editor.apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}
