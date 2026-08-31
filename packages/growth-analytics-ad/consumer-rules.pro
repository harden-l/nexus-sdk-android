# Keep metadata used by analytics/ad SDK APIs and their callback implementations.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault

# Keep stable provider class names for host-side diagnostics and optional lookup.
-keepnames class com.nexus.sdk.growth.firebase.FirebaseAnalyticsProvider
-keepnames class com.nexus.sdk.growth.appsflyer.AppsflyerAnalyticsProvider
-keepnames class com.nexus.sdk.growth.bi.DataEyeAnalyticsProvider
-keepnames class com.nexus.sdk.growth.ads.AdMobAdProvider

# Firebase, AdMob, AppsFlyer, and DataEye dependencies contribute their own
# consumer ProGuard rules. Do not keep all vendor classes here; doing so would
# unnecessarily disable shrinking and optimization in the host App.
