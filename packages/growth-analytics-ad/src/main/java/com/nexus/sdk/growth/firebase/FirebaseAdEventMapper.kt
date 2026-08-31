package com.nexus.sdk.growth.firebase

import android.os.Bundle
import com.nexus.sdk.growth.event_router.AnalyticsEvent
import com.google.firebase.analytics.FirebaseAnalytics

object FirebaseAdEventMapper {
    internal const val EVENT_AD_IMPRESSION = "ad_imp"

    fun map(event: AnalyticsEvent): FirebaseAnalyticsEvent? {
        if (event.eventName != "ad_revenue") {
            return null
        }

        return FirebaseAnalyticsEvent(
            name = EVENT_AD_IMPRESSION,
            params = Bundle().apply {
                putString(
                    FirebaseAnalytics.Param.AD_SOURCE,
                    event.params["network_name"].asString()
                        .ifBlank { event.params["ad_platform"].asString() })
                putString(
                    FirebaseAnalytics.Param.AD_PLATFORM,
                    event.params["ad_platform"].asString()
                )
                putString(FirebaseAnalytics.Param.AD_FORMAT, event.params["ad_format"].asString())
                putString(
                    FirebaseAnalytics.Param.AD_UNIT_NAME,
                    event.params["ad_unit_id"].asString()
                )
                putString(FirebaseAnalytics.Param.CURRENCY, event.params["currency"].asString())
                event.params["revenue"].asDouble()
                    ?.let { putDouble(FirebaseAnalytics.Param.VALUE, it) }
            }
        )
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
