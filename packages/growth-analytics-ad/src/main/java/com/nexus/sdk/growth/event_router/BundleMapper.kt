package com.nexus.sdk.growth.event_router

import android.os.Bundle
import com.nexus.sdk.growth.event_router.AnalyticsEvent

internal object BundleMapper {
    fun toBundle(event: AnalyticsEvent): Bundle {
        val bundle = Bundle()
        bundle.putString("product_id", event.productId)
        bundle.putString("platform", event.platform)
        event.uid?.let { bundle.putString("user_id", it) }
        event.deviceId?.let { bundle.putString("device_id", it) }
        bundle.putLong("timestamp", event.timestamp)
        event.params.forEach { (key, value) ->
            when (value) {
                null -> Unit
                is String -> bundle.putString(key, value)
                is Int -> bundle.putInt(key, value)
                is Long -> bundle.putLong(key, value)
                is Float -> bundle.putFloat(key, value)
                is Double -> bundle.putDouble(key, value)
                is Boolean -> bundle.putString(key, value.toString())
                else -> bundle.putString(key, value.toString())
            }
        }
        return bundle
    }
}
