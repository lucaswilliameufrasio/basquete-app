package com.example.basqueteapp

import android.content.SharedPreferences

internal class Favorites(private val preferences: SharedPreferences) {
    fun load(): Map<String, String> {
        return preferences.all.mapNotNull { (key, value) ->
            if (value is String) {
                key to value
            } else {
                null
            }
        }.toMap()
    }

    fun toggle(id: String?, name: String): Map<String, String> {
        if (id.isNullOrBlank() || id == "0") {
            return load()
        }
        val editor = preferences.edit()
        if (preferences.contains(id)) {
            editor.remove(id)
        } else {
            editor.putString(id, name)
        }
        editor.apply()
        return load()
    }
}
