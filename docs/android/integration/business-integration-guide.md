# Nexus SDK Android 业务接入说明
本文面向接入 Nexus SDK 的业务 App。SDK按模块提供能力，可根据需求接入其中1个或多个模块。

当前版本：`0.0.3`

## 1. 模块选择
| 模块 | AAR | 适用场景 | 前置依赖 |
| --- | --- | --- | --- |
| CoreUserSDK | [nexus-core-user-0.0.3.aar](https://raw.githubusercontent.com/harden-l/nexus-sdk-android/main/dist/android/aar/0.0.3/nexus-core-user-0.0.3.aar) | 设备 ID、静默登录、用户信息、邮箱绑定 | 无 |
| GrowthAnalyticsAdSDK | [nexus-growth-analytics-ad-0.0.3.aar](https://raw.githubusercontent.com/harden-l/nexus-sdk-android/main/dist/android/aar/0.0.3/nexus-growth-analytics-ad-0.0.3.aar) | BI/Firebase/AppsFlyer 事件、AdMob 广告、归因 | 建议接入 CoreUserSDK，用于 uid/deviceId |
| PaymentSDK | [nexus-payment-0.0.3.aar](https://raw.githubusercontent.com/harden-l/nexus-sdk-android/main/dist/android/aar/0.0.3/nexus-payment-0.0.3.aar) | 商品、订阅页、Google Play Billing、订单校验、权益 | 必须先初始化 CoreUserSDK |
| CrossPromoSDK | [nexus-cross-promo-0.0.3.aar](https://raw.githubusercontent.com/harden-l/nexus-sdk-android/main/dist/android/aar/0.0.3/nexus-cross-promo-0.0.3.aar) | 应用互导推荐页、Deep Link、导量归因 | 必须先初始化 CoreUserSDK；如需事件上报，建议接入 GrowthAnalyticsAdSDK |

## 2. 通用准备
请按实际接入模块向后台或 SDK 提供方确认配置：

| 配置 | 用途 |
| --- | --- |
| `productId` | 后台产品 ID；接口 Header 中的 `ProductId`。 |
| `productName` | 接口 Header `Product`。 |
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
- 登录接口 `/m/v7/user/login` 固定不加密。
- `gt` 为登录注册赠送梯度码，默认不传。
- 其它接口按 `encrypt` 配置加密请求并解密响应。
- 同步网络 API 不能在主线程调用；业务侧优先使用异步 API。

## 3. AAR 依赖
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

如果业务 App 只需要登录，只添加 `core-user` 即可。

## 4. CoreUserSDK 接入
### 4.1 适用场景
接入 CoreUserSDK 后，业务方可以获得：

- 设备唯一 ID。
- 静默登录 uid。
- 用户信息：邮箱、手机号绑定状态、余额 `balance`。
- 聊天账单扣除金币。
- SDK 内置绑定邮箱弹窗。

### 4.2 依赖
```kotlin
dependencies {
    implementation(files("libs/nexus-core-user-<version>.aar"))
}
```

### 4.3 初始化
```kotlin
import com.nexus.sdk.coreuser.init.CoreUserConfig
import com.nexus.sdk.coreuser.init.CoreUserSDK

CoreUserSDK.init(
    context,
    CoreUserConfig(
        productId = "7",
        productName = "TEST PRODUCT",
        apiBaseUrl = "https://serverlf.stoahayaamhsothy.com/",
        encrypt = false,
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
        apiBaseUrl = "https://v8b.crypsiscollectiveinc.com",
        encrypt = true,
        encryptionKey = "32-byte-product-encryption-key",
        gt = 1
    )
)
```

`gt` 为登录接口注册赠送梯度码，非必填。不需要注册赠送时保持默认 `null`，SDK 不会在登录请求中携带 `gt`。

`CoreUserConfig` 字段说明：

| 字段 | 是否必填 | 说明 |
| --- | --- | --- |
| `productId` | 是 | 后台产品 ID；接口 Header 中的 `ProductId`。 |
| `productName` | 是 | 产品名称；接口 Header 中的 `Product`。 |
| `apiBaseUrl` | 是 | Nexus 后台接口域名；测试环境使用 `https://serverlf.stoahayaamhsothy.com/`，生产环境使用 `https://v8b.crypsiscollectiveinc.com`。 |
| `version` | 否 | App 版本号；默认读取当前 App 版本，读取失败时为 `1.0.0`。 |
| `country` | 否 | 国家/地区；不传时 SDK 自动使用设备 Locale。 |
| `language` | 否 | 语言；不传时 SDK 自动使用设备 Locale。 |
| `encrypt` | 否 | 是否加密非登录接口，默认 `true`；登录接口固定不加密。 |
| `encryptionKey` | `encrypt=true` 时必填 | 当前产品的 32 字节 AES key。 |
| `debug` | 否 | 是否输出 SDK debug log，默认 `false`。 |
| `gt` | 否 | 登录注册赠送梯度码：`1` 赠送 10，`2` 赠送 20，`3` 赠送 30，其它值不赠送；不传时登录请求不携带。 |

### 4.4 登录
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

### 4.5 获取登录动态配置
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
- `logout()` 会清空本地保存的登录动态配置和用户资料缓存，但保留 uid，下一次登录请求仍会携带该 uid。
- `getConfig()` 返回的是登录动态配置，不是 `/m/v7/user/info` 的用户信息。

### 4.6 用户信息和邮箱绑定
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

`fetchUserInfoAsync()` 会请求 `/m/v7/user/info`，返回用户资料和当前余额 `balance`，并刷新 SDK 本地用户缓存。业务方展示金币余额或扣金币后刷新余额时，可以读取返回的 `user.balance`。

使用 SDK 内置邮箱绑定弹窗：

```kotlin
CoreUserSDK.ensureEmailBound(activity) { result ->
    // ALREADY_BOUND / BOUND / CANCELLED / USER_INFO_FAILED / BIND_FAILED
}
```

### 4.7 扣除聊天金币
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

- 该能力调用 `/m/v7/coins/consume_chat`，SDK 会自动携带当前 uid；本地无用户时会先静默登录。
- `cost` 必须大于 `0`，金币数使用 `Double`，避免小数被截断。
- 该接口按 `CoreUserConfig.encrypt` 的通用策略加密请求和解密响应，不走登录接口免加密规则。
- 扣除成功后 SDK 不直接修改本地 `SDKUser.balance` 缓存；业务方如需刷新余额，调用 `fetchUserInfoAsync()`。

### 4.8 CoreUser 验证
- 初始化不抛异常。
- `silentLoginAsync()` 返回 uid。
- 配置 `gt` 后，登录请求能携带注册赠送梯度码；未配置时不携带。
- `CoreUserSDK.getConfig()` 能读取登录接口返回的动态配置。
- 再次登录时请求体带上本地已有 uid。
- `/m/v7/user/info` 返回的 `email_bound`、`phone_bound`、`balance` 解析正确。
- `/m/v7/coins/consume_chat` 能返回并解析 `before_coins`、`after_coins`、`balance`。
- 未绑定邮箱时弹出 SDK 内置绑定邮箱弹窗。

## 5. GrowthAnalyticsAdSDK 接入
### 5.1 适用场景
接入 GrowthAnalyticsAdSDK 后，业务方可以使用：

- BI(DataEye) 事件上报。
- Firebase 事件上报。
- AppsFlyer 事件上报。
- AdMob 广告加载和展示。
- 归因、Deep Link 解析和缓存。

如果业务方需要事件带 uid/deviceId，建议同时接入 CoreUserSDK 并调用 `setUser(user)`。

### 5.2 依赖
```kotlin
dependencies {
    implementation(files("libs/nexus-growth-analytics-ad-<version>.aar"))
    // 推荐同时接入，用于用户关联
    implementation(files("libs/nexus-core-user-<version>.aar"))
}
```

### 5.3 AndroidManifest
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

SDK 不内置 `google-services.json`。模拟配置只能验证本地调用链路，不能验证 Firebase 后台真实收数。

如果启用 AppsFlyer，业务 App 需要提供自己的 Dev Key、App ID/包名相关配置，并在需要 OneLink/Deep Link 时配置对应的 intent-filter 或 App Links。

### 5.4 初始化
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

### 5.5 事件和用户属性
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

### 5.6 Deep Link 和归因
```kotlin
intent.data?.toString()?.let { url ->
    GrowthAnalyticsAdSDK.handleDeepLink(url)
}

val installSource = GrowthAnalyticsAdSDK.getInstallSource()
val lastDeepLink = GrowthAnalyticsAdSDK.getLastDeepLink()
```

### 5.7 AdMob 广告
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

开屏和插屏支持预加载，展示成功或展示失败后 SDK 会自动尝试加载下一条。

### 5.8 Growth/Ad 验证
- Growth 事件能在 debug log 中看到。
- BI(DataEye) 后台能收到事件。
- Firebase、AppsFlyer 后台能收到 `ad_impression`。Firebase 必须使用真实 `google-services.json` 才能做后台收数验证。
- AdMob App ID 已配置在业务 App Manifest。
- AdMob 各广告位能加载和展示。
- 开屏、插屏展示后能继续预加载下一条。

Firebase 验证建议：

1. 使用业务 App 包名在 Firebase Console 创建 Android App。
2. 下载真实 `google-services.json` 并放入业务 App module。
3. 打开 Firebase Analytics DebugView。
4. 运行 Demo 或业务 App，触发 AdMob paid event 或手动触发 `ad_impression`。
5. 在 DebugView/控制台确认事件和参数。

如果只使用模拟配置，能验证 SDK 本地初始化和调用路径，但不能证明 Firebase 服务端已接收事件。

## 6. PaymentSDK 接入
### 6.1 适用场景
接入 PaymentSDK 后，业务方可以使用：

- `/m/v7/iap/list` 获取后台商品。
- Google Play Billing 查询真实商品信息。
- 订阅页。
- Google Play 购买。
- `/pp/v7/gp/os` 订单校验。
- 本地权益和收入上报。

PaymentSDK 必须先初始化 CoreUserSDK。

### 6.2 依赖
```kotlin
dependencies {
    implementation(files("libs/nexus-core-user-<version>.aar"))
    implementation(files("libs/nexus-payment-<version>.aar"))
}
```

### 6.3 初始化
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

### 6.4 获取商品
```kotlin
val products = PaymentSDK.getProducts(forceRefresh = true)
```

商品展示信息来自两部分：

- 后台 `/m/v7/iap/list`：`market_product_id`、`product_type`、`coins_granted`。
- Google Play Billing：价格、币种、本地化价格、订阅周期、试用信息。

### 6.5 订阅页
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

校验规则：

- `showTerms = true` 时 `termsUrl` 必填。
- `showPrivacy = true` 时 `privacyUrl` 必填。
- 支付方式配置错误时不自动兜底。

### 6.6 购买和恢复
```kotlin
PaymentSDK.purchase(
    activity = activity,
    product = products.first(),
    channel = PaymentChannel.GOOGLE_PLAY
) { result ->
    if (result.success) {
        // 购买成功，权益已处理
    } else {
        // 购买失败或取消
    }
}
```

```kotlin
val restoreResult = PaymentSDK.restore(PaymentChannel.GOOGLE_PLAY)
```

### 6.7 Payment 验证
- CoreUserSDK 已初始化并能取到 uid。
- `/m/v7/iap/list` 能返回商品。
- Google Play Billing 能查询到商品价格。
- 商品 ID 和 Google Play Console 商品 ID 一致。
- 真实支付从 Google Play 测试轨道安装 App。
- 购买成功后调用 `/pp/v7/gp/os`。
- 订阅页能展示商品、共享应用、协议入口和恢复购买。

## 7. CrossPromoSDK 接入
### 7.1 适用场景
接入 CrossPromoSDK 后，业务方可以使用：

- 同账号应用推荐页。
- 排除当前 App。
- 已安装 App 跳转。
- 未安装 App 商店跳转。
- Deep Link 参数解析。
- 导量归因和用户关联事件。

CrossPromoSDK 必须先初始化 CoreUserSDK；如果需要上报互导事件，建议同时初始化 GrowthAnalyticsAdSDK。

### 7.2 依赖
```kotlin
dependencies {
    implementation(files("libs/nexus-core-user-<version>.aar"))
    implementation(files("libs/nexus-cross-promo-<version>.aar"))

    // 如果需要事件上报，建议接入
    implementation(files("libs/nexus-growth-analytics-ad-<version>.aar"))
}
```

### 7.3 AndroidManifest
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

### 7.4 初始化
```kotlin
CoreUserSDK.init(context, coreUserConfig)

CrossPromoSDK.init(
    context,
    CrossPromoConfig(sourceProductId = "7")
)
```

### 7.5 展示推荐页
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

### 7.6 处理 Deep Link
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

### 7.7 CrossPromo 验证
- CoreUserSDK 已初始化并能取到 uid。
- 推荐页能展示同账号应用列表。
- 当前 App 已从列表中排除。
- 已安装目标 App 能直接跳转。
- 未安装目标 App 能跳转商店链接。
- Deep Link 打开目标 App 后能解析并缓存归因参数。
- 登录后能触发用户关联事件。
