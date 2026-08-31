package com.nexus.sdk.growth.bi

import android.content.Context
import android.util.Log
import cn.dataeye.android.DataEyeAnalyticsSDK
import com.nexus.sdk.growth.event_router.AnalyticsEvent
import com.nexus.sdk.growth.event_router.AnalyticsProvider
import com.nexus.sdk.growth.bi.BIEventMapper
import org.json.JSONObject

internal class DataEyeAnalyticsProvider(
    context: Context,
    appId: String,
    serverUrl: String?
) : AnalyticsProvider {
    private companion object {
        const val TAG = "DataEyeAnalyticsProvider"
    }

    override val name: String = "dataeye"
    private val dataEye =
        DataEyeAnalyticsSDK.sharedInstance(context.applicationContext, appId, serverUrl)
    private var userProperties: Map<String, Any?> = emptyMap()

    override fun track(event: AnalyticsEvent) {
        event.uid?.let { dataEye?.login(it) }
        val biEvent = BIEventMapper.map(event)
        Log.d(TAG, "track event=${event.eventName}, biEvent=${biEvent.name}, params=${biEvent.params}")
        dataEye?.track(
            biEvent.name,
            JSONObject((userProperties + biEvent.params).filterValues { it != null })
        )
    }

    fun setUserProperties(properties: Map<String, Any?>) {
        userProperties = properties
    }

    override fun flush() = Unit
}
