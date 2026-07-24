package com.xiangqi.arena

import java.net.URI

@JvmInline
value class GameEndpoint private constructor(val url: String) {
    val origin: String
        get() {
            val uri = URI(url)
            val explicitPort = if (uri.port == -1) "" else ":${uri.port}"
            return "${uri.scheme}://${uri.host}$explicitPort"
        }

    fun allows(candidate: String): Boolean {
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return false
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return false
        val explicitPort = if (uri.port == -1) "" else ":${uri.port}"
        return "${uri.scheme}://${uri.host}$explicitPort" == origin
    }

    companion object {
        val Production = GameEndpoint("https://www.xiangqiarena.com/online")
        val Emulator = GameEndpoint("http://10.0.2.2:18388/online/index.html")
        val DevicePractice = GameEndpoint("http://127.0.0.1:18388/online/index.html#/practice")
        val DeviceHome = GameEndpoint("http://127.0.0.1:18388/online/index.html#/home")

        fun customOrNull(value: String): GameEndpoint? {
            val normalized = value.trim()
            if (normalized.isEmpty()) return null

            val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
            if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
            return GameEndpoint(normalized)
        }
    }
}
