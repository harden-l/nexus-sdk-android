package com.nexus.sdk.crosspromo.deeplink

import org.junit.Assert.assertEquals
import org.junit.Test

class CrossPromoLinkParserTest {
    @Test
    fun parsesCrossPromoAttributionParams() {
        val result = CrossPromoLinkParser.parse(
            "nexus://promo?click_id=cp_1&source_product_id=app_a&target_product_id=app_b&placement=settings&campaign=internal_cross_promo&source_uid=u_1&source_device_id=d_1"
        )

        assertEquals("cp_1", result.clickId)
        assertEquals("app_a", result.sourceProductId)
        assertEquals("app_b", result.targetProductId)
        assertEquals("settings", result.placement)
        assertEquals("internal_cross_promo", result.campaign)
        assertEquals("u_1", result.sourceUid)
        assertEquals("d_1", result.sourceDeviceId)
    }

    @Test
    fun supportsShortParamAliases() {
        val result = CrossPromoLinkParser.parse(
            "nexus://promo?src=app_a&dst=app_b&entrance=home&utm_campaign=summer"
        )

        assertEquals("app_a", result.sourceProductId)
        assertEquals("app_b", result.targetProductId)
        assertEquals("home", result.placement)
        assertEquals("summer", result.campaign)
    }
}
