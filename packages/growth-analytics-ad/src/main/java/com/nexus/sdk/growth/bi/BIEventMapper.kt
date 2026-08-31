package com.nexus.sdk.growth.bi

import com.nexus.sdk.growth.event_router.AnalyticsEvent

object BIEventMapper {
    fun map(event: AnalyticsEvent): BIEyeEvent {
        return when (event.eventName) {
            "ad_load" -> adEvent("ad_request", event)
            "ad_inventory" -> adEvent("ad_inventory", event)
            "ad_revenue" -> adImpEvent(event)
            "ad_click" -> adEvent("ad_click", event)
            "purchase_order" -> purchaseEvent(event, "order")
            "purchase_paid" -> purchaseEvent(event, "paid")
            "purchase_paid_err" -> purchaseEvent(event, "paid_err")
            "purchase_revenue" -> purchaseRevenueEvent(event)
            else -> BIEyeEvent(event.eventName, event.params)
        }
    }

    private fun adEvent(name: String, event: AnalyticsEvent): BIEyeEvent {
        return BIEyeEvent(
            name = name,
            params = mapOf(
                "ad_type" to adType(event.params["ad_format"]),
                "PlacementId" to placementId(event),
                "NetworkFirmId" to networkFirmId(event)
            )
        )
    }

    private fun adImpEvent(event: AnalyticsEvent): BIEyeEvent {
        val revenue = event.params["revenue"].asDouble() ?: 0.0
        return BIEyeEvent(
            name = "ad_imp",
            params = mapOf(
                "ad_type" to adType(event.params["ad_format"]),
                "Ecpm" to event.params["ecpm"].asDouble(),
                "Revenue" to revenue,
                "Currency" to event.params["currency"].asString(),
                "PlacementId" to placementId(event),
                "scene" to event.params["scene"].asString().ifBlank { placementId(event) },
                "NetworkFirmId" to networkFirmId(event)
            )
        )
    }

    private fun purchaseRevenueEvent(event: AnalyticsEvent): BIEyeEvent {
        return purchaseEvent(event, "paid")
    }

    private fun purchaseEvent(event: AnalyticsEvent, status: String): BIEyeEvent {
        return BIEyeEvent(
            name = "purchase",
            params = mapOf(
                "item_id" to firstString(event.params, "item_id", "store_product_id"),
                "item_name" to firstString(event.params, "item_name", "store_product_id"),
                "revenue" to event.params["revenue"].asDouble(),
                "currency" to event.params["currency"].asString(),
                "order_num" to firstString(event.params, "order_num", "order_id"),
                "purchase_status" to event.params["purchase_status"].asString().ifBlank { status },
                "msg" to firstString(event.params, "msg", "error_msg")
            )
        )
    }

    private fun placementId(event: AnalyticsEvent): String {
        return firstString(event.params, "PlacementId", "placement", "ad_unit_id")
    }

    private fun networkFirmId(event: AnalyticsEvent): String {
        return firstString(
            event.params,
            "network_firm_id",
            "ad_platform_id",
            "ad_source_id",
            "ad_source_name",
            "ad_platform_id",
            "ad_platform"
        )
            .ifBlank { "admob" }
    }

    private fun adType(value: Any?): String {
        return when (value.asString()) {
            "native" -> "Native"
            "rewarded" -> "RewardedVideo"
            "banner" -> "Banner"
            "interstitial" -> "Interstitial"
            "app_open" -> "Splash"
            else -> value.asString()
        }
    }

    private fun firstString(params: Map<String, Any?>, vararg keys: String): String {
        return keys.firstNotNullOfOrNull { key ->
            params[key].asString().takeIf { it.isNotBlank() }
        }.orEmpty()
    }

    private fun Any?.asString(): String {
        return when (this) {
            null -> ""
            is String -> this
            else -> toString()
        }
    }

    private fun Any?.asDouble(): Double? {
        return when (this) {
            is Number -> toDouble()
            is String -> toDoubleOrNull()
            else -> null
        }
    }
}
