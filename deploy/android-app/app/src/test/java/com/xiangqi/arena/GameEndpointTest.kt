package com.xiangqi.arena

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEndpointTest {
    @Test
    fun productionPointsToOnlineSite() {
        assertEquals(
            "https://www.xiangqiarena.com/online",
            GameEndpoint.Production.url,
        )
    }

    @Test
    fun customAcceptsHttpAndHttpsOnly() {
        assertEquals(
            "https://example.com/game",
            GameEndpoint.customOrNull("  https://example.com/game  ")?.url,
        )
        assertEquals(
            "http://192.168.1.8:18388/online",
            GameEndpoint.customOrNull("http://192.168.1.8:18388/online")?.url,
        )
        assertNull(GameEndpoint.customOrNull("javascript:alert(1)"))
        assertNull(GameEndpoint.customOrNull("file:///sdcard/test.html"))
        assertNull(GameEndpoint.customOrNull("   "))
    }

    @Test
    fun releaseAlwaysStartsProductionWhileDebugCanUseSavedOverride() {
        assertEquals(
            GameEndpoint.Production,
            LaunchPolicy.resolve(isDebug = false, savedOverride = GameEndpoint.Emulator),
        )
        assertEquals(
            GameEndpoint.Emulator,
            LaunchPolicy.resolve(isDebug = true, savedOverride = GameEndpoint.Emulator),
        )
        assertEquals(
            GameEndpoint.Production,
            LaunchPolicy.resolve(isDebug = true, savedOverride = null),
        )
        assertEquals(
            GameEndpoint.customOrNull("http://127.0.0.1:18388/online"),
            LaunchPolicy.resolve(
                isDebug = true,
                savedOverride = GameEndpoint.Emulator,
                oneShotDebugUrl = "http://127.0.0.1:18388/online",
            ),
        )
        assertEquals(
            GameEndpoint.Production,
            LaunchPolicy.resolve(
                isDebug = false,
                savedOverride = GameEndpoint.Emulator,
                oneShotDebugUrl = "http://127.0.0.1:18388/online",
            ),
        )
    }

    @Test
    fun nativeBridgeAcceptsOnlyTheExactConfiguredOrigin() {
        assertTrue(GameEndpoint.Production.allows("https://www.xiangqiarena.com/online#/home"))
        assertFalse(GameEndpoint.Production.allows("https://xiangqiarena.com/online"))
        assertFalse(GameEndpoint.Production.allows("https://www.xiangqiarena.com.evil.example/online"))
        assertFalse(GameEndpoint.Production.allows("https://www.xiangqiarena.com:8443/online"))
    }
}
