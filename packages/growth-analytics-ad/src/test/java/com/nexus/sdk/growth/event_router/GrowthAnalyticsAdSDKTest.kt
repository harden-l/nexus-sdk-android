package com.nexus.sdk.growth.event_router

import com.nexus.sdk.coreuser.silent_login.SDKUser
import com.nexus.sdk.growth.event_router.MockAnalyticsProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthAnalyticsAdSDKTest {
    @Test
    fun routesEventToAllProviders() {
        val bi = MockAnalyticsProvider("bi")
        val firebase = MockAnalyticsProvider("firebase")
        GrowthAnalyticsAdSDK.init(
            config = AnalyticsConfig(productId = "product_a"),
            providers = listOf(bi, firebase)
        )
        GrowthAnalyticsAdSDK.setUser(SDKUser(uid = "u_1", deviceId = "device_1"))

        val event = GrowthAnalyticsAdSDK.track("page_view", mapOf("page" to "home"))

        assertEquals("u_1", event.uid)
        assertEquals("device_1", event.deviceId)
        assertEquals(1, bi.snapshot().size)
        assertEquals(1, firebase.snapshot().size)
        assertEquals("home", bi.snapshot().first().params["page"])
    }

    @Test
    fun flushClearsMockProviders() {
        val bi = MockAnalyticsProvider("bi")
        GrowthAnalyticsAdSDK.init(
            config = AnalyticsConfig(productId = "product_a"),
            providers = listOf(bi)
        )

        GrowthAnalyticsAdSDK.track("click")
        assertTrue(bi.snapshot().isNotEmpty())

        GrowthAnalyticsAdSDK.flush()
        assertTrue(bi.snapshot().isEmpty())
    }
}
