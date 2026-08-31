package com.nexus.sdk.growth.attribution

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object DeepLinkParser {
    fun parse(url: String): DeepLinkResult {
        val params = parseQuery(url.substringAfter("?", missingDelimiterValue = ""))
        val source = first(params, "source", "utm_source", "pid", "media_source")
        val campaign = first(params, "campaign", "utm_campaign", "c")
        val medium = first(params, "medium", "utm_medium")
        val deepLinkValue = first(params, "deep_link_value", "deepLinkValue")
        val target = first(params, "target", "af_dp", "af_web_dp", "deep_link_sub1")
        return DeepLinkResult(
            url = url,
            source = source,
            campaign = campaign,
            medium = medium,
            target = target,
            deepLinkValue = deepLinkValue,
            params = params
        )
    }

    private fun first(params: Map<String, String>, vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key -> params[key]?.takeIf { it.isNotBlank() } }
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.substringBefore("#")
            .split("&")
            .filter { it.isNotBlank() }
            .associate { pair ->
                val key = decode(pair.substringBefore("="))
                val value = decode(pair.substringAfter("=", missingDelimiterValue = ""))
                key to value
            }
    }

    private fun decode(value: String): String {
        return URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }
}
