# GrowthAnalyticsAdSDK Android

Android Kotlin implementation for analytics, attribution, ads, and revenue reporting.

## Scope

- Unified event model and provider routing.
- BI(DataEye), Firebase, AppsFlyer, and Mock analytics providers.
- Offline queue wrapper for provider delivery.
- User properties.
- Install source and deep-link attribution cache.
- AdMob and Mock ad providers.
- Banner, Interstitial, Rewarded, Rewarded Interstitial, Native, and App Open ads.
- Ad revenue and purchase revenue normalization.
- Firebase / AppsFlyer `ad_imp` mapping.

## Firebase host configuration

Firebase Analytics requires the host App to add `com.google.firebase:firebase-analytics` and apply `com.google.gms.google-services`. The host must also provide a `google-services.json` whose package name matches its `applicationId`. The Gradle plugin and configuration file cannot be embedded in this SDK AAR.

This module publishes `consumer-rules.pro`. Nexus provider metadata and provider class names are preserved, while Firebase, AdMob, AppsFlyer, and DataEye contribute their official consumer rules through their Maven dependencies. Avoid blanket `-keep` rules for entire vendor packages unless a specific third-party SDK version explicitly requires them.

## Basic Usage

```kotlin
GrowthAnalyticsAdSDK.init(
    context,
    AnalyticsConfig(
        productId = "android-demo",
        platform = "android",
        enableBI = true,
        enableFirebase = true,
        enableAppsflyer = true,
        enableAdMob = true,
        debug = true
    ),
    activityProvider = { activity }
)

GrowthAnalyticsAdSDK.setUser(user)
GrowthAnalyticsAdSDK.setUserProperties(mapOf("country" to "US"))
GrowthAnalyticsAdSDK.track("page_view", mapOf("page" to "home"))
```

## Ads

```kotlin
val placement = AdPlacement(
    placement = "home_interstitial",
    adUnitId = "ca-app-pub-3940256099942544/1033173712",
    format = AdFormat.INTERSTITIAL
)

GrowthAnalyticsAdSDK.loadAd(placement)
GrowthAnalyticsAdSDK.showAd(placement)
```

## AdMob Requirement

Apps that include the AdMob provider must declare an AdMob application ID in their app manifest:

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy" />
```

The demo uses Google's official test app ID:

```text
ca-app-pub-3940256099942544~3347511713
```

## Docs

- [../../../docs/android/specs/event-and-revenue.md](../../../docs/android/specs/event-and-revenue.md)
- [../../../docs/android/modules/growth-analytics-ad.md](../../../docs/android/modules/growth-analytics-ad.md)
