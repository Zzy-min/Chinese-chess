package com.xiangqi.arena

import android.content.Context

class DeveloperSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("qingqiju_developer", Context.MODE_PRIVATE)

    fun selectedEndpoint(): GameEndpoint? {
        return GameEndpoint.customOrNull(preferences.getString(KEY_ENDPOINT, "").orEmpty())
    }

    fun save(endpoint: GameEndpoint?) {
        preferences.edit().apply {
            if (endpoint == null) remove(KEY_ENDPOINT) else putString(KEY_ENDPOINT, endpoint.url)
        }.apply()
    }

    private companion object {
        const val KEY_ENDPOINT = "endpoint"
    }
}
