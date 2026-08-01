# Nexus SDK Android 业务接入说明
本文面向接入 Nexus SDK 的业务 App。SDK按模块提供能力，可根据需求接入其中1个或多个模块。

当前版本：`0.0.5`

## 1. 模块选择
| 模块 | AAR | 适用场景 | 前置依赖 |
| --- | --- | --- | --- |
| CoreUserSDK | [nexus-core-user-0.0.5.aar](https://raw.githubusercontent.com/harden-l/nexus-sdk-android/main/dist/android/aar/0.0.5/nexus-core-user-0.0.5.aar) | 设备 ID、静默登录、用户信息、邮箱绑定、余额和金币 | 无 |
| GrowthAnalyticsAdSDK | [nexus-growth-analytics-ad-0.0.5.aar](https://raw.githubusercontent.com/harden-l/nexus-sdk-android/main/dist/android/aar/0.0.5/nexus-growth-analytics-ad-0.0.5.aar) | BI/Firebase/AppsFlyer 事件、AdMob 广告、归因 | CoreUserSDK |
| PaymentSDK | [nexus-payment-0.0.5.aar](https://raw.githubusercontent.com/harden-l/nexus-sdk-android/main/dist/android/aar/0.0.5/nexus-payment-0.0.5.aar) | 商品、订阅页、Google Play Billing、订单校验、权益 | CoreUserSDK、GrowthAnalyticsAdSDK |
| CrossPromoSDK | [nexus-cross-promo-0.0.5.aar](https://raw.githubusercontent.com/harden-l/nexus-sdk-android/main/dist/android/aar/0.0.5/nexus-cross-promo-0.0.5.aar) | 应用互导推荐页、Deep Link、导量归因 | CoreUserSDK、GrowthAnalyticsAdSDK |

## 2. 通用准备
请按实际接入模块向后台或 SDK 提供方确认配置：

| 配置 | 用途 |
| --- | --- |
| `productId` | 后台产品 ID；接口 Header 中的 `ProductId`。 |
| `productName` | 接口 Header `Product`。 |
| `accountName` | Google Play 开发者账号名称；默认 `test` 仅供测试，生产上线前必须替换为真实值。 |
| `apiBaseUrl` | 接口域名；测试环境使用 `https://serverlf.stoahayaamhsothy.com/`，生产环境使用 `https://v8b.crypsiscollectiveinc.com`。 |
| `encryptionKey` | 生产环境必填，32 字节 AES key。 |
| AdMob App ID / Ad Unit ID | 仅广告模块需要。 |
| Firebase 配置 | 仅 Firebase 事件需要，业务 App 需要按 Firebase 官方方式接入真实配置。 |
| AppsFlyer Dev Key | 仅 AppsFlyer 事件需要。 |
| `gt` | 登录注册赠送梯度码，非必填：`1` 赠送 10，`2` 赠送 20，`3` 赠送 30，其它值不赠送。 |
| Google Play 商品 ID | 仅支付模块需要。 |
| Deep Link Scheme | 仅 CrossPromo 或归因 Deep Link 需要。 |

生产环境说明：

- `CoreUserConfig.encrypt` 默认是 `true`。
- `encrypt = true` 时必须传 `encryptionKey`。
- `gt` 为登录注册赠送梯度码，默认不传。
- 其它接口按 `encrypt` 配置加密请求并解密响应。
- 同步网络 API 不能在主线程调用；业务侧优先使用异步 API。

## 3. AAR 依赖
业务工程要求：

- `minSdk` 不低于 23，建议使用 `compileSdk 36`。
- 使用 JDK 17。
- Gradle 仓库包含 `google()` 和 `mavenCentral()`。

将需要的 AAR 放到业务 App 模块的 `libs/` 目录，并按需添加：

```kotlin
dependencies {
    implementation(files("libs/nexus-core-user-<version>.aar"))
    // 按需接入
    implementation(files("libs/nexus-growth-analytics-ad-<version>.aar"))
    implementation(files("libs/nexus-payment-<version>.aar"))
    implementation(files("libs/nexus-cross-promo-<version>.aar"))
}
```

本地 AAR 不会自动引入其 Maven 依赖。请根据接入的模块添加对应依赖：

```kotlin
dependencies {
    // GrowthAnalyticsAdSDK
    implementation("com.google.android.gms:play-services-ads:25.3.0")
    implementation("com.google.firebase:firebase-analytics:23.0.0")
    implementation("com.appsflyer:af-android-sdk:6.17.5")
    implementation("io.github.dataeyesdk:dataeye-android-sdk:2.8.3")

    // PaymentSDK
    implementation("com.android.billingclient:billing-ktx:8.0.0")
}
```

只接入 CoreUserSDK 时无需添加上述第三方依赖。接入 PaymentSDK 或 CrossPromoSDK 时，必须同时添加其前置 AAR。

## 4. CoreUserSDK 接入
### 4.1 初始化
```kotlin
import com.nexus.sdk.coreuser.init.CoreUserConfig
import com.nexus.sdk.coreuser.init.CoreUserSDK

CoreUserSDK.init(
    context,
    CoreUserConfig(
        productId = "7",
        productName = "TEST PRODUCT",
        accountName = "test",
        apiBaseUrl = "https://serverlf.stoahayaamhsothy.com/",
        encrypt = false,
        encryptionKey = "1b8df48c1fa64ce28a2e8133dffe600c",
        debug = true,
        gt = 1
    )
)
```

生产环境需要打开加密并传入当前产品的 `encryptionKey`：

```kotlin
CoreUserSDK.init(
    context,
    CoreUserConfig(
        productId = "7",
        productName = "TEST PRODUCT",
        accountName = "real-google-play-account-name",
        apiBaseUrl = "https://v8b.crypsiscollectiveinc.com",
        encrypt = true,
        encryptionKey = "<CURRENT_PRODUCT_32_BYTE_AES_KEY>"
    )
)
```

生产示例中的 `productId`、`productName`、`accountName` 和 `encryptionKey` 必须替换为当前产品的真实配置；不要直接使用占位值。`gt` 仅在业务需要注册赠送时传入。

`CoreUserConfig` 字段说明：

| 字段 | 是否必填 | 说明 |
| --- | --- | --- |
| `productId` | 是 | 后台产品 ID；接口 Header 中的 `ProductId`。 |
| `productName` | 是 | 产品名称；接口 Header 中的 `Product`。 |
| `accountName` | 否 | 默认 `test`，仅供测试；`apiBaseUrl + /related_products` 接口使用它查询同账号应用，生产上线前必须设置真实的 Google Play 开发者账号名称。 |
| `apiBaseUrl` | 是 | Nexus 后台接口域名；测试环境使用 `https://serverlf.stoahayaamhsothy.com/`，生产环境使用 `https://v8b.crypsiscollectiveinc.com`。 |
| `version` | 否 | App 版本号；默认读取当前 App 版本，读取失败时为 `1.0.0`。 |
| `country` | 否 | 国家/地区；不传时 SDK 自动使用设备 Locale。 |
| `language` | 否 | 语言；不传时 SDK 自动使用设备 Locale。 |
| `encrypt` | 否 | 是否加密非登录接口，默认 `true`；登录接口固定不加密。 |
| `encryptionKey` | `encrypt=true` 时必填 | 当前产品的 32 字节 AES key。 |
| `debug` | 否 | 是否输出 SDK debug log，默认 `false`。 |
| `gt` | 否 | 登录注册赠送梯度码：`1` 赠送 10，`2` 赠送 20，`3` 赠送 30，其它值不赠送；不传时登录请求不携带。仅本次首次创建用户且新建共享钱包时生效 |

### 4.2 登录
```kotlin
CoreUserSDK.silentLoginAsync { result ->
    result.onSuccess { user ->
        // user.uid
        // user.deviceId
        // user.emailBound
        // user.phoneBound
        // user.balance
        // 登录不会强制绑定邮箱；需要绑定时由业务主动展示入口或调用 ensureEmailBound。
    }.onFailure { error ->
        // 登录失败处理
    }
}
```

登录类型：

```kotlin
import com.nexus.sdk.coreuser.silent_login.LoginType

val user = CoreUserSDK.silentLogin(LoginType.GUEST)
```

当前枚举值：

- `LoginType.GUEST`
- `LoginType.EMAIL`
- `LoginType.PHONE`

默认使用 `LoginType.GUEST`。登录请求会携带本地已有 uid；首次登录时 uid 为空字符串。

登录后SDK会拉取一次用户信息。

### 4.3 获取登录动态配置
```kotlin
val loginConfig = CoreUserSDK.getConfig()
```

`getConfig()` 返回最近一次登录接口响应中除 `uid` 之外的动态配置字段，类型为 `Map<String, Any?>`。登录接口返回字段是不固定的，SDK 不会为这些字段定义固定模型，业务方按后台配置约定读取即可。

```kotlin
CoreUserSDK.silentLoginAsync { result ->
    result.onSuccess {
        val config = CoreUserSDK.getConfig()
        val example = config["example"]
    }
}
```

说明：

- 首次登录成功前调用时可能返回空 Map。
- `logout()` 会先注销服务端用户，成功后清空本地登录动态配置和用户资料缓存，但保留 uid，下一次登录请求仍会携带该 uid。
- `getConfig()` 返回的是登录动态配置，不是用户资料。

退出登录示例：

```kotlin
CoreUserSDK.logoutAsync { result ->
    if (result.isSuccess) {
        // 退出成功
    } else {
        val error = result.exceptionOrNull()
        // 注销接口失败，本地用户缓存不会被清理
    }
}
```

### 4.4 用户信息和邮箱绑定
```kotlin
CoreUserSDK.fetchUserInfoAsync { result ->
    result.onSuccess { user ->
        // user.balance 为当前用户余额
        if (!user.emailBound) {
            // 可展示绑定入口
        }
    }
}
```

`fetchUserInfoAsync()` 返回用户资料和当前余额 `balance`，并刷新 SDK 本地用户缓存。业务方展示金币余额或扣金币后刷新余额时，可以读取返回的 `user.balance`。

使用 SDK 内置邮箱绑定弹窗：

```kotlin
CoreUserSDK.ensureEmailBound(activity) { result ->
    // ALREADY_BOUND / BOUND / CANCELLED / USER_INFO_FAILED / BIND_FAILED
}
```

### 4.5 扣除聊天金币
```kotlin
CoreUserSDK.consumeChatCoinsAsync(
    cost = 2.5,
    remark = "chat billing"
) { result ->
    result.onSuccess { consume ->
        // consume.beforeCoins
        // consume.afterCoins
        // consume.balance
    }.onFailure { error ->
        // 扣除失败处理
    }
}
```

说明：

- SDK 会自动携带当前 uid；本地无用户时会先静默登录。
- `cost` 必须大于 `0`，金币数使用 `Double`，避免小数被截断。
- 该接口按 `CoreUserConfig.encrypt` 的通用策略加密请求和解密响应，不走登录接口免加密规则。
- 扣除成功后 SDK 不直接修改本地 `SDKUser.balance` 缓存；业务方如需刷新余额，调用 `fetchUserInfoAsync()`。

## 5. GrowthAnalyticsAdSDK 接入
### 5.1 AndroidManifest
如果启用 AdMob，业务 App 必须在自己的 `AndroidManifest.xml` 中配置 AdMob App ID。这个值属于宿主 App，不能由 SDK AAR 写死，否则多个业务 App 会共用错误的 AdMob 应用配置：

```xml
<application>
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy" />
</application>
```

如果启用 Firebase，业务 App 需要按 Firebase 官方 Android 接入方式添加真实配置：

- 将 Firebase Console 下载的 `google-services.json` 放到业务 App module。
- 在业务 App 的 Gradle 配置中启用 Google Services 插件。
- 使用和业务 App 包名匹配的 Firebase Android App 配置。

SDK 不内置 `google-services.json`。

如果启用 AppsFlyer，业务 App 需要提供自己的 Dev Key、App ID/包名相关配置，并在需要 OneLink/Deep Link 时配置对应的 intent-filter 或 App Links。

### 5.2 初始化
```kotlin
import android.app.Activity
import com.nexus.sdk.growth.event_router.AnalyticsConfig
import com.nexus.sdk.growth.event_router.GrowthAnalyticsAdSDK

var currentActivity: Activity? = null

GrowthAnalyticsAdSDK.init(
    context = context,
    config = AnalyticsConfig(
        productId = "7",
        enableBI = true,
        enableFirebase = true,
        enableAppsflyer = true,
        enableAdMob = true,
        debug = true
    ),
    activityProvider = { currentActivity }
)
```

如果已接入 CoreUserSDK：

```kotlin
CoreUserSDK.silentLoginAsync { result ->
    result.onSuccess { user ->
        GrowthAnalyticsAdSDK.setUser(user)
    }
}
```

### 5.3 事件和用户属性
```kotlin
GrowthAnalyticsAdSDK.track(
    "button_click",
    mapOf("scene" to "home")
)
```

```kotlin
GrowthAnalyticsAdSDK.setUserProperties(
    mapOf("vip_level" to 1)
)
```

当前 Firebase 和 AppsFlyer 只按需求处理 `ad_impression`。

说明：

- `GrowthAnalyticsAdSDK.track(...)` 可以分发普通事件到已注入或已启用的 Provider。
- 当前按业务需求，Firebase / AppsFlyer 的正式字段映射重点只保证 `ad_impression`。
- BI(DataEye) 按 Nexus/BI 事件模型上报。

### 5.4 Deep Link 和归因
```kotlin
intent.data?.toString()?.let { url ->
    GrowthAnalyticsAdSDK.handleDeepLink(url)
}

val installSource = GrowthAnalyticsAdSDK.getInstallSource()
val lastDeepLink = GrowthAnalyticsAdSDK.getLastDeepLink()
```

### 5.5 AdMob 广告
```kotlin
import com.nexus.sdk.growth.ads.AdFormat
import com.nexus.sdk.growth.ads.AdPlacement

val interstitial = AdPlacement(
    placement = "level_end",
    adUnitId = "ca-app-pub-xxx/interstitial",
    format = AdFormat.INTERSTITIAL
)

GrowthAnalyticsAdSDK.loadAd(interstitial)
GrowthAnalyticsAdSDK.showAd(interstitial)
```

支持广告类型：

- `APP_OPEN`
- `BANNER`
- `INTERSTITIAL`
- `REWARDED`
- `NATIVE`

全屏广告按 `format + adUnitId` 管理缓存。重复调用 `loadAd()` 时，如果已有缓存或正在加载，SDK 不会再次发起广告请求；并发传入的加载回调会在本次加载完成后统一返回。

`showAd()` 会先检查缓存：有缓存时立即展示；无缓存时自动开始加载，本次通过 `onFailed` 返回广告未就绪，业务方可在后续时机再次调用 `showAd()`。开屏、插屏和激励广告展示成功或展示失败后，SDK 会自动预加载下一条。同一广告正在展示时不会重复展示。

## 6. PaymentSDK 接入
### 6.1 初始化
```kotlin
CoreUserSDK.init(context, coreUserConfig)

PaymentSDK.init(
    PaymentConfig(
        productId = "7",
        enabledChannels = listOf(PaymentChannel.GOOGLE_PLAY),
        defaultChannel = PaymentChannel.GOOGLE_PLAY
    )
)
```

注意：`PaymentSDK.init(...)` 内部会读取 `CoreUserSDK.getSdkConfig()`，所以必须先初始化 CoreUserSDK。

### 6.2 订阅页
```kotlin
import com.nexus.sdk.payment.subscription_template.SubscriptionPageConfig
import com.nexus.sdk.payment.subscription_template.SubscriptionSharedAppsConfig

PaymentSDK.showSubscriptionPage(
    activity,
    SubscriptionPageConfig(
        title = "Upgrade to Pro",
        benefitDescription = "Purchase one product and share membership benefits.",
        benefits = listOf("Unlimited usage", "Remove ads"),
        sharedApps = SubscriptionSharedAppsConfig(
            title = "membership share",
            description = "Your membership gives you access to every current service in this app."
        ),
        paymentChannels = listOf(PaymentChannel.GOOGLE_PLAY),
        showPaymentChannel = true,
        showRestore = true,
        showTerms = true,
        showPrivacy = true,
        termsUrl = "https://example.com/terms",
        privacyUrl = "https://example.com/privacy",
        ctaText = "Continue"
    )
)
```

打开订阅页后，SDK 会自动完成以下流程，业务方不需要提前获取商品或手动调用购买接口：

- 从 Nexus 后台获取商品的 `market_product_id`、`product_type` 和 `coins_granted`。
- 从 Google Play Billing 获取价格、币种、本地化价格、订阅周期和试用信息，并与后台商品合并。
- 获取关联应用并展示 Membership Share 区域。
- 用户点击 CTA 后发起购买，购买成功后完成服务端订单校验和权益处理。
- `showRestore = true` 时由页面提供恢复入口并执行恢复流程。
- 订阅页只展示同时存在于 Nexus 商品接口和 Google Play Billing 的商品；后台配置错误或商店不存在的商品不会展示。
- Google Play 订单校验在后台线程执行，成功后 SDK 会确认订阅/非消耗品，或消耗带 `coins_granted` 的金币商品。
- App 启动时 SDK 会查询未完成订单，恢复 Pending 完成和进程重启期间遗漏的购买。
- 权益和交付记录按 `productId + uid` 持久化，防止切换用户时串用权益或重复交付。

校验规则：

- `showTerms = true` 时 `termsUrl` 必填。
- `showPrivacy = true` 时 `privacyUrl` 必填。
- 支付方式配置错误时不自动兜底。

## 7. CrossPromoSDK 接入
### 7.1 AndroidManifest
目标 App 需要配置自己的 Deep Link：

```xml
<activity
    android:name=".MainActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="yourapp"
            android:host="promo" />
    </intent-filter>
</activity>
```

### 7.2 初始化
```kotlin
CoreUserSDK.init(context, coreUserConfig)

CrossPromoSDK.init(
    context,
    CrossPromoConfig(sourceProductId = "7")
)
```

### 7.3 展示推荐页
```kotlin
import com.nexus.sdk.crosspromo.placement.ShowPromoPageOptions

CrossPromoSDK.showPromoPage(
    context,
    ShowPromoPageOptions(
        placement = "home",
        campaign = "internal_cross_promo",
        title = "Recommended Apps",
        description = "More apps from this account"
    )
)
```

### 7.4 处理 Deep Link
```kotlin
intent.data?.toString()?.let { url ->
    CrossPromoSDK.handleIncomingPromoLink(url)
}
```

登录成功后可尝试处理待关联归因：

```kotlin
CoreUserSDK.silentLoginAsync {
    CrossPromoSDK.flushPendingAttributionAfterLogin()
}
```
