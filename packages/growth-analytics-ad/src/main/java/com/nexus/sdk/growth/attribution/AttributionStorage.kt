package com.nexus.sdk.growth.attribution

import android.content.Context
import org.json.JSONObject

internal class AttributionStorage(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun saveInstallSource(data: AttributionData) {
        preferences.edit().putString(KEY_INSTALL_SOURCE, data.toJson().toString()).apply()
    }

    fun getInstallSource(): AttributionData? {
        return preferences.getString(KEY_INSTALL_SOURCE, null)?.let { AttributionDataJson.decode(it) }
    }

    fun saveDeepLink(result: DeepLinkResult) {
        preferences.edit().putString(KEY_LAST_DEEP_LINK, result.toJson().toString()).apply()
    }

    fun getLastDeepLink(): DeepLinkResult? {
        return preferences.getString(KEY_LAST_DEEP_LINK, null)?.let { DeepLinkResultJson.decode(it) }
    }

    private fun AttributionData.toJson(): JSONObject {
        return JSONObject()
            .put("source", source)
            .put("campaign", campaign)
            .put("medium", medium)
            .put("channel", channel)
            .put("adset", adset)
            .put("ad", ad)
            .put("afStatus", afStatus)
            .put("mediaSource", mediaSource)
            .put("deepLinkValue", deepLinkValue)
            .put("target", target)
            .put("rawParams", JSONObject(rawParams))
            .put("timestamp", timestamp)
    }

    private fun DeepLinkResult.toJson(): JSONObject {
        return JSONObject()
            .put("url", url)
            .put("source", source)
            .put("campaign", campaign)
            .put("medium", medium)
            .put("target", target)
            .put("deepLinkValue", deepLinkValue)
            .put("params", JSONObject(params))
            .put("timestamp", timestamp)
    }

    companion object {
        private const val NAME = "nexus_growth_attribution"
        private const val KEY_INSTALL_SOURCE = "install_source"
        private const val KEY_LAST_DEEP_LINK = "last_deep_link"
    }
}

private object AttributionDataJson {
    fun decode(raw: String): AttributionData {
        val json = JSONObject(raw)
        val rawParams = json.optJSONObject("rawParams").toStringMap()
        return AttributionData(
            source = json.optNullableString("source"),
            campaign = json.optNullableString("campaign"),
            medium = json.optNullableString("medium"),
            channel = json.optNullableString("channel"),
            adset = json.optNullableString("adset"),
            ad = json.optNullableString("ad"),
            afStatus = json.optNullableString("afStatus"),
            mediaSource = json.optNullableString("mediaSource"),
            deepLinkValue = json.optNullableString("deepLinkValue"),
            target = json.optNullableString("target"),
            rawParams = rawParams,
            timestamp = json.optLong("timestamp", System.currentTimeMillis())
        )
    }
}

private object DeepLinkResultJson {
    fun decode(raw: String): DeepLinkResult {
        val json = JSONObject(raw)
        return DeepLinkResult(
            url = json.optString("url"),
            source = json.optNullableString("source"),
            campaign = json.optNullableString("campaign"),
            medium = json.optNullableString("medium"),
            target = json.optNullableString("target"),
            deepLinkValue = json.optNullableString("deepLinkValue"),
            params = json.optJSONObject("params").toStringMap(),
            timestamp = json.optLong("timestamp", System.currentTimeMillis())
        )
    }
}

private fun JSONObject?.toStringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    return keys().asSequence().associateWith { key -> optString(key) }
}

private fun JSONObject.optNullableString(key: String): String? {
    return if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}
