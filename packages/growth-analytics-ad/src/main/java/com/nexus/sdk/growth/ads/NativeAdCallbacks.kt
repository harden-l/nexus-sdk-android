package com.nexus.sdk.growth.ads

import com.google.android.gms.ads.nativead.NativeAd

interface NativeAdCallbacks {
    fun onLoaded(placement: AdPlacement, nativeAd: NativeAd)
    fun onFailed(placement: AdPlacement, error: Throwable) = Unit
}
