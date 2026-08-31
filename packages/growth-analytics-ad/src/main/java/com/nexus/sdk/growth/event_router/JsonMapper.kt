package com.nexus.sdk.growth.event_router

import com.nexus.sdk.growth.event_router.AnalyticsEvent
import org.json.JSONObject

internal object JsonMapper {
    fun toJson(event: AnalyticsEvent): JSONObject {
        val json = JSONObject()
        json.put("product_id", event.productId)
        json.put("platform", event.platform)
        json.put("timestamp", event.timestamp)
        event.uid?.let { json.put("user_id", it) }
        event.deviceId?.let { json.put("device_id", it) }
        event.params.forEach { (key, value) ->
            json.put(key, value)
        }
        return json
    }
}
