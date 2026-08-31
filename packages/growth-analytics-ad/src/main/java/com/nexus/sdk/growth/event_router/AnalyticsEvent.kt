package com.nexus.sdk.growth.event_router

data class AnalyticsEvent(
    val eventName: String,
    val uid: String? = null,
    val deviceId: String? = null,
    val productId: String,
    val platform: String,
    val timestamp: Long,
    val params: Map<String, Any?> = emptyMap()
)
