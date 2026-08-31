package com.nexus.sdk.growth.attribution

import com.appsflyer.deeplink.DeepLink

internal object AttributionMapper {
    fun fromConversionData(data: Map<String, Any>): AttributionData {
        val params = data.mapValues { it.value.toString() }
        return AttributionData(
            source = first(params, "pid", "media_source", "af_channel"),
            campaign = first(params, "c", "campaign"),
            medium = first(params, "utm_medium"),
            channel = first(params, "af_channel"),
            adset = first(params, "af_adset", "adset"),
            ad = first(params, "af_ad", "ad"),
            afStatus = first(params, "af_status"),
            mediaSource = first(params, "media_source", "pid"),
            deepLinkValue = first(params, "deep_link_value"),
            target = first(params, "target", "af_dp", "af_web_dp"),
            rawParams = params
        )
    }

    fun fromDeepLink(deepLink: DeepLink): DeepLinkResult {
        val params = deepLink.getClickEvent()?.toStringMap().orEmpty()
        val source = deepLink.mediaSource ?: first(params, "pid", "media_source")
        val campaign = deepLink.campaign ?: first(params, "c", "campaign")
        val deepLinkValue = deepLink.deepLinkValue ?: first(params, "deep_link_value")
        val target = deepLink.getStringValue("target")
            ?: deepLink.getStringValue("af_dp")
            ?: deepLink.getStringValue("af_web_dp")
        return DeepLinkResult(
            url = params["original_url"].orEmpty(),
            source = source,
            campaign = campaign,
            target = target,
            deepLinkValue = deepLinkValue,
            params = params
        )
    }

    fun fromDeepLinkResult(result: DeepLinkResult): AttributionData {
        return AttributionData(
            source = result.source,
            campaign = result.campaign,
            medium = result.medium,
            mediaSource = result.source,
            deepLinkValue = result.deepLinkValue,
            target = result.target,
            rawParams = result.params,
            timestamp = result.timestamp
        )
    }

    private fun first(params: Map<String, String>, vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key -> params[key]?.takeIf { it.isNotBlank() } }
    }
}

private fun org.json.JSONObject.toStringMap(): Map<String, String> {
    return keys().asSequence().associateWith { key -> optString(key) }
}
