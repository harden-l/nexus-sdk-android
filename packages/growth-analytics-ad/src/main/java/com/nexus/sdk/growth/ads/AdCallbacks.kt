package com.nexus.sdk.growth.ads

interface AdCallbacks {
    fun onLoaded(placement: AdPlacement) = Unit
    fun onShown(placement: AdPlacement) = Unit
    fun onClicked(placement: AdPlacement) = Unit
    fun onClosed(placement: AdPlacement) = Unit
    fun onReward(placement: AdPlacement) = Unit
    fun onFailed(placement: AdPlacement, error: Throwable) = Unit
}
