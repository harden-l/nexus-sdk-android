package com.nexus.sdk.growth.event_router

internal class EventRouter(
    private val providers: List<AnalyticsProvider>
) {
    fun track(event: AnalyticsEvent) {
        EventValidator.validate(event)
        providers.forEach { provider ->
            provider.track(event)
        }
    }

    fun flush() {
        providers.forEach { provider ->
            provider.flush()
        }
    }
}
