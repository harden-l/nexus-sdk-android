package com.nexus.sdk.growth.event_router

internal object EventValidator {
    fun validate(event: AnalyticsEvent) {
        require(event.eventName.isNotBlank()) { "eventName is required" }
        require(event.productId.isNotBlank()) { "productId is required" }
        require(event.platform.isNotBlank()) { "platform is required" }
        when (event.eventName) {
            "ad_load",
            "ad_inventory",
            "ad_click" -> validateAdLifecycleEvent(event)
            "ad_revenue" -> validateAdRevenueEvent(event)
            "purchase_revenue" -> validatePurchaseRevenueEvent(event)
        }
    }

    private fun validateAdLifecycleEvent(event: AnalyticsEvent) {
        requireString(event, "ad_unit_id")
        requireString(event, "ad_format")
    }

    private fun validateAdRevenueEvent(event: AnalyticsEvent) {
        requireString(event, "revenue_type")
        requireString(event, "ad_platform")
        requireString(event, "ad_unit_id")
        requireString(event, "ad_format")
        requireString(event, "currency")
        requireNumber(event, "revenue")
    }

    private fun validatePurchaseRevenueEvent(event: AnalyticsEvent) {
        requireString(event, "revenue_type")
        requireString(event, "payment_channel")
        requireString(event, "order_id")
        requireString(event, "store_product_id")
        requireString(event, "purchase_type")
        requireString(event, "currency")
        requireNumber(event, "revenue")
    }

    private fun requireString(event: AnalyticsEvent, key: String) {
        val value = event.params[key]
        require(value is String && value.isNotBlank()) { "$key is required for ${event.eventName}" }
    }

    private fun requireNumber(event: AnalyticsEvent, key: String) {
        val value = event.params[key]
        val number = when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
        require(number != null && number >= 0.0) { "$key must be a non-negative number for ${event.eventName}" }
    }
}
