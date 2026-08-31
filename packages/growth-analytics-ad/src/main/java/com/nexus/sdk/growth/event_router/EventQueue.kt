package com.nexus.sdk.growth.event_router

import android.content.Context
import java.util.UUID

internal class EventQueue(
    context: Context,
    name: String,
    private val maxSize: Int
) {
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    @Synchronized
    fun enqueue(event: AnalyticsEvent) {
        val records = snapshot().toMutableList()
        records += EventRecord(UUID.randomUUID().toString(), event)
        val trimmed = records.takeLast(maxSize)
        save(trimmed)
    }

    @Synchronized
    fun snapshot(): List<EventRecord> {
        val raw = preferences.getString(KEY_EVENTS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence()
            .mapNotNull(EventRecordCodec::decode)
            .toList()
    }

    @Synchronized
    fun remove(ids: Collection<String>) {
        if (ids.isEmpty()) return
        save(snapshot().filterNot { it.id in ids })
    }

    private fun save(records: List<EventRecord>) {
        preferences.edit()
            .putString(KEY_EVENTS, records.joinToString("\n", transform = EventRecordCodec::encode))
            .apply()
    }

    companion object {
        private const val KEY_EVENTS = "events"
    }
}
