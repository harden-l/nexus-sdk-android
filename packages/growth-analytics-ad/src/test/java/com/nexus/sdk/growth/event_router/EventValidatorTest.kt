package com.nexus.sdk.growth.event_router

import com.nexus.sdk.growth.event_router.AnalyticsEvent
import com.nexus.sdk.growth.event_router.EventValidator
import org.junit.Test

class EventValidatorTest {
    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankEventName() {
        EventValidator.validate(
            AnalyticsEvent(
                eventName = "",
                productId = "product_a",
                platform = "android",
                timestamp = 1L
            )
        )
    }
}
