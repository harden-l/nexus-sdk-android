package com.nexus.sdk.growth.event_router

import android.util.Base64

internal object EventRecordCodec {
    fun encode(record: EventRecord): String {
        val payload = listOf(
            record.id,
            record.event.eventName,
            record.event.uid.orEmpty(),
            record.event.deviceId.orEmpty(),
            record.event.productId,
            record.event.platform,
            record.event.timestamp.toString(),
            record.event.params.entries.joinToString("&") { "${escape(it.key)}=${escape(it.value?.toString().orEmpty())}" }
        ).joinToString("|", transform = ::escape)
        return Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun decode(value: String): EventRecord? {
        return runCatching {
            val decoded = String(Base64.decode(value, Base64.NO_WRAP), Charsets.UTF_8)
            val parts = decoded.split("|").map(::unescape)
            val params = parts.getOrNull(7)
                ?.split("&")
                ?.filter { it.contains("=") }
                ?.associate {
                    val key = it.substringBefore("=")
                    val rawValue = it.substringAfter("=")
                    unescape(key) to unescape(rawValue)
                }
                .orEmpty()

            EventRecord(
                id = parts[0],
                event = AnalyticsEvent(
                    eventName = parts[1],
                    uid = parts.getOrNull(2)?.takeIf { it.isNotBlank() },
                    deviceId = parts.getOrNull(3)?.takeIf { it.isNotBlank() },
                    productId = parts[4],
                    platform = parts[5],
                    timestamp = parts[6].toLong(),
                    params = params
                )
            )
        }.getOrNull()
    }

    private fun escape(value: String): String {
        return value.replace("%", "%25").replace("|", "%7C").replace("&", "%26").replace("=", "%3D")
    }

    private fun unescape(value: String): String {
        return value.replace("%3D", "=").replace("%26", "&").replace("%7C", "|").replace("%25", "%")
    }
}
