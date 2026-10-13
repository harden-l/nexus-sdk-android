# Nexus SDK Android 业务接入说明（共享账号 API v1）

本文面向 Android 业务 App，沿用按模块接入的方式。可按需启用账号、统计广告、支付和应用推荐能力，并引入对应前置依赖。

当前 Android 版本：`0.1.0`（2026-10-13）。适用新版共享账号 `/api/v1` 协议；需要已开通 v1 路由的服务。四个模块使用配套 `0.1.0` AAR，不要与 `0.0.x` 的账号、支付或推荐模块混用。

## 1. 模块选择

| 模块 | AAR | 适用场景 | 前置依赖 |
| --- | --- | --- | --- |
| CoreUserSDK | [nexus-core-user-0.1.0.aar](https://raw.githubusercontent.com/harden-l/nexus-sdk-android/0.1.0/dist/android/aar/0.1.0/nexus-core-user-0.1.0.aar) | 游客/邮箱/手机号登录、用户信息、绑定、余额、扣费、周积分 | 无 |
| GrowthAnalyticsAdSDK | [nexus-growth-analytics-ad-0.1.0.aar](https://raw.githubusercontent.com/harden-l/nexus-sdk-android/0.1.0/dist/android/aar/0.1.0/nexus-growth-analytics-ad-0.1.0.aar) | Firebase、AppsFlyer、DataEye 事件，AdMob 广告和归因 | CoreUserSDK |
| PaymentSDK | [nexus-payment-0.1.0.aar](https://raw.githubusercontent.com/harden-l/nexus-sdk-android/0.1.0/dist/android/aar/0.1.0/nexus-payment-0.1.0.aar) | 三套订阅页、Google Play Billing、订单校验与恢复购买 | CoreUserSDK、GrowthAnalyticsAdSDK AAR；Growth 无需初始化 |
| CrossPromoSDK | [nexus-cross-promo-0.1.0.aar](https://raw.githubusercontent.com/harden-l/nexus-sdk-android/0.1.0/dist/android/aar/0.1.0/nexus-cross-promo-0.1.0.aar) | 关联应用推荐、Deep Link、导量归因 | CoreUserSDK、GrowthAnalyticsAdSDK |

产物统一由 [Android 产物仓库](https://github.com/harden-l/nexus-sdk-android) 提供。仅账号功能只需 CoreUser AAR；支付功能需带上 Growth AAR，这是当前代码的类依赖，不表示必须启用统计或广告。

## 2. 通用准备

向后台或 SDK 提供方确认以下配置：

| 配置 | 用途 |
| --- | --- |
| `productId` | 当前 App 的后台产品 ID；明文模式发送为 `ProductId` 请求头 |
| `productName` | 当前产品名称；加密模式发送为 `Product` 请求头 |
| `accountName` | 企业账号名称，用于查询关联应用；默认 `test` 仅供测试，上线前替换为真实值 |
| `apiBaseUrl` | 已开通新版接口的服务域名，不带 `/api/v1`；SDK 自动拼接路径 |
| `encryptionKey` | 当前产品的 AES 密钥；UTF-8 编码后必须为 16、24 或 32 字节 |
| `gt` | 可选注册赠送梯度：1/2/3 分别赠送原始金币 0.1/0.2/0.3，对应显示 10/20/30 |
| Firebase 配置 | 启用 Firebase 时提供匹配包名的 `google-services.json`，并应用 Google Services 插件 |
| AppsFlyer Dev Key | 启用 AppsFlyer 时配置；Deep Link 场景同时配置 OneLink/App Links |
| DataEye App ID / Server URL | 启用 BI 时提供 App ID；Server URL 可不传 |
| AdMob App ID / Ad Unit ID | 启用广告时配置宿主 App ID 和各格式对应的广告位 |
| Google Play 商品 | 后台商品 ID 与 Play Console 商品一致，配置好价格、订阅基础方案及服务端商店通知 |
| Deep Link Scheme | 使用关联应用跳转或 Deep Link 归因时配置 |

测试服务：`https://aishare.stoahayaamhsothy.com`，产品 ID `7`，产品名称 `TEST PRODUCT`。生产域名、产品和密钥由后台另行提供，不要沿用旧版接口域名推断新版生产配置。

通信约定：

- `encrypt` 默认 `true`，同时必须提供当前产品密钥。不同产品分别配置自己的密钥。
- AES/CBC/PKCS5Padding，IV 使用密钥前 16 字节，密文使用 Base64。
- 登录请求体始终是明文 JSON；`encrypt=true` 时登录响应仍需解密，SDK 自动处理。务必使用 HTTPS。
- 其他已集成接口按 `encrypt` 配置加密请求并解密响应；SDK 兼容服务端明文错误对象。
- SDK 内置接口自动管理 Bearer 请求头；业务自有认证请求可通过 `getToken()` 获取 Token。不要在请求体手动传 UID。
- `init(...)` 只初始化配置和本地会话，不自动登录、不拉取动态开关配置。
- UI 线程优先调用 `*Async` 方法，其回调在主线程执行；同步网络方法只能在后台线程调用。

## 3. AAR 依赖

业务工程要求：`minSdk >= 23`、建议 `compileSdk 36`、JDK 17；Gradle 仓库包含 `google()` 和 `mavenCentral()`。

将配套 AAR 放入 App 模块的 `libs/`，按模块选择引入：

```kotlin
dependencies {
    implementation(files("libs/nexus-core-user-0.1.0.aar"))
    // 按需引入；Payment / CrossPromo 同时需要 Growth AAR
    implementation(files("libs/nexus-growth-analytics-ad-0.1.0.aar"))
    implementation(files("libs/nexus-payment-0.1.0.aar"))
    implementation(files("libs/nexus-cross-promo-0.1.0.aar"))
}
```

本地 AAR 不会自动解析 Maven 传递依赖。根据启用的能力添加：

```kotlin
dependencies {
    // 广告与事件平台：仅添加实际使用的平台依赖，并关闭未使用的平台开关
    implementation("com.google.android.gms:play-services-ads:25.3.0")
    implementation("com.google.firebase:firebase-analytics:23.0.0")
    implementation("com.appsflyer:af-android-sdk:6.17.5")
    implementation("io.github.dataeyesdk:dataeye-android-sdk:2.8.3")

    // PaymentSDK
    implementation("com.android.billingclient:billing-ktx:8.0.0")
}
```

只接 CoreUser 不需要上述第三方依赖。Payment 不会初始化 Growth；未初始化 Growth 时跳过事件上报，商品、订阅页和支付流程仍可使用。

启用 Firebase 时，在根工程声明 Google Services 插件：

```kotlin
plugins {
    id("com.google.gms.google-services") version "4.4.4" apply false
}
```

在业务 App 模块应用插件，将真实 `google-services.json` 放到该模块根目录：

```kotlin
plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}
```

Google Services 插件由宿主 App 应用，不能打进 AAR。若使用旧式 `buildscript` 配置，可在根工程添加 `classpath("com.google.gms:google-services:4.4.4")`，在 App 模块应用同名插件。

## 4. CoreUserSDK 接入

### 4.1 初始化

以下使用测试产品配置，`gt = 1` 仅演示注册赠送；不需要赠送时不传：

```kotlin
import com.nexus.sdk.coreuser.init.CoreUserConfig
import com.nexus.sdk.coreuser.init.CoreUserSDK

CoreUserSDK.init(
    context,
    CoreUserConfig(
        productId = "7",
        productName = "TEST PRODUCT",
        accountName = "test",
        apiBaseUrl = "https://aishare.stoahayaamhsothy.com",
        encrypt = true,
        encryptionKey = "1b8df48c1fa64ce28a2e8133dffe600c",
        gt = 1,
        debug = false
    )
)
```

上线前替换为当前产品的正式域名、产品 ID/名称、企业账号名和密钥。测试密钥仅用于上述测试产品。

| 字段 | 是否必填 / 默认值 | 说明 |
| --- | --- | --- |
| `productId` | 必填 | 后台产品 ID |
| `productName` | 构造时提供 | 产品名称；加密模式必须与后台匹配 |
| `accountName` | 默认 `test` | 关联应用查询使用；正式接入替换为真实账号名 |
| `apiBaseUrl` | 必填 | 服务域名，不含 `/api/v1` |
| `version` | 默认 `1.0.0` | 初始化优先读取宿主 App 的 versionName；读取失败时使用此值 |
| `country` | 设备 Locale | 国家/地区；Locale 为空时默认 `US` |
| `language` | 设备 Locale | 语言；Locale 为空时默认 `en` |
| `encrypt` | 默认 `true` | 控制通用请求加密和响应解密；登录请求体例外 |
| `encryptionKey` | 加密时必填 | UTF-8 编码后为 16/24/32 字节；不是按十六进制文本解码 |
| `debug` | 默认 `false` | Nexus SDK 调试日志；正式环境保持关闭 |
| `gt` | 默认不传 | 1/2/3 对应原始金币 0.1/0.2/0.3，显示 10/20/30；其他值不赠送 |
| `appId` | 默认空字符串 | 兼容保留字段；不能替代 productId，也不能用于跨 App 共享登录态 |

`gt` 仅在游客首次创建用户且首次创建钱包时生效，已有钱包、重复登录、邮箱/手机号登录不补赠。

SDK 不提供远程开关 `getConfig()` 或开关获取接口；业务远程配置由接入方自行管理。`getSdkConfig()` 读取的是初始化参数，不是服务端动态配置。

### 4.2 登录

推荐流程：

1. 已有有效会话时复用会话；首次使用可调用游客登录。
2. 有账号的用户可直接邮箱/手机号登录，无需先创建游客。
3. 登录不会强制绑定；登录成功后根据用户资料决定是否展示绑定入口。
4. 会话失效时，游客允许重新游客登录；邮箱/手机号用户须重新输入账号密码，不会自动降级成游客。

游客登录：

```kotlin
CoreUserSDK.silentLoginAsync { result ->
    result.getOrNull()?.let { user ->
        // 使用 user.uid 更新业务登录态
        if (!user.userInfoSynced) {
            // 登录已成功，但用户资料未同步；稍后调用 fetchUserInfoAsync
        }
    }
    result.exceptionOrNull()?.let { error ->
        // 展示登录失败或重新登录入口
    }
}
```

邮箱登录：

```kotlin
CoreUserSDK.loginWithEmailAsync(
    email = "user@example.com",
    password = "user-password"
) { result ->
    result.getOrNull()?.let { user ->
        // 登录成功，使用返回的用户资料
    }
    result.exceptionOrNull()?.let { error ->
        // 处理账号或密码错误；不自动创建游客账号
    }
}
```

手机号登录：

```kotlin
CoreUserSDK.loginWithPhoneAsync(
    phonePrefix = "86",
    phone = "<PHONE_NUMBER>",
    password = "user-password"
) { result ->
    // result.getOrNull() / result.exceptionOrNull()
}
```

邮箱和手机号登录只能登录已绑定、已设密身份。邮箱会去除首尾空白并转为小写；密码必须为 **8～72 字节、ASCII 33～126 可见字符**，不得 trim 后提交，空格不合法。SDK 不持久化密码。

同步方法为 `silentLogin()`、`loginWithEmail(email, password)`、`loginWithPhone(phonePrefix, phone, password)`，仅后台线程调用。

会话与本地用户：

```kotlin
val session = CoreUserSDK.getSessionInfo()
val cachedUser = CoreUserSDK.getCurrentUser()
// session.uid / loginType / expiresAt / isAuthenticated
// expiresAt 为 Unix 秒；SessionInfo 不包含 Token 原文
// cachedUser 是本地快照，最新资料需 fetchUserInfoAsync
```

SDK 使用 Android Keystore 加密保存 Token，按服务地址和产品隔离会话；并发游客登录会合并。只有 UID 的旧缓存不代表有效登录，升级后应重新建立会话。不要频繁主动执行邮箱/手机号登录作为健康检查，服务端新登录会使旧 Token 失效。

获取 Token，用于业务方自行调用同一账号服务的认证接口：

```kotlin
val token: String? = CoreUserSDK.getToken()
if (token != null) {
    val authorization = "Bearer $token"
    // 将 authorization 设置为业务请求的 Authorization 头
} else {
    // 当前没有本地有效会话，按业务流程先完成登录
}
```

`getToken()` 必须在 `CoreUserSDK.init(...)` 后调用，未初始化时抛 `CoreUserException`。该方法可在主线程读取，不发网络请求、不自动登录或刷新 Token。返回不带 `Bearer ` 前缀的原始字符串；未登录、本地到期、注销、清理本地会话或 SDK 已处理 401 后返回 `null`。返回值限定当前服务地址和产品。

非空只表示本地会话尚未到期，不保证服务端尚未吊销 Token。业务自有请求的 401 不会自动被 SDK 感知，应停止使用该凭证并引导重新登录，不能自动重放扣费等操作。请求前按需读取，不长期缓存、打印或上报 Token，也不要将它发送给无关域名。

业务错误通过 `CoreUserException` 返回：

```kotlin
import com.nexus.sdk.coreuser.init.CoreUserException

CoreUserSDK.fetchUserInfoAsync { result ->
    val error = result.exceptionOrNull()
    if (error is CoreUserException) {
        if (error.code == 401 || error.status == 401) {
            // 会话已失效：提示重新登录，不自动重放扣费/领取等操作
        }
        // error.code：业务码；status：HTTP 状态；traceId：服务端追踪 ID
    }
}
```

过期会话在 SDK 中清除；服务端 401 同样使会话失效。已绑定身份在失效后调用游客入口会返回需要账号登录的错误，该本地错误的 `code` 可能为空，应处理所有失败分支。绑定、扣费、领取和注销请求不会自动重放。

### 4.3 获取用户信息

```kotlin
CoreUserSDK.fetchUserInfoAsync { result ->
    result.getOrNull()?.let { user ->
        val balanceText = user.balanceDecimal.stripTrailingZeros().toPlainString()
        val vipActive = user.isVip
        // 使用 balanceText / vipActive 刷新界面
    }
    result.exceptionOrNull()?.let { error ->
        // 保持资料未刷新状态，不将失败当作余额为零或会员失效
    }
}
```

调用成功后返回 `SDKUser` 并更新缓存：

| 字段 | 含义 |
| --- | --- |
| `uid` / `deviceId` | 服务端用户 ID / SDK 设备标识；SDK 不本地生成用户 UID |
| `email` / `emailBound` | 邮箱及绑定状态 |
| `phonePrefix` / `phone` / `phoneBound` | 手机区号、号码及绑定状态 |
| `balanceDecimal` | BigDecimal 展示余额，已是服务端原值 ×100，优先使用 |
| `balance` | 同样为 ×100 后的余额，Double 兼容字段 |
| `isVip` / `vipExpiredAt` | 当前产品会员状态 / 到期 Unix 秒 |
| `userInfoSynced` | 用户资料是否已成功同步；为 false 时不能凭默认字段判断会员或绑定状态 |

**余额不要再次乘以 100。** 例如服务端余额 `20`，SDK 返回展示余额 `2000`；服务端 `0.099`，SDK 返回 `9.9`。

### 4.4 绑定邮箱

SDK 内置邮箱和密码输入弹窗：

```kotlin
CoreUserSDK.ensureEmailBound(activity) { result ->
    // result.status:
    // ALREADY_BOUND / BOUND / CANCELLED / USER_INFO_FAILED / BIND_FAILED
    // 根据 result.user、result.error 更新页面
}
```

弹窗先检查用户资料，已绑定则直接返回；未绑定时要求输入邮箱与密码，允许取消。登录过程本身不强制打开弹窗。

自定义界面可使用：

```kotlin
CoreUserSDK.bindEmailAsync(
    email = "user@example.com",
    password = "user-password"
) { result ->
    if (result.isSuccess) {
        CoreUserSDK.fetchUserInfoAsync { refreshed ->
            // 以 refreshed 中的邮箱绑定状态更新业务界面
        }
    }
}
```

绑定 API 的 `password` 可省略，原带密码调用仍有效。但首次绑定不设密时，不能直接使用邮箱密码登录，建议始终提示用户设密。不要用空白字符串替代不传密码。

手机号绑定使用 `bindPhoneAsync(phonePrefix, phone, password, callback)`，密码同样可选。绑定前先确认当前登录账号；绑定不是邮箱/手机号所有权验证，也不等于跨产品会员共享。绑定超时先查询资料确认结果，不自动重复提交。

### 4.5 扣除聊天金币

`cost` 使用服务端原始金币单位，推荐 BigDecimal。每笔账单生成并保存一个幂等键，重试时复用原账单的键、金额和备注：

```kotlin
import java.math.BigDecimal
import java.util.UUID

// 创建账单时生成一次，并与账单一起保存；不能在每次重试时重新生成
val billKey = UUID.randomUUID().toString()
val cost = BigDecimal("0.025")

CoreUserSDK.consumeChatCoinsAsync(
    cost = cost,
    remark = "chat billing",
    idempotencyKey = billKey
) { result ->
    result.getOrNull()?.let { transaction ->
        // transaction.costDecimal：0.025 原始金币，对应显示 2.5
        // transaction.idempotencyKey：本次账单的幂等键
        CoreUserSDK.fetchUserInfoAsync { refreshed ->
            // 更新当前展示余额
        }
    }
    result.exceptionOrNull()?.let { error ->
        // 网络异常不等于未扣款；恢复同一账单后复用 billKey，不能创建新账单重试
    }
}
```

参数和结果：

- `cost > 0`；Double 入口兼容保留，精确计费建议使用 BigDecimal。
- `idempotencyKey` 为 1～128 位 ASCII 字母、数字、`-`、`_`。不传会自动生成，但业务账单应显式管理，避免不确定结果下重复扣费。
- `remark` 可不传，最大 255 字符；同一账单重试保持一致。
- `costDecimal / beforeCoinsDecimal / afterCoinsDecimal / balanceDecimal` 均为原始金币单位；Double 同名兼容字段同样不做 ×100。
- 扣费结果不会直接覆盖 `SDKUser.balance`；展示最新余额请调用 `fetchUserInfoAsync()`。
- 同键重试返回原交易结果，不代表当前钱包余额。SDK 不自动重放扣费请求。

### 4.6 用户注销

```kotlin
CoreUserSDK.logoutAsync { result ->
    if (result.isSuccess) {
        // 当前产品用户已注销，本地会话和用户资料已清理
    } else {
        // 处理失败，不提前显示注销成功
    }
}
```

`logoutAsync()` 实际调用 `/api/v1/account/deregister`，是**服务端当前产品用户注销**，不是仅退出本地登录。成功后不保留用于恢复会话的 UID，设备标识保留；服务端不会因此清空共享钱包或注销其他产品用户。重复注销可能返回 401，不视为幂等成功。

如果业务仅希望本地退出或切换账号，调用 `CoreUserSDK.clearLocalSession()`。该方法只清理本地会话和用户资料，不调用服务端注销，也不撤销服务端 Token；下一步由业务明确展示登录入口，不应立即调用游客登录代替用户选账号。

### 4.7 查询和领取订阅周积分

使用 SDK 订阅页时，页面自动查询和领取；以下接口供业务自定义领取入口使用，位于 CoreUserSDK，无需初始化 Payment。

查询资格：

```kotlin
import java.math.BigDecimal

CoreUserSDK.getWeeklyPointsInfoAsync { result ->
    result.getOrNull()?.let { info ->
        val displayPoints = info.weeklyPointsDecimal.multiply(BigDecimal("100"))
        if (info.canClaim) {
            // 展示 displayPoints 和领取按钮
        } else {
            // 根据 info.cannotClaimReason 展示不可领取状态
        }
    }
}
```

| WeeklyPointsInfo 字段 | 含义 |
| --- | --- |
| `isVip` | 当前产品是否有有效会员 |
| `marketProductId` | 服务端所选订阅 SKU；无有效订阅时可能为空 |
| `weeklyPointsDecimal` | 当前领取额度，BigDecimal 原始金币单位；显示时 ×100 |
| `canClaim` | 当前是否可以领取，不能单凭商品配置或 isVip 判断 |
| `cannotClaimReason` | 可领时为空；其他值见下表 |
| `weeklyPoints` | 已弃用的 Int 兼容值，小数会截断，新接入不使用 |

| 不可领取原因 | 业务展示建议 |
| --- | --- |
| `no_valid_subscription` | 引导选择订阅，不能调用领取 |
| `already_claimed` | 显示本周已领取 |
| `product_not_configured` | 当前订阅未配置周积分，不展示可领取操作 |

这些不可领状态是查询成功的业务结果，不是接口失败。领取时不传商品 ID，由服务端选择当前产品的有效订阅：

```kotlin
CoreUserSDK.claimWeeklyPointsAsync { result ->
    result.getOrNull()?.let { claim ->
        // claim.pointsDecimal：实际领取原始金币；显示时 ×100
        // claim.transactionId：领取记录 ID
        // claim.claimTimeSeconds：Unix 秒
        CoreUserSDK.fetchUserInfoAsync { refreshed ->
            // refreshed 中的 balanceDecimal 已 ×100，直接显示
        }
        CoreUserSDK.getWeeklyPointsInfoAsync { refreshed ->
            // 刷新领取按钮状态
        }
    }
    result.exceptionOrNull()?.let { error ->
        // 2130：已领取；2131：无有效订阅；2132：未配置周积分
        // 超时先查询资格和余额确认结果，不立即重复发起领取
    }
}
```

`WeeklyPointsClaimResult` 还提供 `marketProductId`、`beforeCoinsDecimal`、`afterCoinsDecimal`、`balanceDecimal`，金额均是原始单位；`success` 依据服务端成功响应生成。`points` 是弃用的截断 Int，`claimTime` 是兼容 RFC3339 时间字符串，优先使用 `pointsDecimal` 和 `claimTimeSeconds`。

同产品同用户每个 UTC 自然周最多领取一次；不按设备时区、不累计补领。商品周积分不是用户当前领取资格，不能取全部商品最大额度作为可领值。旧的带 SKU 领取重载已弃用，传非空 SKU 会明确失败。

金额单位汇总：

| 数据来源 | SDK 返回单位 | 页面处理 |
| --- | --- | --- |
| `SDKUser.balanceDecimal / balance` | 已 ×100 | 直接展示 |
| 扣费入参与结果 | 原始金币 | 展示时按业务换算，计费不乘 100 |
| 周积分查询/领取 Decimal 字段 | 原始金币 | 展示时 ×100 一次 |
| `Product.coinsGrantedDecimal / weeklyPointsDecimal` | 原始金币 | SDK 订阅页自动 ×100 |
| `Product.priceUsd` | 美分 | SDK 转成标注 USD 的参考价格，不参与金币倍率 |
| Billing 本地化金额 | 商店价格 | 原样展示，不乘 100 |

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
        debug = false
    ),
    activityProvider = { currentActivity }
)
```

`activityProvider` 应返回当前可展示广告的 Activity；宿主须随 Activity 前后台生命周期更新 `currentActivity`，销毁时清除引用。

配置检查：

- 只启用 Firebase：`enableFirebase = true`，`enableBI = false`，`enableAppsflyer = false`。
- 只启用 AppsFlyer：`enableAppsflyer = true` 并填写 `appsflyerDevKey`，其余事件平台开关设为 `false`。
- 只启用 DataEye：`enableBI = true` 并填写 `dataEyeAppId`，其余事件平台开关设为 `false`。
- 同时启用时，保留三个真实配置；`GrowthAnalyticsAdSDK.getProviders()` 可用于调试确认创建出的 Provider。
- `debug = true` 只控制 Nexus SDK 日志，Firebase、AppsFlyer 和 DataEye 各自的调试模式仍按对应官方 SDK 配置。

如果已接入 CoreUserSDK：

```kotlin
CoreUserSDK.silentLoginAsync { result ->
    result.getOrNull()?.let { user ->
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
- `REWARDED_INTERSTITIAL`
- `NATIVE`

全屏广告按 `format + adUnitId` 管理缓存。重复调用 `loadAd()` 时，如果已有缓存或正在加载，SDK 不会再次发起广告请求；并发传入的加载回调会在本次加载完成后统一返回。

`showAd()` 会先检查缓存：有缓存时立即展示；无缓存时自动开始加载，本次通过 `onFailed` 返回广告未就绪，业务方可在后续时机再次调用 `showAd()`。开屏、插屏和激励广告展示成功或展示失败后，SDK 会自动预加载下一条。同一广告正在展示时不会重复展示。

`ad_show` 只在广告实际展示成功后上报：开屏、插屏、激励和激励插屏对应 AdMob 的 `onAdShowedFullScreenContent()`，Banner 和 Native 对应广告曝光回调。仅调用 `showAd()`、广告未就绪、加载失败或展示失败时不会上报 `ad_show`。

## 6. PaymentSDK 接入

推荐使用 SDK 内置订阅页。业务方提供页面内容和支付配置，页面自动获取商品、合并商店详情、发起购买、校验订单和恢复购买，无需自行拼装商品列表。

### 6.1 接入顺序

1. 引入 CoreUser、Growth、Payment AAR 和 Billing 依赖。
2. 初始化 CoreUser；如用户选择邮箱/手机号账号，先完成该账号登录。
3. 初始化 Payment；只有需要事件上报时才额外初始化 Growth。
4. 注册页面事件，在需要时打开订阅页。
5. 购买、恢复或领取后，使用用户信息更新业务会员状态和余额。

游客可由 SDK 自动建立会话；已绑定账号会话失效时，应先引导重新登录再进入支付流程。

### 6.2 初始化

```kotlin
import com.nexus.sdk.payment.config.PaymentChannel
import com.nexus.sdk.payment.config.PaymentConfig
import com.nexus.sdk.payment.subscription.PaymentSDK

PaymentSDK.init(
    PaymentConfig(
        productId = "7",
        enabledChannels = listOf(PaymentChannel.GOOGLE_PLAY),
        defaultChannel = PaymentChannel.GOOGLE_PLAY
    )
)
```

必须先初始化 CoreUser，且两个模块的 `productId` 应一致。Payment 使用 CoreUser 提供的认证与网络配置；不会初始化 Growth。

| PaymentConfig 字段 | 默认值 / 说明 |
| --- | --- |
| `productId` | 必填，当前后台产品 ID |
| `platform` | `android` |
| `country` / `appVersion` | 可选，参与渠道规则匹配 |
| `defaultChannel` | `GOOGLE_PLAY`，必须包含在 enabledChannels 中 |
| `enabledChannels` | 默认仅 `GOOGLE_PLAY`，不能为空 |
| `fallbackChannels` | 默认空；仅在显式配置并通过校验时使用 |
| `rules` | 默认空；可配置地区、平台和版本条件下的渠道规则 |

Android 真实支付使用 Google Play Billing。不要将 `MOCK` 用于正式购买，也不要将 `APP_STORE` 配置为 Android 原生支付渠道。

### 6.3 展示订阅页

```kotlin
import com.nexus.sdk.payment.subscription_template.SubscriptionPageConfig
import com.nexus.sdk.payment.subscription_template.SubscriptionPageTemplates
import com.nexus.sdk.payment.subscription_template.SubscriptionSharedAppsConfig

PaymentSDK.showSubscriptionPage(
    activity,
    SubscriptionPageConfig(
        templateId = SubscriptionPageTemplates.AURORA,
        scene = "home_upgrade",
        title = "Upgrade to Pro",
        benefitDescription = "Unlock features in this product.",
        benefits = listOf("Premium tools", "More creative freedom"),
        sharedApps = SubscriptionSharedAppsConfig(
            title = "Related apps",
            description = "Explore other apps from this publisher."
        ),
        paymentChannels = listOf(PaymentChannel.GOOGLE_PLAY),
        showPaymentChannel = true,
        showRestore = true,
        ctaText = "Continue"
    )
)
```

三套模板使用相同业务流程，切换时只需修改 `templateId`：

| 模板 ID | 常量 | 说明 |
| --- | --- | --- |
| `aurora` | `SubscriptionPageTemplates.AURORA` | 明亮风格，订阅方案卡片与横向金币卡片 |
| `midnight` | `SubscriptionPageTemplates.MIDNIGHT` | 深色风格，订阅和内购以纵向卡片呈现 |
| `minimal` | `SubscriptionPageTemplates.MINIMAL` | 简洁风格，订阅列表与横向金币卡片 |

默认模板为 aurora；空字符串或未知 ID 回退到 aurora。三个 ID 延续原命名，页面业务以本 Android 版本为准。

`SubscriptionPageConfig` 参数：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `templateId` | `aurora` | 内置模板 ID |
| `scene` | 空字符串 | 业务入口标识 |
| `title` | `Upgrade to Pro` | 页面标题 |
| `benefitDescription` | `Manage your subscription and discover related apps.` | 当前产品权益描述 |
| `benefits` | 空列表 | 权益字符串数组，按模板样式展示为标签 |
| `sharedApps` | 默认关联应用配置 | 配置区域标题、描述；不传应用列表，列表由接口获取 |
| `paymentChannels` | 空列表 | 空时使用 PaymentConfig 解析出的渠道；显式配置时需为已注册的可用渠道 |
| `showPaymentChannel` | `true` | 是否显示渠道选择；隐藏不会改变实际支付渠道 |
| `showRestore` | `true` | 是否显示恢复购买入口 |
| `showTerms` / `showPrivacy` | 均为 `true` | 是否显示条款、隐私入口 |
| `termsUrl` | 见下文 | 条款 URL，入口开启时不能为空 |
| `privacyUrl` | 见下文 | 隐私 URL，入口开启时不能为空 |
| `ctaText` | `Start Pro` | 主购买按钮文案 |
| `restoreText` | `Restore` | 恢复入口文案 |
| `termsText` | `Terms` | 条款入口文案 |
| `privacyText` | `Privacy` | 隐私入口文案 |

`SubscriptionSharedAppsConfig` 只有 `title` 和 `description`，默认分别为 `Related apps` 和 `Explore other apps from this publisher. Membership is managed separately.`。属性名为兼容保留的 sharedApps，但区域表示**关联应用**，不表示跨产品会员权益共享。

默认条款地址为 `https://www.crypsiscollectiveinc.com/terms.html`，隐私地址为 `https://www.crypsiscollectiveinc.com/privacy.html`，点击后使用系统浏览器打开。业务可覆盖为自身有效地址。

### 6.4 页面自动处理流程

打开页面后，SDK 会自动完成：

- 请求 `/api/v1/iap/products`，按 `sort` 稳定排序，并区分订阅方案与一次性积分包。
- 先展示后台返回的商品权益，再合并 Billing 返回的本地化价格、币种、周期和试用信息。后台 `price_usd` 只作为标注 USD 的参考价格；未获取商店详情前不能购买。
- 新版 `weekly_points_enabled = 1` 表示按周领取，展示 `weeklyPointsDecimal ×100`；`2` 表示关闭周领，展示 `coinsGrantedDecimal ×100` 的购买到账权益。SDK 的 `weeklyPointsEnabled` 已映射为 Boolean。
- 查询当前用户的周积分领取资格，使用资格响应中的额度。无订阅时可显示订阅引导；已领取或未配置时不允许再次领取。领取成功后刷新用户资料和领取状态。
- 获取关联应用，显示当前企业账号下的应用信息；区域不承诺共享会员权益。
- 依据当前会员、订阅资格和本地已校验权益显示已拥有标识。消耗型金币包通常允许重复购买；已拥有标识不能代替商店和服务端最终判断。
- 发起 Google Play 购买、查询服务端订单状态；确认支付后由 SDK 完成确认/消耗等商店处理。查询订单本身不会给用户加金币。
- 恢复购买并补查未完成交易；Pending 保留为待确认状态，不提示用户重新购买同一订单。
- 点击关闭按钮或调用 `PaymentSDK.closeSubscriptionPage()` 关闭页面。

会员状态以 `fetchUserInfoAsync()` 的 `isVip / vipExpiredAt` 为准，余额以其 `balanceDecimal` 为准。钱包可能按后端规则跨产品共享，但会员按产品管理。不要仅凭购买回调在业务端自行加币或直接永久开通会员。

### 6.5 页面事件与余额刷新

```kotlin
import com.nexus.sdk.payment.subscription_template.SubscriptionPageEventName

val removePageListener = PaymentSDK.onSubscriptionPageEvent { event ->
    when (event.name) {
        SubscriptionPageEventName.PURCHASE_SUCCESS,
        SubscriptionPageEventName.RESTORE_SUCCESS,
        SubscriptionPageEventName.WEEKLY_POINTS_CLAIM_SUCCESS -> {
            CoreUserSDK.fetchUserInfoAsync { result ->
                result.getOrNull()?.let { user ->
                    // 使用 user.isVip 和 user.balanceDecimal 刷新业务界面
                }
            }
        }
        SubscriptionPageEventName.PURCHASE_FAILED -> {
            // 根据 event.state / event.params 展示失败或待确认状态
        }
        else -> Unit
    }
}

// 页面所属业务组件销毁时调用，避免重复监听或保留 Activity 引用
// removePageListener()
```

页面内置的领取操作会刷新余额和领取状态。上面示例用于同步订阅页之外的业务界面；若刷新失败，稍后重试查询，不重复购买或领取。

页面事件还包括展示、商品选择、渠道选择、购买点击/取消、恢复点击/失败、周积分领取点击/失败、关闭。事件监听不依赖 Growth 初始化。

### 6.6 订单状态与配置注意事项

服务端订单状态：

| status | 语义 | 业务处理 |
| --- | --- | --- |
| `10` | Pending，待支付或待确认 | 保留交易、等待通知或恢复查询，不引导重复付款 |
| `50` | 已支付 | 继续由 SDK 处理交付；刷新用户资料确认会员和余额 |
| `60` | 已退款 | 刷新资料，不继续按成功订单授予权益 |
| `70` | 已取消 | 不交付，允许用户在新的购买操作中重新选择 |
| `80` | 订阅已过期 | 刷新会员状态 |

订单时间戳使用 Unix 秒，金额与币种保持商店/服务端支付单位，不执行金币 ×100。真实商店通知可能延迟；商店付款完成而服务端仍 Pending 时，应等待确认。

接入注意事项：

- AAR 只提供客户端能力，不能替代 Play Console 商品配置和服务端支付通知配置。
- 后台商品 ID 必须与当前包名对应的 Play 商品一致；购买前必须获取有效商店详情。
- 不配置默认兜底渠道来掩盖错误配置；显式传入不支持的渠道应修正配置。
- 所有 UI 操作在主线程发起；自定义购买或查询场景中的同步网络方法应在后台执行。
- 订单确认不等于整个账号资料已刷新；购买、恢复和周积分领取后按需刷新用户信息。
- 不提供内置 pay/config 接口；页面内容由调用方通过 SubscriptionPageConfig 传入，商品及关联应用由 SDK 请求后台获取。

## 7. CrossPromoSDK 接入

### 7.1 AndroidManifest

目标 App 配置自己的 Deep Link：

```xml
<activity
    android:name=".MainActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="yourapp" android:host="promo" />
    </intent-filter>
</activity>
```

Android 11 及以上需要检查目标应用安装状态时，在 Manifest 的 `<queries>` 中声明对应目标包名。例如：

```xml
<queries>
    <package android:name="com.example.relatedapp" />
</queries>
```

SDK 推荐页 Activity 已由 AAR Manifest 注册，业务方不需要重复声明。

### 7.2 初始化

先完成 CoreUser 初始化；需要实际归因事件上报时，按第 5 节初始化 Growth，再设置登录用户：

```kotlin
import com.nexus.sdk.crosspromo.placement.CrossPromoConfig
import com.nexus.sdk.crosspromo.placement.CrossPromoSDK

CrossPromoSDK.init(
    context,
    CrossPromoConfig(
        sourceProductId = "7",
        campaign = "internal_cross_promo",
        defaultPlacement = "home"
    )
)
```

`sourceProductId` 使用当前后台产品 ID。推荐内容来自 `/api/v1/product/related-products`，请求所需 `account_name` 从 CoreUserConfig.accountName 获取；不在 CrossPromoConfig 中传 products。

### 7.3 展示推荐页

```kotlin
import com.nexus.sdk.crosspromo.placement.ShowPromoPageOptions

CrossPromoSDK.showPromoPage(
    activity,
    ShowPromoPageOptions(
        placement = "home",
        campaign = "internal_cross_promo",
        title = "Recommended Apps",
        description = "More apps from this account"
    )
)
```

页面每次打开会刷新关联应用并排除当前产品；`description` 展示在标题下方。无数据时显示空态，不需要业务方自行过滤列表。

已安装目标 App 优先 Deep Link 或应用启动入口；否则使用可用的商店地址。后台应提供正确的目标包名和跳转信息。

### 7.4 处理 Deep Link

目标 App 在首次启动和 `onNewIntent` 中处理传入链接：

```kotlin
intent.data?.toString()?.let { url ->
    CrossPromoSDK.handleIncomingPromoLink(url)
}
```

完成目标 App 的用户登录后处理待归因信息：

```kotlin
CoreUserSDK.silentLoginAsync { result ->
    result.getOrNull()?.let { user ->
        if (GrowthAnalyticsAdSDK.isInitialized()) {
            GrowthAnalyticsAdSDK.setUser(user)
        }
        CrossPromoSDK.flushPendingAttributionAfterLogin()
    }
    result.exceptionOrNull()?.let { error ->
        // 已绑定账号需要重新登录时，展示邮箱/手机号登录入口
    }
}
```

邮箱或手机号登录成功后也可执行相同的设置用户及归因处理。归因参数保存在本地并通过 Growth 上报 BI，新版登录请求不再携带旧的 att/st/level/is_has_sim/uid 参数。

Deep Link 中的来源 UID 只用于归因，不是目标 App 的登录凭证。不能据此直接登录来源用户；跨 App 关联身份及钱包共享按服务端账号配置和用户实际登录结果处理。
