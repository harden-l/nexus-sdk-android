package com.nexus.sdk.growth.attribution

import com.nexus.sdk.growth.attribution.DeepLinkParser
import org.junit.Assert.assertEquals
import org.junit.Test

class DeepLinkParserTest {
    @Test
    fun parsesOneLinkParameters() {
        val result = DeepLinkParser.parse(
            "https://example.onelink.me/demo?pid=facebook&c=spring&deep_link_value=home&target=nexus://home&utm_medium=cpc"
        )

        assertEquals("facebook", result.source)
        assertEquals("spring", result.campaign)
        assertEquals("cpc", result.medium)
        assertEquals("home", result.deepLinkValue)
        assertEquals("nexus://home", result.target)
    }
}
