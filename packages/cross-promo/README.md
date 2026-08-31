# CrossPromoSDK Android

CrossPromoSDK handles internal cross-promotion between products in the same account.

Implemented in phase 8:

- Product list loaded from `apiBaseUrl + /related_products`.
- Unified promo page template.
- Product cards with icon, title, description and action button.
- Deep link first, Google Play / store URL fallback.
- Incoming promo link attribution parsing.
- Pending attribution cache.
- Login attribution flag integration with `CoreUserSDK` through `att=1`.
- Cross-product user link event.
- Cross-promo events routed through `GrowthAnalyticsAdSDK.track`.

## Basic Usage

```kotlin
CrossPromoSDK.init(
    context,
    CrossPromoConfig(
        sourceProductId = "product_a",
        defaultPlacement = "settings"
    )
)

CrossPromoSDK.showPromoPage(
    activity,
    ShowPromoPageOptions(
        placement = "settings",
        title = "More Apps",
        description = "Apps from the same account that may share membership benefits."
    )
)
```

## Events

- `cross_promo_show`
- `cross_promo_click`
- `cross_promo_open`
- `cross_promo_store_open`
- `cross_promo_open_failed`
- `cross_promo_activate`
- `cross_promo_user_link`

Common event params:

- `click_id`
- `source_product_id`
- `target_product_id`
- `placement`
- `campaign`
- `source_uid`
- `target_uid`
- `source_device_id`
- `target_device_id`
- `link_type`

## Attribution Flow

When a target app receives a cross-promo link, call:

```kotlin
CrossPromoSDK.handleIncomingPromoLink(intent.dataString)
```

This stores pending attribution, reports `cross_promo_activate`, and enables CoreUser login attribution. The next `CoreUserSDK.silentLogin()` request sends `att=1`.

After login, call:

```kotlin
CrossPromoSDK.flushPendingAttributionAfterLogin()
```

This reports `cross_promo_user_link` with `click_id`, source user/device, and target user/device. Current Android implementation uses DataEye events for attribution analysis; no server-side user-link API is called.
