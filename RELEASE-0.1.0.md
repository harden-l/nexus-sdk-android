# Android SDK 0.1.0

Date: 2026-10-13. This release updates Android only; iOS and provider releases are unchanged.

## Same-version update (2026-10-15)

- Rebuilt Payment AAR as `0.1.0`. Upfront `coins_granted` and additional enabled `weekly_points` are now both shown in Aurora, Midnight and Minimal, each scaled by100 once.
- CoreUser, GrowthAnalyticsAd and CrossPromo AARs are byte-for-byte unchanged.
- Replace any previously downloaded Payment0.1.0 AAR. New SHA256: `a8730379b18203603b8c95def4dafd10d82437b54b260cdf48be50b47c6a3760`.
- The `0.1.0` tag is refreshed for this explicitly requested same-version update; the previous release remains in Git history at `7d9357c14837311db821f34d42fcac5eb0426b84`.
- Payment unit tests and an AAR-backed Debug Demo build passed. Android Pixel10 and iOS iPhone17Pro simulator checks confirmed all three templates display125 upfront credits plus37.5 weekly points for raw values1.25 and0.375. Android also verified claim balance refresh1212.5 ->1250. These are fixture checks, not real store purchases.

## Included artifacts

CoreUser, GrowthAnalyticsAd, Payment and CrossPromo AARs all report version `0.1.0`. Download from [dist/android/aar/0.1.0](dist/android/aar/0.1.0); verify using its `SHA256SUMS` file.

## Changes

- Migrate account, product, weekly-points, order and related-app requests to shared-account API `/api/v1`.
- Add product-scoped Keystore sessions, guest login coalescing, expiry/401 handling and public `CoreUserSDK.getToken()`.
- Support email/phone password login, optional binding passwords, exact decimal amounts and per-bill charge idempotency keys.
- Keep user balances display-scaled x100; charge and weekly-point values remain raw. Weekly claims use the subscription selected by the server, without a client SKU.
- Use sorted backend products and USD reference prices until Billing details arrive; prevent purchase before store details are ready.
- Correct CrossPromo Activity registration and coin-card reference-price wrapping.
- Remove old dynamic-switch API support. Account deregistration clears the session; use `clearLocalSession()` for local-only sign-out.

## Integration

Use a v1-enabled backend and the complete matching module dependency set. Do not mix older account/payment/recommendation AARs. Payment requires CoreUser and Growth AARs, but does not initialize Growth or require analytics to be enabled.

See the [business integration guide](docs/android/integration/business-integration-guide.md) for initialization, token access, migration behavior, units, weekly points and subscription-page configuration.

## Validation and limits

- 90 local module/integration tests passed.
- Both Debug Demo apps compiled against the actual release AARs and were installed on an OPPO PESM10 running Android 12.
- Isolated real-account verification passed for encrypted login, process-restart session recovery, host-owned requests using `getToken()`, email binding/login, decimal debit idempotency and no-subscription claim rejection.
- Three subscription templates and recommendation filtering passed using explicit local fixture data.
- No real paid purchase was initiated. The supplied live product catalog and related-app list are empty; real purchase/restore, store notification delivery and paid-subscription weekly claim success are not verified by this release test.

Only AARs, checksums and release documentation are updated in this artifact repository. Historical `packages/` sources are not the source for this version.
