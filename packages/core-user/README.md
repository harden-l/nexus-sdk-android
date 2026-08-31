# CoreUserSDK Android

Android Kotlin implementation for the first SDK phase.

## Scope

- Initialize SDK config.
- Generate and persist `deviceId`.
- Silent login through `/m/v7/user/login`.
- Persist `uid`.
- Bind email or phone through `/m/v7/user/bind_account`.
- Login always uses unencrypted JSON with `Encrypt: 0`; other interfaces follow `CoreUserConfig.encrypt`.
- `CoreUserConfig.encrypt` defaults to `true`; when enabled, a 32-byte product `encryptionKey` is required.

## Example

```kotlin
CoreUserSDK.init(
    context,
    CoreUserConfig(
        productId = "7",
        productName = "TEST PRODUCT",
        accountName = "test", // Replace with the real Google Play account name before release.
        apiBaseUrl = "https://api.example.com",
        encryptionKey = "PRODUCT_ENCRYPTION_KEY"
    )
)

CoreUserSDK.silentLoginAsync { result ->
    val user = result.getOrNull() ?: return@silentLoginAsync
}

CoreUserSDK.bindEmailAsync("user@example.com", "user-password") { result ->
    val bindResult = result.getOrNull()
}

CoreUserSDK.loginWithEmailAsync("user@example.com", "user-password") { result ->
    val user = result.getOrNull()
}

CoreUserSDK.getRelatedProductsAsync { result ->
    val relatedProducts = result.getOrNull().orEmpty()
}
```
