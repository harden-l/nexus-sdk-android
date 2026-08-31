package com.nexus.sdk.growth.event_router

import com.nexus.sdk.growth.appsflyer.AppsflyerAdEventMapper
import com.nexus.sdk.growth.firebase.FirebaseAdEventMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class AdImpressionEventNameTest {
    @Test
    fun firebaseUsesAdImpEventName() {
        assertEquals("ad_imp", FirebaseAdEventMapper.EVENT_AD_IMPRESSION)
    }

    @Test
    fun appsFlyerUsesAdImpEventName() {
        assertEquals("ad_imp", AppsflyerAdEventMapper.EVENT_AD_IMPRESSION)
    }
}
