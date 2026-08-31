# Nexus SDK Android 业务接入说明
本文面向接入 Nexus SDK 的业务 App。SDK按模块提供能力，可根据需求接入其中1个或多个模块。

当前版本：`0.0.12`

## 1. 模块选择
| 模块 | AAR | 适用场景 | 前置依赖 |
| --- | --- | --- | --- |
| CoreUserSDK | [nexus-core-user-0.0.12.aar](https://raw.githubusercontent.com/harden-l/nexus-sdk-android/main/dist/android/aar/0.0.12/nexus-core-user-0.0.12.aar) | 设备 ID、游客登录、邮箱密码登录、用户信息、邮箱绑定、余额和金币 | 无 |
| GrowthAnalyticsAdSDK | [nexus-growth-analytics-ad-0.0.12.aar](https://raw.githubusercontent.com/harden-l/nexus-sdk-android/main/dist/android/aar/0.0.12/nexus-growth-analytics-ad-0.0.12.aar) | BI/Firebase/AppsFlyer 事件、AdMob 广告、归因 | CoreUserSDK |
| PaymentSDK | [nexus-payment-0.0.12.aar](https://raw.githubusercontent.com/harden-l/nexus-sdk-android/main/dist/android/aar/0.0.12/nexus-payment-0.0.12.aar) | 商品、三套订阅页模板、Google Play Billing、订单校验、权益 | CoreUserSDK、GrowthAnalyticsAdSDK |
| CrossPromoSDK | [nexus-cross-promo-0.0.12.aar](https://raw.githubusercontent.com/harden-l/nexus-sdk-android/main/dist/android/aar/0.0.12/nexus-cross-promo-0.0.12.aar) | 应用互导推荐页、Deep Link、导量归因 | CoreUserSDK、GrowthAnalyticsAdSDK |

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
| Firebase 配置 | 仅 Firebase 事件需要；业务 App 必须应用 Google Services 插件，并提供真实 `google-services.json`。 |
| AppsFlyer Dev Key | 仅 AppsFlyer 事件需要。 |
| DataEye App ID / Server URL | 仅 BI(DataEye) 事件需要；Server URL 不传时使用 DataEye 默认地址。 |
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
    // 只添加业务 App 实际启用的平台依赖
    implementation("com.google.android.gms:play-services-ads:25.3.0")
    implementation("com.google.firebase:firebase-analytics:23.0.0")
    implementation("com.appsflyer:af-android-sdk:6.17.5")
    implementation("io.github.dataeyesdk:dataeye-android-sdk:2.8.3")

    // PaymentSDK
    implementation("com.android.billingclient:billing-ktx:8.0.0")
}
```

只接入 CoreUserSDK 时无需添加上述第三方依赖。接入 PaymentSDK 或 CrossPromoSDK 时，必须同时添加其前置 AAR。

启用 Firebase 时，还需要在业务工程根 Gradle 配置中声明 Google Services 插件版本：

```kotlin
plugins {
    id("com.google.gms.google-services") version "4.4.4" apply false
}
```

然后在业务 App module 的 `build.gradle.kts` 中应用插件：

```kotlin
plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}
```

如果业务工程仍使用 `buildscript` 方式，则在根工程加入 `classpath("com.google.gms:google-services:4.4.4")`，并在 App module 使用 `apply(plugin = "com.google.gms.google-services")`。Google Services 插件只能由宿主 App 应用，不能打进 SDK AAR。

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

推荐账号流程：

1. 用户未主动选择邮箱登录时，调用 `silentLoginAsync()` 创建或恢复游客用户。
2. 游客登录成功后根据 `SDKUser.emailBound` 决定是否展示绑定入口；登录本身不会强制弹出绑定页面。
3. 绑定邮箱时必须同时设置密码。绑定成功后，邮箱成为当前 UID 的登录凭证。
4. 用户在新设备、重装 App 或其他接入同一账号体系的 App 中主动登录时，直接调用 `loginWithEmailAsync()`，不要先创建新的游客用户。
5. 邮箱登录成功后 SDK 会自动拉取用户信息，业务方使用返回的 `SDKUser` 更新登录态。

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

游客登录也可以在后台线程使用同步 API：

```kotlin
val user = CoreUserSDK.silentLogin()
```

邮箱密码登录：

```kotlin
CoreUserSDK.loginWithEmailAsync(
    email = "user@example.com",
    password = "user-password"
) { result ->
    result.onSuccess { user ->
        // 邮箱登录成功
    }.onFailure { error ->
        // 邮箱或密码错误
    }
}
```

`email` 和 `password` 均为必填。同步 API 为 `loginWithEmail(email, password)`，只能在后台线程调用。登录请求会携带本地已有 uid；本地没有 uid 时发送空字符串，服务端根据邮箱密码恢复对应用户。

登录后 SDK 会拉取一次用户信息。密码只用于当前绑定或登录请求，SDK 不会持久化密码；debug 日志中的密码会被脱敏。

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

`fetchUserInfoAsync()` 返回用户资料和当前余额 `balance`，并刷新 SDK 本地用户缓存。SDK 会将用户信息接口返回的 `balance` 乘以 `100` 后写入 `SDKUser.balance`，例如接口返回 `20` 时业务方读取到 `2000`。`SDKUser.balance` 类型为 `Double`，支持小数余额。

使用 SDK 内置邮箱绑定弹窗：

```kotlin
CoreUserSDK.ensureEmailBound(activity) { result ->
    // ALREADY_BOUND / BOUND / CANCELLED / USER_INFO_FAILED / BIND_FAILED
}
```

弹窗会同时要求用户输入邮箱和密码。密码用于设置邮箱登录凭证，绑定成功后可调用 `loginWithEmail()` 登录；SDK 不会在本地持久化密码。

调用绑定接口前应先完成游客登录或其他类型登录，确保 SDK 中存在当前用户 UID。同一邮箱只能按服务端账号规则绑定；邮箱已被占用、密码不符合规则等情况会通过失败结果返回，业务方应向用户展示可理解的错误提示。

使用自定义 UI 直接绑定：

```kotlin
CoreUserSDK.bindEmailAsync(
    email = "user@example.com",
    password = "user-password"
) { result ->
    // 处理绑定结果
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
- `ConsumeChatCoinsResult` 中的 `cost`、`beforeCoins`、`afterCoins` 和 `balance` 保持扣金币接口返回的原始单位，不执行 `×100`。
- 扣除成功后 SDK 不直接修改本地 `SDKUser.balance` 缓存；业务方如需刷新余额，调用 `fetchUserInfoAsync()`。

## 5. GrowthAnalyticsAdSDK 接入
### 5.1 第三方平台配置

Firebase、AppsFlyer 和 DataEye 可以独立启用，也可以同时启用。`AnalyticsConfig` 中三个开关默认均为 `true`，业务方应显式关闭未使用的平台，避免误以为事件已经真实上报。

| 平台 | 必须配置 | `AnalyticsConfig` | 未完整配置时的行为 |
| --- | --- | --- | --- |
| Firebase | `firebase-analytics` 依赖、Google Services 插件、与包名匹配的 `google-services.json` | `enableFirebase = true` | Firebase 初始化会依赖宿主 App 配置；SDK 不内置 Firebase 配置文件。 |
| AppsFlyer | `af-android-sdk` 依赖、业务 App 的 Dev Key；归因场景再配置 OneLink/App Links | `enableAppsflyer = true`、`appsflyerDevKey = "<DEV_KEY>"` | Dev Key 为空时 SDK 使用 Mock Provider，不会向 AppsFlyer 发送数据。 |
| DataEye | `dataeye-android-sdk` 依赖、DataEye App ID；自定义上报域名时再提供 Server URL | `enableBI = true`、`dataEyeAppId = "<APP_ID>"`、可选 `dataEyeServerUrl` | App ID 为空时 SDK 使用 Mock Provider，不会向 DataEye 发送数据。 |

生产环境必须调用带 `Context` 的 `GrowthAnalyticsAdSDK.init(context, config, activityProvider)`，该重载才会根据上述配置创建真实 Provider。只接入一个平台时，将另外两个平台的开关设置为 `false`。

#### Firebase

将 Firebase Console 下载的 `google-services.json` 放到业务 App module 根目录，并在业务 App 的 Gradle 配置中启用 Google Services 插件：

```kotlin
plugins {
    id("com.google.gms.google-services")
}
```

Firebase Console 中 Android App 的包名必须与业务 App 的 `applicationId` 一致。SDK AAR 不内置 `google-services.json`，也不替业务 App 创建 Firebase 项目。

#### R8 / ProGuard 混淆

`nexus-growth-analytics-ad` AAR 已通过 `consumer-rules.pro` 携带 Nexus Provider 所需的元数据和类名规则，业务 App 启用 `minifyEnabled = true` 时会自动合并。Firebase、AdMob、AppsFlyer 和 DataEye 的 Maven 依赖也会分别携带并合并其官方消费端规则。

业务方不应默认添加以下宽泛规则：

```proguard
-keep class com.google.** { *; }
-keep class com.appsflyer.** { *; }
-keep class cn.dataeye.** { *; }
```

这些规则会阻止大量无用代码缩减。只有第三方 SDK 官方文档针对当前版本明确要求额外规则，或 Release 混淆包出现可复现问题时，才应在业务 App 的 `proguard-rules.pro` 中补充对应的最小规则。使用本地 AAR 时，第三方 Maven 依赖仍必须按实际启用平台声明，否则其官方消费端规则也不会进入最终构建。

#### AppsFlyer

`appsflyerDevKey` 使用 AppsFlyer 后台当前 App 的 Dev Key，不要填写 Apple App ID、包名或 OneLink 地址。需要 OneLink/Deep Link 归因时，业务 App 还要在 AppsFlyer 后台配置 Android App，并在 Manifest 中配置对应的 App Links 或 intent-filter。

#### DataEye

`dataEyeAppId` 使用 DataEye 后台分配给当前 App 的 App ID。`dataEyeServerUrl` 为可选自定义上报地址，不传时使用 DataEye SDK 默认地址。DataEye 的 uid 来自 `GrowthAnalyticsAdSDK.setUser(user)`，因此建议在 CoreUser 登录成功后设置用户。

#### AdMob

如果启用 AdMob，业务 App 必须在自己的 `AndroidManifest.xml` 中配置 AdMob App ID。这个值属于宿主 App，不能由 SDK AAR 写死，否则多个业务 App 会共用错误的 AdMob 应用配置：

```xml
<application>
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy" />
</application>
```

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
        dataEyeAppId = "<DATAEYE_APP_ID>",
        dataEyeServerUrl = null,
        appsflyerDevKey = "<APPSFLYER_DEV_KEY>",
        debug = true
    ),
    activityProvider = { currentActivity }
)
```

配置检查：

- 只启用 Firebase：`enableFirebase = true`，`enableBI = false`，`enableAppsflyer = false`。
- 只启用 AppsFlyer：`enableAppsflyer = true` 并填写 `appsflyerDevKey`，其余事件平台开关设为 `false`。
- 只启用 DataEye：`enableBI = true` 并填写 `dataEyeAppId`，其余事件平台开关设为 `false`。
- 同时启用时，保留三个真实配置；`GrowthAnalyticsAdSDK.getProviders()` 可用于调试确认创建出的 Provider。
- `debug = true` 只控制 Nexus SDK 日志，Firebase、AppsFlyer 和 DataEye 各自的调试模式仍按对应官方 SDK 配置。

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

广告收益由 AdMob Paid Event 触发 `reportAdRevenue()`。SDK 内部事件名是 `ad_revenue`，发送到 Firebase、AppsFlyer 和 DataEye 时均映射为 `ad_imp`。

说明：

- `GrowthAnalyticsAdSDK.track(...)` 可以分发普通事件到已注入或已启用的 Provider。
- 当前按业务需求，Firebase / AppsFlyer 只发送 `ad_imp`，其它 Nexus 内部事件不会发送到这两个平台。
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
import com.nexus.sdk.payment.subscription_template.SubscriptionPageTemplates
import com.nexus.sdk.payment.subscription_template.SubscriptionSharedAppsConfig

PaymentSDK.showSubscriptionPage(
    activity,
    SubscriptionPageConfig(
        templateId = SubscriptionPageTemplates.AURORA,
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
        ctaText = "Continue"
    )
)
```

`templateId` 用于切换 SDK 内置页面模板：

| 模板 ID | 常量 | 模板说明 |
| --- | --- | --- |
| `aurora` | `SubscriptionPageTemplates.AURORA` | 明亮现代风格，突出当前权益、共享应用和购买选项；默认模板。 |
| `midnight` | `SubscriptionPageTemplates.MIDNIGHT` | 深色沉浸风格，适合会员、内容和创作类产品。 |
| `minimal` | `SubscriptionPageTemplates.MINIMAL` | 清爽紧凑风格，适合工具类应用或商品较多的页面。 |

未传、传空值或传入未知模板 ID 时会回退到 `aurora`。Android 与 iOS 使用相同模板 ID，业务方可直接由远程配置控制两端样式。

切换模板只需要修改 `templateId`，其余页面配置和调用方式不变。

打开订阅页后，SDK 会自动完成以下流程，业务方不需要提前获取商品或手动调用购买接口：

- 从 Nexus 后台 `/m/v6/iap/list` 获取商品的 `market_product_id`、`product_type` 和 `coins_granted`；`coinsGranted` 类型为 `Double?`，保留接口原始值并支持小数赠币。
- 页面按 `product_type` 自动分组：`2` 展示为订阅方案，`1` 展示为积分包或一次性内购。
- 订阅页展示金币时统一使用 `coins_granted × 100`，例如接口返回 `20` 时页面展示 `2000`；购买判断和订单处理仍使用接口原始值。
- 从 Google Play Billing 获取价格、币种、本地化价格、订阅周期和试用信息，并与后台商品合并。
- 获取关联应用并展示 Membership Share 区域。
- 用户点击 CTA 后发起购买，购买成功后完成服务端订单校验和权益处理。
- `showRestore = true` 时由页面提供恢复入口并执行恢复流程。
- Google Play 订单校验在后台线程执行，成功后 SDK 会确认订阅/非消耗品，或消耗带 `coins_granted` 的金币商品。
- App 启动时 SDK 会查询未完成订单，恢复 Pending 完成和进程重启期间遗漏的购买。
- 权益和交付记录按 `productId + uid` 持久化，防止切换用户时串用权益或重复交付。
- 服务条款和隐私协议默认展示，分别使用 `https://www.crypsiscollectiveinc.com/terms.html` 和 `https://www.crypsiscollectiveinc.com/privacy.html`；点击后由系统浏览器打开。

校验规则：

- `showTerms`、`showPrivacy` 默认均为 `true`，业务方可显式设为 `false` 隐藏对应入口。
- `termsUrl`、`privacyUrl` 已提供上述默认值；业务方可以覆盖，但入口开启时不能传空字符串。
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
