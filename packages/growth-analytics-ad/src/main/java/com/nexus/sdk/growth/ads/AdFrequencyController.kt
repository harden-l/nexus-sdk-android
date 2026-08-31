package com.nexus.sdk.growth.ads

internal class AdFrequencyController {
    private val impressionCounts = mutableMapOf<String, Int>()

    @Synchronized
    fun canShow(placement: AdPlacement): Boolean {
        val cap = placement.frequencyCap ?: return true
        return impressionCounts.getOrDefault(placement.placement, 0) < cap
    }

    @Synchronized
    fun markShown(placement: AdPlacement) {
        impressionCounts[placement.placement] = impressionCounts.getOrDefault(placement.placement, 0) + 1
    }
}
