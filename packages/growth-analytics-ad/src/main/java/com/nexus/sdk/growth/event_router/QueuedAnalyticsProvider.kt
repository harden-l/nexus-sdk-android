package com.nexus.sdk.growth.event_router

import android.content.Context

internal class QueuedAnalyticsProvider(
    context: Context,
    private val delegate: AnalyticsProvider,
    maxSize: Int
) : AnalyticsProvider {
    override val name: String = delegate.name
    private val queue = EventQueue(context, "growth_queue_${delegate.name}", maxSize)

    override fun track(event: AnalyticsEvent) {
        queue.enqueue(event)
        flush()
    }

    override fun flush() {
        val pending = queue.snapshot()
        if (pending.isEmpty()) return

        val delivered = mutableListOf<String>()
        for (record in pending) {
            try {
                delegate.track(record.event)
                delivered += record.id
            } catch (_: Throwable) {
                break
            }
        }
        queue.remove(delivered)
        delegate.flush()
    }
}
