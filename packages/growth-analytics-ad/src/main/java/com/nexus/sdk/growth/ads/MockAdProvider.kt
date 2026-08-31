package com.nexus.sdk.growth.ads

class MockAdProvider(
    override val name: String = "mock_ad"
) : AdProvider {
    val loadedPlacements = mutableListOf<AdPlacement>()
    val shownPlacements = mutableListOf<AdPlacement>()
    private val loadedKeys = mutableSetOf<MockAdCacheKey>()

    override fun loadAd(placement: AdPlacement, callbacks: AdCallbacks?) {
        val key = placement.cacheKey()
        if (key in loadedKeys) {
            callbacks?.onLoaded(placement)
            return
        }
        loadedKeys.add(key)
        loadedPlacements.add(placement)
        callbacks?.onLoaded(placement)
    }

    override fun showAd(placement: AdPlacement, callbacks: AdCallbacks?) {
        if (!loadedKeys.remove(placement.cacheKey())) {
            loadAd(placement, callbacks = null)
            callbacks?.onFailed(placement, IllegalStateException("Ad is not loaded"))
            return
        }
        shownPlacements += placement
        callbacks?.onShown(placement)
        if (placement.format == AdFormat.REWARDED) {
            callbacks?.onReward(placement)
        }
        callbacks?.onClosed(placement)
        loadAd(placement, callbacks = null)
    }

    private fun AdPlacement.cacheKey() = MockAdCacheKey(format, adUnitId)

    private data class MockAdCacheKey(
        val format: AdFormat,
        val adUnitId: String
    )
}
