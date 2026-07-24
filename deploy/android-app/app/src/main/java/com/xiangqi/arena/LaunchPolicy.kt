package com.xiangqi.arena

object LaunchPolicy {
    fun resolve(
        isDebug: Boolean,
        savedOverride: GameEndpoint?,
        oneShotDebugUrl: String? = null,
    ): GameEndpoint {
        if (!isDebug) return GameEndpoint.Production
        return GameEndpoint.customOrNull(oneShotDebugUrl.orEmpty())
            ?: savedOverride
            ?: GameEndpoint.Production
    }
}
