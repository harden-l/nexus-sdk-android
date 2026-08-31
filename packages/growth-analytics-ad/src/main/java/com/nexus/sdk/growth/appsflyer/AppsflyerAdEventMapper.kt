package com.nexus.sdk.growth.appsflyer

import com.appsflyer.AFInAppEventParameterName
import com.nexus.sdk.growth.event_router.AnalyticsEvent

object AppsflyerAdEventMapper {
    internal const val EVENT_AD_IMPRESSION = "ad_imp"

    fun map(event: AnalyticsEvent): AppsflyerAnalyticsEvent? {
        if (event.eventName != "ad_revenue") {
            return null
        }

        return AppsflyerAnalyticsEvent(
            name = EVENT_AD_IMPRESSION,
            params = mapOf(
                AFInAppEventParameterName.CURRENCY to event.params["currency"],
                AFInAppEventParameterName.REVENUE to event.params["revenue"],
                AFInAppEventParameterName.AD_REVENUE_AD_TYPE to event.params["ad_format"],
                AFInAppEventParameterName.AD_REVENUE_PLACEMENT_ID to firstString(
                    event.params,
                    "placement",
                    "ad_unit_id"
                ),
                AFInAppEventParameterName.AD_REVENUE_NETWORK_NAME to firstString(
                    event.params,
                    "network_name",
                    "ad_platform"
                ),
                AFInAppEventParameterName.AD_REVENUE_MEDIATED_NETWORK_NAME to event.params["mediation_platform"]
            ).filterValues { it != null && it.toString().isNotBlank() }
        )
    }

    private fun firstString(params: Map<String, Any?>, vararg keys: String): String {
        return keys.firstNotNullOfOrNull { key ->
            params[key]?.toString()?.takeIf { it.isNotBlank() }
        }.orEmpty()
    }
}
