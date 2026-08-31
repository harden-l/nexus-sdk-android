# PaymentSDK Android

Android Kotlin implementation for payment config, product catalog, subscription UI, purchase providers, order verification, and entitlement.

## Scope

- Local payment config from `PaymentSDK.init(...)`.
- Product catalog from `/m/v7/iap/list`, including weekly points configuration.
- Subscription page template and UI.
- Google Play Billing provider.
- Mock provider and third-party provider stubs.
- Order verification through `/pp/v7/gp/os`.
- Entitlement grant and restore.
- Purchase revenue reporting after verified success.

## Basic Usage

```kotlin
PaymentSDK.init(
    PaymentConfig(
        productId = "android-demo",
        defaultChannel = PaymentChannel.GOOGLE_PLAY,
        enabledChannels = listOf(PaymentChannel.GOOGLE_PLAY)
    )
)

val products = PaymentSDK.getProducts(forceRefresh = true)

PaymentSDK.showSubscriptionPage(
    activity,
    SubscriptionPageConfig(
        templateId = SubscriptionPageTemplates.AURORA,
        title = "Upgrade to Pro",
        benefitDescription = "Purchase one product and share membership benefits.",
        benefits = listOf("Unlimited usage", "Remove ads", "Unlock premium features"),
        sharedApps = SubscriptionSharedAppsConfig(
            title = "membership share",
            description = "Your membership gives you access to every current service in this app."
        ),
        paymentChannels = listOf(PaymentChannel.GOOGLE_PLAY),
        showPaymentChannel = true,
        showRestore = true,
        ctaText = "Continue",
        restoreText = "Restore Purchase",
        termsText = "Terms",
        privacyText = "Privacy"
    )
)
```

Terms and Privacy are shown by default and open in the system browser:

- Terms: `https://www.crypsiscollectiveinc.com/terms.html`
- Privacy: `https://www.crypsiscollectiveinc.com/privacy.html`

Set `showTerms` or `showPrivacy` to `false` to hide an entry. Custom URLs are supported, but an enabled entry cannot use an empty URL.

The subscription page displays coin grants using `coins_granted × 100` (for example, API value `20` is displayed as `2000`). `Product.coinsGranted` and purchase processing keep the raw API value.

Built-in templates are `aurora`, `midnight`, and `minimal`. Pass the matching `SubscriptionPageTemplates` constant through `templateId`; unknown IDs fall back to `aurora`.

## Purchase

```kotlin
PaymentSDK.purchase(
    activity = activity,
    product = product,
    channel = PaymentChannel.GOOGLE_PLAY
) { result ->
    // handle result
}
```

Revenue is reported only after provider purchase succeeds, server verification succeeds, and entitlement is granted.

## Channel Rules

- Android real channel is `google_play`.
- `app_store` in Android config is treated as a config error.
- The SDK does not automatically fallback to unconfigured channels.

## Docs

- [../../../docs/android/modules/payment.md](../../../docs/android/modules/payment.md)
- [../../../docs/android/specs/payment/stage-7-real-integration.md](../../../docs/android/specs/payment/stage-7-real-integration.md)
