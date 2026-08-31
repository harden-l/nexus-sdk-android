package com.nexus.sdk.crosspromo.deeplink

import com.nexus.sdk.crosspromo.placement.CrossPromoLinkResult
import java.net.URLDecoder

object CrossPromoLinkParser {
    fun parse(url: String): CrossPromoLinkResult {
        val params = queryParams(url)
        return CrossPromoLinkResult(
            clickId = params.firstValue("click_id", "clickId"),
            sourceProductId = params.firstValue("source_product_id", "source", "src"),
            targetProductId = params.firstValue("target_product_id", "target", "dst"),
            placement = params.firstValue("placement", "entrance"),
            campaign = params.firstValue("campaign", "utm_campaign"),
            sourceUid = params.firstValue("source_uid", "uid"),
            sourceDeviceId = params.firstValue("source_device_id", "device_id"),
            rawUrl = url
        )
    }

    private fun queryParams(url: String): Map<String, String> {
        val query = url.substringAfter('?', missingDelimiterValue = "")
            .substringBefore('#')
        if (query.isBlank()) return emptyMap()
        return query.split('&')
            .mapNotNull { pair ->
                val key = pair.substringBefore('=').decodeUrl()
                val value = pair.substringAfter('=', missingDelimiterValue = "").decodeUrl()
                key.takeIf { it.isNotBlank() }?.let { it to value }
            }
            .toMap()
    }

    private fun Map<String, String>.firstValue(vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key -> get(key)?.takeIf { it.isNotBlank() } }
    }

    private fun String.decodeUrl(): String {
        return URLDecoder.decode(this, "UTF-8")
    }
}
