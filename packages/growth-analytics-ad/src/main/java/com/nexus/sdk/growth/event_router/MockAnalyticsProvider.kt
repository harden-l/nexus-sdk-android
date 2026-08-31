package com.nexus.sdk.growth.event_router

class MockAnalyticsProvider(
    override val name: String
) : AnalyticsProvider {
    private val events = mutableListOf<AnalyticsEvent>()

    override fun track(event: AnalyticsEvent) {
        events += event
    }

    override fun flush() {
        events.clear()
    }

    fun snapshot(): List<AnalyticsEvent> = events.toList()
}
