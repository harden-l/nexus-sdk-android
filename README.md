# Nexus SDK Android

Android release artifacts for Nexus SDK.

## Version

Current release: `0.1.0`

## AAR Artifacts

| Module | AAR |
| --- | --- |
| CoreUserSDK | [nexus-core-user-0.1.0.aar](dist/android/aar/0.1.0/nexus-core-user-0.1.0.aar) |
| GrowthAnalyticsAdSDK | [nexus-growth-analytics-ad-0.1.0.aar](dist/android/aar/0.1.0/nexus-growth-analytics-ad-0.1.0.aar) |
| PaymentSDK | [nexus-payment-0.1.0.aar](dist/android/aar/0.1.0/nexus-payment-0.1.0.aar) |
| CrossPromoSDK | [nexus-cross-promo-0.1.0.aar](dist/android/aar/0.1.0/nexus-cross-promo-0.1.0.aar) |

## Integration Guide

See [Android business integration guide](docs/android/integration/business-integration-guide.md).

## Compatibility

Android 0.1.0 uses shared-account API `/api/v1`. Use a v1-enabled backend and matching AARs; do not mix with older account/payment/cross-promo modules. iOS and provider versions are managed separately.

See [0.1.0 release notes](RELEASE-0.1.0.md) and [SHA-256 checksums](dist/android/aar/0.1.0/SHA256SUMS). Historical `packages/` files are not the current source distribution; use the versioned AARs and integration guide.
