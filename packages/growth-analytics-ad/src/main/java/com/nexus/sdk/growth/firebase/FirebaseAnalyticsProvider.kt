package com.nexus.sdk.growth.firebase

import android.content.Context
import com.nexus.sdk.growth.event_router.AnalyticsEvent
import com.nexus.sdk.growth.event_router.AnalyticsProvider
import com.nexus.sdk.growth.firebase.FirebaseAdEventMapper
import com.google.firebase.analytics.FirebaseAnalytics

internal class FirebaseAnalyticsProvider(
    context: Context
) : AnalyticsProvider {
    override val name: String = "firebase"
    private val firebaseAnalytics = FirebaseAnalytics.getInstance(context.applicationContext)

    override fun track(event: AnalyticsEvent) {
        event.uid?.let { firebaseAnalytics.setUserId(it) }
        val firebaseEvent = FirebaseAdEventMapper.map(event) ?: return
        firebaseAnalytics.logEvent(firebaseEvent.name, firebaseEvent.params)
    }

    fun setUserProperties(properties: Map<String, Any?>) {
        properties.forEach { (key, value) ->
            firebaseAnalytics.setUserProperty(key.take(24), value?.toString()?.take(36))
        }
    }

    override fun flush() = Unit
}
