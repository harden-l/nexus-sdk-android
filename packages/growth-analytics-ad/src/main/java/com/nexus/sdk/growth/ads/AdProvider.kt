package com.nexus.sdk.growth.ads

interface AdProvider {
    val name: String

    fun loadAd(placement: AdPlacement, callbacks: AdCallbacks? = null)

    fun showAd(placement: AdPlacement, callbacks: AdCallbacks? = null)
}
