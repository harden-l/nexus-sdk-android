package com.nexus.sdk.growth.event_router

interface AnalyticsProvider {
    val name: String

    fun track(event: AnalyticsEvent)

    fun flush()
}
