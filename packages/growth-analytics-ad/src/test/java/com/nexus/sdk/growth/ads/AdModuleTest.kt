package com.nexus.sdk.growth.ads

import com.nexus.sdk.growth.ads.AdFormat
import com.nexus.sdk.growth.ads.AdPlacement
import com.nexus.sdk.growth.ads.MockAdProvider
import com.nexus.sdk.growth.event_router.AnalyticsConfig
import com.nexus.sdk.growth.event_router.GrowthAnalyticsAdSDK
import com.nexus.sdk.growth.event_router.MockAnalyticsProvider
import com.nexus.sdk.growth.ad_revenue.AdRevenuePayload
import org.junit.Assert.assertEquals
import org.junit.Test

class AdModuleTest {
    @Test
    fun loadAndShowUseAdProvider() {
        val analyticsProvider = MockAnalyticsProvider("bi")
        val adProvider = MockAdProvider()
        val placement = AdPlacement(
            placement = "home_interstitial",
            adUnitId = "ca-app-pub-test",
            format = AdFormat.INTERSTITIAL
        )

        GrowthAnalyticsAdSDK.init(
            config = AnalyticsConfig(productId = "product_a"),
            providers = listOf(analyticsProvider),
            adProvider = adProvider
        )

        GrowthAnalyticsAdSDK.loadAd(placement)
        GrowthAnalyticsAdSDK.showAd(placement)

        assertEquals(2, adProvider.loadedPlacements.size)
        assertEquals(1, adProvider.shownPlacements.size)
        assertEquals(listOf("ad_load", "ad_show"), analyticsProvider.snapshot().map { it.eventName })
    }

    @Test
    fun showWithoutCacheLoadsForNextAttemptAndUsesFormatAndAdUnitCacheKey() {
        val adProvider = MockAdProvider()
        val firstPlacement = AdPlacement("screen_a", "shared-unit", AdFormat.INTERSTITIAL)
        val secondPlacement = AdPlacement("screen_b", "shared-unit", AdFormat.INTERSTITIAL)
        val callbacks = RecordingAdCallbacks()

        adProvider.showAd(firstPlacement, callbacks)
        adProvider.showAd(secondPlacement, callbacks)

        assertEquals(1, callbacks.failedCount)
        assertEquals(listOf("screen_b"), callbacks.shownPlacements)
        assertEquals(2, adProvider.loadedPlacements.size)
    }

    @Test
    fun reportsAdRevenueEvent() {
        val analyticsProvider = MockAnalyticsProvider("bi")
        GrowthAnalyticsAdSDK.init(
            config = AnalyticsConfig(productId = "product_a"),
            providers = listOf(analyticsProvider)
        )

        val event = GrowthAnalyticsAdSDK.reportAdRevenue(
            AdRevenuePayload(
                adPlatform = "admob",
                mediationPlatform = "admob_mediation",
                adUnitId = "ca-app-pub-test",
                placement = "home_interstitial",
                adFormat = AdFormat.INTERSTITIAL,
                currency = "USD",
                revenue = 0.0123,
                country = "US",
                networkName = "Meta Audience Network",
                precision = "estimated"
            )
        )

        assertEquals("ad_revenue", event.eventName)
        assertEquals("ad", event.params["revenue_type"])
        assertEquals(0.0123, event.params["revenue"])
        assertEquals(1, analyticsProvider.snapshot().size)
    }

    private class RecordingAdCallbacks : AdCallbacks {
        var failedCount = 0
        val shownPlacements = mutableListOf<String>()

        override fun onShown(placement: AdPlacement) {
            shownPlacements.add(placement.placement)
        }

        override fun onFailed(placement: AdPlacement, error: Throwable) {
            failedCount += 1
        }
    }
}
