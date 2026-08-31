package com.nexus.sdk.payment.subscription_template

import com.nexus.sdk.payment.subscription.PaymentSDK
import com.nexus.sdk.payment.config.PaymentContext
import com.nexus.sdk.payment.config.PaymentChannel
import com.nexus.sdk.payment.products.Product
import com.nexus.sdk.payment.products.ProductType
import com.nexus.sdk.payment.R
import com.nexus.sdk.coreuser.network.RelatedProduct
import com.nexus.sdk.coreuser.weekly_points.WeeklyPointsInfo
import com.nexus.sdk.coreuser.init.CoreUserSDK
import android.app.Activity
import android.content.res.ColorStateList
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.graphics.Outline
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import java.net.URL
import kotlin.concurrent.thread

class SubscriptionPageActivity : Activity() {
    private lateinit var config: SubscriptionPageConfig
    private lateinit var root: LinearLayout
    private lateinit var stateView: TextView
    private lateinit var actionHost: LinearLayout
    private var subscriptionScrollView: ScrollView? = null
    private var actionSummary: TextView? = null
    private lateinit var theme: SubscriptionPageTheme
    private var products: List<Product> = emptyList()
    private var membershipShareItems: List<SubscriptionSharedAppItem> = emptyList()
    private var selectedProduct: Product? = null
    private val productGroups = mutableListOf<RadioGroup>()
    private var selectedChannel: PaymentChannel? = null
    private var weeklyPointsInfo: WeeklyPointsInfo? = null
    private var state: SubscriptionPageState = SubscriptionPageState.LOADING
    private val carouselHandler = Handler(Looper.getMainLooper())
    private var carouselScrollView: HorizontalScrollView? = null
    private var carouselDots: LinearLayout? = null
    private var carouselItemCount: Int = 0
    private var carouselCurrentIndex: Int = 0
    private var carouselStepPx: Int = 0
    private val carouselRunnable = object : Runnable {
        override fun run() {
            val scrollView = carouselScrollView ?: return
            if (carouselItemCount <= 1 || carouselStepPx <= 0) return
            carouselCurrentIndex = (carouselCurrentIndex + 1) % carouselItemCount
            scrollView.smoothScrollTo(carouselCurrentIndex * carouselStepPx, 0)
            updateCarouselDots(carouselCurrentIndex)
            carouselHandler.postDelayed(this, CAROUSEL_INTERVAL_MS)
        }
    }
    private val primaryColor: Int get() = theme.primary
    private val pageBackgroundColor: Int get() = theme.pageBackground
    private val mutedTextColor: Int get() = theme.muted
    private val borderColor: Int get() = theme.border

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PaymentSDK.attachSubscriptionPage(this)
        config = PaymentSDK.getActiveSubscriptionPageConfig()
        theme = SubscriptionPageThemeResolver.resolve(config.templateId)
        window.statusBarColor = theme.pageBackground
        window.navigationBarColor = theme.pageBackground
        setContentView(createContentView())
        configureSystemBars()
        emit(SubscriptionPageEventName.PAGE_SHOW)
        loadPageData(forceRefresh = true)
    }

    override fun onDestroy() {
        stopCarousel()
        PaymentSDK.detachSubscriptionPage(this)
        super.onDestroy()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        updateState(SubscriptionPageState.CANCELLED)
        emit(SubscriptionPageEventName.PURCHASE_CANCEL)
        super.onBackPressed()
    }

    private fun createContentView(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val horizontalPadding = when (theme.id) {
                SubscriptionPageTemplates.MIDNIGHT -> 16
                SubscriptionPageTemplates.MINIMAL -> 20
                else -> 18
            }
            setPadding(dp(horizontalPadding), statusBarHeight() + dp(12), dp(horizontalPadding), dp(40))
            background = pageBackgroundDrawable()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        root.addView(header())

        stateView = label("", 13f, Color.DKGRAY, top = 14)
        stateView.visibility = View.GONE
        root.addView(stateView)

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            background = pageBackgroundDrawable()
            addView(
                root,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        subscriptionScrollView = scrollView
        actionHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val horizontalPadding = if (theme.id == SubscriptionPageTemplates.MIDNIGHT) 16 else 20
            setPadding(dp(horizontalPadding), dp(10), dp(horizontalPadding), dp(14))
            background = actionHostBackground()
            elevation = dp(12).toFloat()
        }
        actionHost.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val params = scrollView.layoutParams as? FrameLayout.LayoutParams ?: return@addOnLayoutChangeListener
            val requiredBottomSpace = actionHost.height + dp(12)
            if (params.bottomMargin != requiredBottomSpace) {
                params.bottomMargin = requiredBottomSpace
                scrollView.layoutParams = params
            }
        }
        return FrameLayout(this).apply {
            background = pageBackgroundDrawable()
            addView(
                scrollView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ).apply { bottomMargin = dp(110) }
            )
            addView(
                actionHost,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                )
            )
            addView(
                View(this@SubscriptionPageActivity).apply {
                    background = pageBackgroundDrawable()
                    elevation = dp(14).toFloat()
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    statusBarHeight(),
                    Gravity.TOP
                )
            )
            addView(
                floatingCloseButton(),
                FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END).apply {
                    topMargin = statusBarHeight() + dp(16)
                    marginEnd = dp(16)
                }
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun configureSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val lightStatus = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            val lightNavigation = android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(
                if (theme.dark) 0 else lightStatus,
                lightStatus
            )
            window.insetsController?.setSystemBarsAppearance(
                if (theme.dark) 0 else lightNavigation,
                lightNavigation
            )
        } else {
            val lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.decorView.systemUiVisibility = if (theme.dark) 0 else lightFlags
        }
    }

    private fun floatingCloseButton(): View {
        return ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            imageTintList = ColorStateList.valueOf(theme.title)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(dp(13), dp(13), dp(13), dp(13))
            background = roundedBackground(
                theme.surface,
                if (theme.id == SubscriptionPageTemplates.MIDNIGHT) theme.primary else borderColor,
                when (theme.id) {
                    SubscriptionPageTemplates.MIDNIGHT -> dp(8)
                    SubscriptionPageTemplates.MINIMAL -> dp(12)
                    else -> dp(24)
                }
            )
            contentDescription = "Close"
            elevation = dp(6).toFloat()
            setOnClickListener { closePage() }
        }
    }

    private fun pageBackgroundDrawable(): GradientDrawable {
        return when (theme.id) {
            SubscriptionPageTemplates.AURORA -> GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(245, 247, 255), Color.rgb(232, 249, 252), Color.WHITE)
            )
            SubscriptionPageTemplates.MIDNIGHT -> GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(7, 10, 17), Color.rgb(18, 22, 34), Color.rgb(10, 12, 18))
            )
            else -> GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.WHITE, Color.WHITE)
            )
        }
    }

    private fun loadPageData(forceRefresh: Boolean = false) {
        showLoading()
        thread(name = "subscription-page-load") {
            runCatching {
                val loadedProducts = PaymentSDK.getApiProducts(forceRefresh = forceRefresh)
                val relatedProducts = runCatching {
                    PaymentSDK.getRelatedProducts(forceRefresh = forceRefresh)
                }.getOrDefault(emptyList())
                val weeklyInfo = runCatching { PaymentSDK.getWeeklyPointsInfo() }.getOrNull()
                Triple(loadedProducts, relatedProducts, weeklyInfo)
            }.onSuccess { (loadedProducts, relatedProducts, weeklyInfo) ->
                runOnUiThread {
                    membershipShareItems = relatedProducts.toMembershipShareItems()
                    this@SubscriptionPageActivity.weeklyPointsInfo = weeklyInfo
                    Log.d(TAG, "page data loaded products=${loadedProducts.size} related=${relatedProducts.size}")
                    if (loadedProducts.isEmpty()) {
                        showEmpty()
                    } else {
                        applyProducts(loadedProducts, preserveSelection = false)
                        selectedChannel = resolveInitialChannel()
                        renderReady()
                        loadBillingProductDetails(loadedProducts)
                    }
                }
            }.onFailure { error ->
                runOnUiThread {
                    showError(error)
                }
            }
        }
    }

    private fun loadBillingProductDetails(apiProducts: List<Product>) {
        thread(name = "subscription-billing-products") {
            runCatching {
                PaymentSDK.enrichProductsWithBilling(apiProducts)
            }.onSuccess { enrichedProducts ->
                if (enrichedProducts == apiProducts || enrichedProducts.isEmpty()) return@onSuccess
                runOnUiThread {
                    applyProducts(enrichedProducts, preserveSelection = true)
                    renderReady()
                    Log.d(TAG, "billing product details applied count=${enrichedProducts.size}")
                }
            }.onFailure { error ->
                Log.w(TAG, "billing product details unavailable; keeping API products", error)
            }
        }
    }

    private fun applyProducts(loadedProducts: List<Product>, preserveSelection: Boolean) {
        val selectedProductId = selectedProduct?.marketProductId
        products = loadedProducts
        selectedProduct = if (preserveSelection && selectedProductId != null) {
            loadedProducts.firstOrNull { it.marketProductId == selectedProductId }
                ?: loadedProducts.firstOrNull()
        } else {
            loadedProducts.firstOrNull()
        }
    }

    private fun header(): View = when (theme.id) {
        SubscriptionPageTemplates.MIDNIGHT -> midnightHeader()
        SubscriptionPageTemplates.MINIMAL -> minimalHeader()
        else -> auroraHeader()
    }

    private fun auroraHeader(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(20), dp(52), dp(20))
            background = roundedBackground(theme.elevatedSurface, theme.primary, dp(24), dp(1))
            addView(heroIcon(R.drawable.aurora_hero_mark, "Premium studio"), LinearLayout.LayoutParams(
                dp(72), dp(72)
            ).apply { marginEnd = dp(16) })
            addView(LinearLayout(this@SubscriptionPageActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(config.title, 27f, theme.title).apply {
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 2
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun midnightHeader(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // The close control floats above this row, so the title can use the space beneath it.
            setPadding(dp(4), dp(12), dp(70), dp(12))
            addView(heroIcon(R.drawable.midnight_logo, "Unlimited membership"),
                LinearLayout.LayoutParams(dp(54), dp(54)).apply { marginEnd = dp(12) })
            addView(LinearLayout(this@SubscriptionPageActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(config.title, 18f, theme.title).apply {
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@SubscriptionPageActivity).apply {
                text = "ACTIVE"
                textSize = 12f
                setTextColor(Color.rgb(255, 210, 92))
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = roundedBackground(Color.rgb(38, 35, 25), Color.rgb(115, 88, 34), dp(12), dp(1))
            }, LinearLayout.LayoutParams(dp(74), dp(42)).apply { marginStart = dp(4) })
        }
    }

    private fun minimalHeader(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(4), dp(8), dp(12))
            addView(LinearLayout(this@SubscriptionPageActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(heroIcon(
                    R.drawable.minimal_logo,
                    "Premium studio mark"
                ), LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                    marginEnd = dp(10)
                })
                addView(label(templateEyebrow(), 15f, theme.title).apply {
                    typeface = Typeface.DEFAULT_BOLD
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
            addView(View(this@SubscriptionPageActivity).apply {
                setBackgroundColor(theme.accent)
            }, LinearLayout.LayoutParams(dp(54), dp(3)).apply { topMargin = dp(10) })
            addView(LinearLayout(this@SubscriptionPageActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(config.title, 28f, theme.title, top = 10).apply {
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 2
                    setLineSpacing(dp(2).toFloat(), 1f)
                })
            })
        }
    }

    private fun heroIcon(drawableRes: Int, description: String): View {
        return ImageView(this).apply {
            setImageResource(drawableRes)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = description
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(18).toFloat())
                }
            }
        }
    }

    private fun roundedOutline(radius: Int): ViewOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radius.toFloat())
        }
    }

    private fun templateEyebrow(): String {
        return when (theme.id) {
            SubscriptionPageTemplates.MIDNIGHT -> "CREATIVE WORKSPACE"
            SubscriptionPageTemplates.MINIMAL -> "PREMIUM STUDIO"
            else -> "NEXUS CREATIVE STUDIO"
        }
    }

    private fun templateSubhead(): String {
        return when (theme.id) {
            SubscriptionPageTemplates.MIDNIGHT ->
                "Generate, refine, and export with a faster creative workflow"
            SubscriptionPageTemplates.MINIMAL ->
                "Professional tools, shared access, and flexible creation credits."
            else -> "Create, edit, and share premium work across your creative apps."
        }
    }

    private fun addProducts() {
        addSubscriptionProducts()
        addOneTimeProducts()
    }

    private fun addSubscriptionProducts() {
        val subscriptions = products.filter { it.productType == ProductType.SUBSCRIPTION }
        if (subscriptions.isNotEmpty()) {
            val title = when (theme.id) {
                SubscriptionPageTemplates.MIDNIGHT -> "Membership"
                SubscriptionPageTemplates.MINIMAL -> "Premium plans"
                else -> "Choose your plan"
            }
            val subtitle = when (theme.id) {
                SubscriptionPageTemplates.MIDNIGHT -> "Unlock premium features and weekly points"
                SubscriptionPageTemplates.MINIMAL -> "Flexible access for your creative workflow"
                else -> "More access, more points, more possibilities"
            }
            addProductGroup(title, subtitle, subscriptions)
        }
    }

    private fun addOneTimeProducts() {
        val coinProducts = products.filter {
            it.productType == ProductType.CONSUMABLE && (it.coinsGranted ?: 0.0) > 0.0
        }
        if (coinProducts.isNotEmpty()) {
            addProductGroup(
                when (theme.id) {
                    SubscriptionPageTemplates.MIDNIGHT -> "Buy coins"
                    SubscriptionPageTemplates.MINIMAL -> "Coin packs"
                    else -> "Creation credits"
                },
                when (theme.id) {
                    SubscriptionPageTemplates.MIDNIGHT -> "One-time credits for your next creation"
                    SubscriptionPageTemplates.MINIMAL -> "Add credits whenever you need them"
                    else -> "One-time credits for premium generations and exports"
                },
                coinProducts
            )
        }
        val unlockProducts = products.filter {
            it.productType != ProductType.SUBSCRIPTION && it !in coinProducts
        }
        if (unlockProducts.isNotEmpty()) {
            addProductGroup(
                when (theme.id) {
                    SubscriptionPageTemplates.MIDNIGHT -> "One-time unlocks"
                    SubscriptionPageTemplates.MINIMAL -> "Permanent access"
                    else -> "Feature unlocks"
                },
                "Purchase once and keep access",
                unlockProducts
            )
        }
    }

    private fun addWeeklyPointsSection() {
        val info = weeklyPointsInfo ?: return
        val hasWeeklyProduct = products.any {
            it.productType == ProductType.SUBSCRIPTION && it.weeklyPointsEnabled && it.weeklyPoints > 0
        }
        if (!hasWeeklyProduct && info.weeklyPoints <= 0) return
        val canClaim = info.canClaim
        val configuredWeeklyPoints = products
            .filter { it.productType == ProductType.SUBSCRIPTION && it.weeklyPointsEnabled }
            .maxOfOrNull { it.weeklyPoints } ?: 0
        val weeklyPoints = maxOf(info.weeklyPoints, configuredWeeklyPoints)
        val displayedWeeklyPoints = weeklyPoints.toLong() * 100L
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(12), dp(14))
            background = roundedBackground(
                if (theme.id == SubscriptionPageTemplates.MIDNIGHT) theme.elevatedSurface else theme.surface,
                if (canClaim) primaryColor else borderColor,
                when (theme.id) {
                    SubscriptionPageTemplates.MIDNIGHT -> dp(8)
                    SubscriptionPageTemplates.MINIMAL -> dp(4)
                    else -> dp(18)
                },
                if (canClaim) dp(2) else dp(1)
            )
        }
        panel.addView(ImageView(this).apply {
            setImageResource(when (theme.id) {
                SubscriptionPageTemplates.MIDNIGHT -> R.drawable.midnight_weekly_gift
                SubscriptionPageTemplates.MINIMAL -> R.drawable.minimal_calendar
                else -> R.drawable.aurora_weekly_calendar
            })
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "Weekly points"
            if (theme.id == SubscriptionPageTemplates.MINIMAL) {
                background = roundedBackground(
                    Color.rgb(255, 239, 236),
                    Color.TRANSPARENT,
                    dp(12),
                    0
                )
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            clipToOutline = true
            outlineProvider = roundedOutline(dp(14))
        }, LinearLayout.LayoutParams(dp(58), dp(58)).apply { marginEnd = dp(12) })
        panel.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("Weekly points", 16f, theme.title).apply { typeface = Typeface.DEFAULT_BOLD })
            addView(label(
                when {
                    canClaim -> "${displayedWeeklyPoints} points ready to claim"
                    info.cannotClaimReason == "already_claimed" -> "Claimed for this week"
                    info.cannotClaimReason == "no_valid_subscription" -> "Subscribe to earn ${displayedWeeklyPoints} points weekly"
                    else -> "${displayedWeeklyPoints} points per week"
                }, 13f, theme.muted, top = 4)
            )
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val needsSubscription = !canClaim && info.cannotClaimReason == "no_valid_subscription"
        panel.addView(Button(this).apply {
            text = when {
                canClaim -> "Claim"
                needsSubscription -> "Subscribe"
                else -> "View status"
            }
            isAllCaps = false
            isEnabled = canClaim || needsSubscription
            textSize = 14f
            setTextColor(if (canClaim || needsSubscription) Color.WHITE else theme.muted)
            background = roundedBackground(
                if (canClaim || needsSubscription) primaryColor else theme.elevatedSurface,
                if (canClaim || needsSubscription) primaryColor else borderColor,
                if (theme.id == SubscriptionPageTemplates.MIDNIGHT) dp(6) else dp(14)
            )
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener {
                if (canClaim) claimWeeklyPoints() else guideToWeeklySubscription()
            }
        }, LinearLayout.LayoutParams(dp(94), dp(46)))
        root.addView(panel, fullWidthParams(top = 18))
        if (canClaim) {
            panel.animate().alpha(0.88f).setDuration(700L).withEndAction {
                panel.animate().alpha(1f).setDuration(700L).start()
            }.start()
        }
    }

    private fun claimWeeklyPoints() {
        val info = weeklyPointsInfo ?: return
        if (!info.canClaim) return
        emit(SubscriptionPageEventName.WEEKLY_POINTS_CLAIM_CLICK)
        thread(name = "subscription-weekly-points-claim") {
            runCatching { PaymentSDK.claimWeeklyPoints(info.marketProductId) }
                .onSuccess { result ->
                    weeklyPointsInfo = info.copy(canClaim = false, cannotClaimReason = "already_claimed")
                    runOnUiThread {
                        renderReady()
                        emit(
                            SubscriptionPageEventName.WEEKLY_POINTS_CLAIM_SUCCESS,
                            mapOf("points" to result.points, "transaction_id" to result.transactionId)
                        )
                    }
                }
                .onFailure { error ->
                    runOnUiThread {
                        emit(SubscriptionPageEventName.WEEKLY_POINTS_CLAIM_FAILED, mapOf("message" to error.message))
                        stateView.text = error.message.orEmpty()
                        stateView.visibility = View.VISIBLE
                    }
                }
        }
    }

    private fun guideToWeeklySubscription() {
        val product = products.firstOrNull {
            it.productType == ProductType.SUBSCRIPTION && it.weeklyPointsEnabled && it.weeklyPoints > 0
        } ?: return
        selectedProduct = product
        updateProductCardSelections(product.marketProductId)
        updateActionSummary()
        emit(SubscriptionPageEventName.PRODUCT_SELECT)
        val scrollView = subscriptionScrollView ?: return
        scrollView.post {
            val location = IntArray(2)
            val target = productGroups.asSequence()
                .flatMap { group -> (0 until group.childCount).asSequence().map { group.getChildAt(it) } }
                .firstOrNull { (it.tag as? Product)?.marketProductId == product.marketProductId }
                ?: return@post
            target.getLocationInWindow(location)
            val scrollLocation = IntArray(2)
            scrollView.getLocationInWindow(scrollLocation)
            scrollView.smoothScrollTo(0, (location[1] - scrollLocation[1] + scrollView.scrollY - dp(24)).coerceAtLeast(0))
        }
    }

    private fun addProductGroup(title: String, subtitle: String, items: List<Product>) {
        val midnightMembership = theme.id == SubscriptionPageTemplates.MIDNIGHT &&
            items.any { it.productType == ProductType.SUBSCRIPTION }
        if (!midnightMembership) {
            root.addView(sectionHeading(title, subtitle), fullWidthParams(top = 24))
        }
        val isCoinRail = items.all {
            it.productType == ProductType.CONSUMABLE && (it.coinsGranted ?: 0.0) > 0.0
        } &&
            theme.id != SubscriptionPageTemplates.MIDNIGHT
        val productGroup = RadioGroup(this).apply {
            orientation = if (isCoinRail) RadioGroup.HORIZONTAL else RadioGroup.VERTICAL
        }
        productGroups += productGroup
        items.forEachIndexed { index, product ->
            val badge = when {
                index == 0 && theme.id == SubscriptionPageTemplates.AURORA &&
                    product.productType == ProductType.SUBSCRIPTION -> "BEST VALUE"
                index == 0 && theme.id == SubscriptionPageTemplates.MIDNIGHT &&
                    product.productType == ProductType.SUBSCRIPTION -> "POPULAR"
                else -> badgeForProduct(product)
            }
            val selected =
                product.marketProductId == selectedProduct?.marketProductId || (selectedProduct == null && index == 0)
            val card = if (isCoinRail) {
                coinProductCard(productGroup, product, badge, selected)
            } else {
                productRadioCard(productGroup, product, badge, selected)
            }
            val params = if (isCoinRail) {
                RadioGroup.LayoutParams(
                    dp(coinCardWidthDp()),
                    dp(if (badge.isNullOrBlank()) 132 else 156)
                )
            } else {
                RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            params.setMargins(0, if (index == 0) dp(14) else dp(12), if (isCoinRail) dp(10) else 0, 0)
            productGroup.addView(card, params)
        }
        productGroup.setOnCheckedChangeListener { group, checkedId ->
            val product = group.findViewById<RadioButton>(checkedId)?.tag as? Product
                ?: return@setOnCheckedChangeListener
            selectedProduct = product
            updateProductCardSelections(product.marketProductId)
            updateActionSummary()
            emit(SubscriptionPageEventName.PRODUCT_SELECT)
        }
        if (midnightMembership) {
            root.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(16), dp(14), dp(14))
                background = roundedBackground(theme.elevatedSurface, theme.primary, dp(16), dp(2))
                addView(LinearLayout(this@SubscriptionPageActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(ImageView(this@SubscriptionPageActivity).apply {
                        setImageResource(R.drawable.midnight_membership_mark)
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                        contentDescription = "Membership"
                    }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(10) })
                    addView(LinearLayout(this@SubscriptionPageActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(label(title, 20f, theme.title).apply { typeface = Typeface.DEFAULT_BOLD })
                        addView(label(subtitle, 13f, theme.muted, top = 3))
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                })
                addView(productGroup, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(10) })
            }, fullWidthParams(top = 18))
        } else if (isCoinRail) {
            root.addView(HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(productGroup)
            }, fullWidthParams(top = 0))
        } else {
            root.addView(productGroup)
        }
    }

    private fun coinCardWidthDp(): Int {
        val columns = if (theme.id == SubscriptionPageTemplates.AURORA) 4 else 3
        val density = resources.displayMetrics.density
        val screenWidthDp = (resources.displayMetrics.widthPixels / density).toInt()
        val horizontalPadding = if (theme.id == SubscriptionPageTemplates.MINIMAL) 40 else 36
        val gapDp = 10
        return ((screenWidthDp - horizontalPadding - gapDp * (columns - 1)) / columns)
            .coerceAtLeast(if (theme.id == SubscriptionPageTemplates.AURORA) 104 else 112)
    }

    private fun addPaymentChannels() {
        val channels = config.paymentChannels.ifEmpty {
            PaymentSDK.resolvePaymentChannel(PaymentContext()).enabledChannels
        }
        if (channels.isEmpty()) return
        val availableChannels = PaymentSDK.getAvailableChannels()
        val invalidChannels = channels.filterNot { it in availableChannels }
        if (invalidChannels.isNotEmpty()) {
            root.addView(
                label(
                    "Payment channel config error: ${invalidChannels.joinToString { it.wireValue }}",
                    13f,
                    Color.RED,
                    top = 14
                )
            )
            return
        }
        root.addView(sectionHeading("Payment method", "Secure checkout through your app store"), fullWidthParams(top = 24))
        val channelGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            channels.forEachIndexed { index, channel ->
                val selected = channel == selectedChannel || (selectedChannel == null && index == 0)
                addView(
                    channelRadioCard(this, channel, selected),
                    RadioGroup.LayoutParams(0, dp(64), 1f).apply {
                        if (index > 0) leftMargin = dp(8)
                    })
            }
        }
        channelGroup.setOnCheckedChangeListener { group, checkedId ->
            selectedChannel = group.findViewById<RadioButton>(checkedId)?.tag as? PaymentChannel
            updateRadioCardSelection(group, checkedId)
            emit(SubscriptionPageEventName.CHANNEL_SELECT)
        }
        root.addView(channelGroup, fullWidthParams(top = 14))
    }

    private fun selectRadioCard(group: RadioGroup, radioButton: RadioButton) {
        group.check(radioButton.id)
        updateRadioCardSelection(group, radioButton.id)
    }

    private fun updateRadioCardSelection(group: RadioGroup, checkedId: Int) {
        for (index in 0 until group.childCount) {
            val card = group.getChildAt(index) as? ViewGroup ?: continue
            val radioButton = findRadioButton(card) ?: continue
            val selected = radioButton.id == checkedId
            radioButton.isChecked = selected
            radioButton.buttonTintList = android.content.res.ColorStateList.valueOf(
                if (selected) primaryColor else Color.rgb(139, 164, 156)
            )
            card.background = productCardBackground(selected)
            card.animate()
                .scaleX(if (selected) 1f else 0.985f)
                .scaleY(if (selected) 1f else 0.985f)
                .alpha(if (selected) 1f else 0.92f)
                .setDuration(180L)
                .start()
        }
    }

    private fun updateProductCardSelections(selectedProductId: String) {
        productGroups.forEach { group ->
            for (index in 0 until group.childCount) {
                val card = group.getChildAt(index) as? ViewGroup ?: continue
                val radioButton = findRadioButton(card) ?: continue
                val product = radioButton.tag as? Product ?: continue
                val selected = product.marketProductId == selectedProductId
                radioButton.isChecked = selected
                radioButton.buttonTintList = ColorStateList.valueOf(
                    if (selected) primaryColor else Color.rgb(139, 164, 156)
                )
                card.background = productCardBackground(selected)
                card.animate()
                    .scaleX(if (selected) 1f else 0.985f)
                    .scaleY(if (selected) 1f else 0.985f)
                    .alpha(if (selected) 1f else 0.92f)
                    .setDuration(180L)
                    .start()
            }
        }
    }

    private fun findRadioButton(parent: ViewGroup): RadioButton? {
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            if (child is RadioButton) return child
            if (child is ViewGroup) findRadioButton(child)?.let { return it }
        }
        return null
    }

    private fun channelRadioCard(
        group: RadioGroup,
        channel: PaymentChannel,
        selected: Boolean
    ): View {
        val radioButton = RadioButton(this).apply {
            id = View.generateViewId()
            tag = channel
            isChecked = selected
            buttonTintList = android.content.res.ColorStateList.valueOf(
                if (selected) primaryColor else Color.rgb(139, 164, 156)
            )
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(12), 0)
            background = productCardBackground(selected)
            addView(radioButton, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                setMargins(0, 0, dp(8), 0)
            })
            addView(label(channel.displayName(), 14f, theme.title).apply {
                typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            setOnClickListener { selectRadioCard(group, radioButton) }
            radioButton.setOnClickListener { selectRadioCard(group, radioButton) }
        }
    }

    private fun addActions() {
        actionHost.removeAllViews()
        actionSummary = label("", 13f, theme.muted, bottom = 8).apply {
            gravity = if (theme.id == SubscriptionPageTemplates.MIDNIGHT) Gravity.START else Gravity.CENTER
        }
        actionHost.addView(actionSummary)
        updateActionSummary()
        actionHost.addView(Button(this).apply {
            text = when (theme.id) {
                SubscriptionPageTemplates.MIDNIGHT -> config.ctaText.uppercase()
                else -> config.ctaText
            }
            isAllCaps = false
            textSize = if (theme.id == SubscriptionPageTemplates.MINIMAL) 17f else 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = roundedBackground(
                primaryColor,
                primaryColor,
                when (theme.id) {
                    SubscriptionPageTemplates.MIDNIGHT -> dp(6)
                    SubscriptionPageTemplates.MINIMAL -> dp(8)
                    else -> dp(18)
                }
            )
            stateListAnimator = null
            setOnClickListener { purchaseSelectedProduct() }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            if (theme.id == SubscriptionPageTemplates.MINIMAL) dp(54) else dp(58)
        ))
    }

    private fun updateActionSummary() {
        val product = selectedProduct ?: return
        val price = product.localizedPrice ?: product.price.orEmpty()
        actionSummary?.text = listOf(product.displayName(), price)
            .filter { it.isNotBlank() }
            .joinToString("  •  ")
    }

    private fun addBottomLinks() {
        if (!config.showRestore && !config.showTerms && !config.showPrivacy) return
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (config.showRestore) {
                addView(TextView(this@SubscriptionPageActivity).apply {
                    text = config.restoreText
                    textSize = 16f
                    setTextColor(primaryColor)
                    setOnClickListener { restorePurchases() }
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            } else {
                addView(View(this@SubscriptionPageActivity), LinearLayout.LayoutParams(0, 1, 1f))
            }
            val linkRow = LinearLayout(this@SubscriptionPageActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
            if (config.showTerms) {
                linkRow.addView(bottomLink(config.termsText, config.termsUrl))
            }
            if (config.showPrivacy) {
                if (linkRow.childCount > 0) {
                    linkRow.addView(TextView(this@SubscriptionPageActivity).apply {
                        text = "  "
                    })
                }
                linkRow.addView(bottomLink(config.privacyText, config.privacyUrl))
            }
            if (linkRow.childCount > 0) {
                addView(linkRow, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        }, fullWidthParams(top = 18))
    }

    private fun bottomLink(textValue: String, url: String): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 16f
            setTextColor(primaryColor)
            gravity = Gravity.END
            if (url.isNotBlank()) {
                setOnClickListener { openUrl(url) }
            }
        }
    }

    private fun openUrl(url: String) {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
            )
        }.onFailure { error ->
            Log.e(TAG, "open url failed: $url", error)
        }
    }

    private fun formatCoinAmount(value: Double): String {
        return CoinAmountFormatter.displayText(value)
    }

    private fun purchaseSelectedProduct() {
        val product = selectedProduct
        if (product == null) {
            updateState(SubscriptionPageState.FAILED)
            emit(
                SubscriptionPageEventName.PURCHASE_FAILED,
                mapOf("message" to "No product selected")
            )
            return
        }
        emit(SubscriptionPageEventName.PURCHASE_CLICK)
        updateState(SubscriptionPageState.PURCHASING)
        runCatching {
            PaymentSDK.purchase(
                this,
                product,
                selectedChannel,
                PaymentContext()
            ) {
                if (it.success) {
                    updateState(SubscriptionPageState.SUCCESS)
                    emit(
                        SubscriptionPageEventName.PURCHASE_SUCCESS,
                        mapOf("order_id" to it.orderId)
                    )
                } else {
                    updateState(SubscriptionPageState.FAILED)
                    emit(SubscriptionPageEventName.PURCHASE_FAILED, mapOf("message" to it.message))
                }
            }
        }.onFailure {
            updateState(SubscriptionPageState.FAILED)
            emit(SubscriptionPageEventName.PURCHASE_FAILED, mapOf("message" to it.message))
        }
    }

    private fun restorePurchases() {
        val channel = selectedChannel
        if (channel == null) {
            emit(
                SubscriptionPageEventName.RESTORE_FAILED,
                mapOf("message" to "No payment channel selected")
            )
            updateState(SubscriptionPageState.FAILED)
            return
        }
        emit(SubscriptionPageEventName.RESTORE_CLICK)
        updateState(SubscriptionPageState.PURCHASING)
        thread(name = "subscription-page-restore") {
            runCatching {
                PaymentSDK.restore(channel)
            }.onSuccess { result ->
                runOnUiThread {
                    updateState(SubscriptionPageState.READY)
                    emit(
                        SubscriptionPageEventName.RESTORE_SUCCESS,
                        mapOf(
                            "restored_count" to result.purchases.count { it.success },
                            "message" to result.message
                        )
                    )
                }
            }.onFailure { error ->
                runOnUiThread {
                    updateState(SubscriptionPageState.FAILED)
                    emit(
                        SubscriptionPageEventName.RESTORE_FAILED,
                        mapOf("message" to error.message)
                    )
                }
            }
        }
    }

    private fun renderReady() {
        Log.d(TAG, "renderReady products=${products.size}")
        resetContent(preserveCarouselPosition = true)
        productGroups.clear()
        when (theme.id) {
            SubscriptionPageTemplates.MIDNIGHT -> {
                addCurrentAppCard()
                addSubscriptionProducts()
                addWeeklyPointsSection()
                addOneTimeProducts()
                addSharingSection()
            }
            SubscriptionPageTemplates.MINIMAL -> {
                addMembershipStatus()
                addCurrentAppCard()
                addWeeklyPointsSection()
                addSubscriptionProducts()
                addOneTimeProducts()
                addSharingSection()
            }
            else -> {
                addMembershipStatus()
                addCurrentAppCard()
                addWeeklyPointsSection()
                addSubscriptionProducts()
                addOneTimeProducts()
                addSharingSection()
            }
        }
        if (config.showPaymentChannel) addPaymentChannels()
        addActions()
        addBottomLinks()
        updateState(SubscriptionPageState.READY)
    }

    private fun addMembershipStatus() {
        val user = runCatching { CoreUserSDK.getCurrentUser() }.getOrNull() ?: return
        val status = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = roundedBackground(
                if (theme.id == SubscriptionPageTemplates.MIDNIGHT) theme.elevatedSurface else theme.surface,
                if (user.isVip) primaryColor else borderColor,
                when (theme.id) {
                    SubscriptionPageTemplates.MIDNIGHT -> dp(8)
                    SubscriptionPageTemplates.MINIMAL -> dp(4)
                    else -> dp(16)
                },
                if (user.isVip) dp(2) else dp(1)
            )
        }
        status.addView(ImageView(this).apply {
            setImageResource(when (theme.id) {
                SubscriptionPageTemplates.MINIMAL -> R.drawable.minimal_vip_mark
                SubscriptionPageTemplates.MIDNIGHT -> R.drawable.midnight_membership_mark
                else -> R.drawable.aurora_membership_mark
            })
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "Membership status"
            clipToOutline = true
            outlineProvider = roundedOutline(dp(16))
        }, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginEnd = dp(10) })
        status.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(if (user.isVip) "VIP active" else "Current membership", 15f, theme.title).apply {
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(label(
                if (user.isVip && user.vipExpiredAt > 0) "Premium access enabled" else "Choose a plan to unlock more",
                12f,
                mutedTextColor,
                top = 3
            ))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        status.addView(View(this).apply {
            setBackgroundColor(borderColor)
        }, LinearLayout.LayoutParams(dp(1), dp(38)).apply { marginEnd = dp(12) })
        status.addView(ImageView(this).apply {
            setImageResource(if (theme.id == SubscriptionPageTemplates.AURORA) {
                R.drawable.aurora_balance_mark
            } else {
                R.drawable.minimal_balance_mark
            })
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "Coin balance"
            clipToOutline = true
            outlineProvider = roundedOutline(dp(14))
        }, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(6) })
        status.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(label("Balance", 11f, mutedTextColor).apply { gravity = Gravity.END })
            addView(label(formatCoinAmount(user.balance), 17f, primaryColor).apply {
                gravity = Gravity.END
                typeface = Typeface.DEFAULT_BOLD
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(status, fullWidthParams(top = 14))
    }

    private fun showLoading() {
        resetContent()
        root.addView(label("Loading products...", 15f, Color.DKGRAY, top = 20))
        updateState(SubscriptionPageState.LOADING)
    }

    private fun showEmpty() {
        resetContent()
        root.addView(label("No products available.", 15f, Color.DKGRAY, top = 20))
        addRetryButton()
        updateState(SubscriptionPageState.FAILED)
    }

    private fun showError(error: Throwable) {
        resetContent()
        root.addView(label("Failed to load products.", 15f, Color.DKGRAY, top = 20))
        root.addView(label(error.message.orEmpty(), 13f, Color.GRAY, top = 6))
        addRetryButton()
        updateState(SubscriptionPageState.FAILED)
    }

    private fun addRetryButton() {
        root.addView(Button(this).apply {
            text = "Retry"
            isAllCaps = false
            setOnClickListener { loadPageData(forceRefresh = true) }
        }, fullWidthParams(top = 16))
    }

    private fun resetContent(preserveCarouselPosition: Boolean = false) {
        stopCarousel(resetPosition = !preserveCarouselPosition)
        if (::actionHost.isInitialized) actionHost.removeAllViews()
        productGroups.clear()
        val keepCount =
            if (::stateView.isInitialized) root.indexOfChild(stateView) + 1 else root.childCount
        while (root.childCount > keepCount) {
            root.removeViewAt(root.childCount - 1)
        }
    }

    private fun selectInitialProduct(): Product? {
        return products.firstOrNull()
    }

    private fun resolveInitialChannel(): PaymentChannel? {
        return runCatching {
            val configuredChannel = config.paymentChannels.firstOrNull()
            configuredChannel ?: PaymentSDK.resolvePaymentChannel(PaymentContext()).defaultChannel
        }.getOrNull()
    }

    private fun updateState(next: SubscriptionPageState) {
        state = next
        if (::stateView.isInitialized) {
            stateView.text = "State: ${next.name.lowercase()}"
        }
    }

    private fun emit(name: SubscriptionPageEventName, params: Map<String, Any?> = emptyMap()) {
        PaymentSDK.dispatchSubscriptionPageEvent(
            SubscriptionPageEvent(
                name = name,
                productId = selectedProduct?.marketProductId,
                paymentChannel = selectedChannel,
                state = state,
                params = mapOf(
                    "template_id" to config.templateId,
                    "scene" to config.scene
                ) + params
            )
        )
    }

    private fun closePage() {
        Log.d(TAG, "close subscription page")
        emit(SubscriptionPageEventName.CLOSE)
        PaymentSDK.closeSubscriptionPage()
        if (!isFinishing) {
            finish()
        }
    }

    private fun label(
        textValue: String,
        size: Float,
        color: Int,
        top: Int = 0,
        bottom: Int = 0
    ): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = size
            setTextColor(color)
            setPadding(0, dp(top), 0, dp(bottom))
            letterSpacing = 0f
        }
    }

    private fun addSharingSection() {
        if (membershipShareItems.isEmpty()) return
        when (theme.sharedAppsInteraction) {
            SharedAppsInteraction.AUTO_CAROUSEL -> addCarouselSharingSection()
            SharedAppsInteraction.MANUAL_RAIL -> addRailSharingSection()
            SharedAppsInteraction.TWO_ROW_GRID -> addGridSharingSection()
        }
    }

    private fun addCarouselSharingSection() {
        val section = config.sharedApps
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = roundedBackground(theme.elevatedSurface, borderColor, dp(18))
        }
        addSharingHeader(container)
        container.addView(label(section.description, 16f, mutedTextColor, top = 8, bottom = 14))
        val dots = pageDots(membershipShareItems.size)
        container.addView(sharedAppCarousel(membershipShareItems))
        container.addView(dots)
        root.addView(container, fullWidthParams(top = 16))
        startCarousel()
    }

    private fun addRailSharingSection() {
        val section = config.sharedApps
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(10), 0, dp(6))
        }
        addSharingHeader(container, endPadding = 4)
        container.addView(label(section.description, 16f, mutedTextColor, top = 8, bottom = 14).apply {
            setPadding(0, dp(8), dp(18), dp(14))
        })
        container.addView(manualSharedAppRail(membershipShareItems))
        root.addView(container, fullWidthParams(top = 16))
    }

    private fun addGridSharingSection() {
        val section = config.sharedApps
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(10), dp(4), dp(4))
        }
        addSharingHeader(container, endPadding = 0)
        container.addView(label(section.description, 16f, mutedTextColor, top = 8, bottom = 12))
        container.addView(twoRowSharedAppGrid(membershipShareItems))
        root.addView(container, fullWidthParams(top = 16))
    }

    private fun addSharingHeader(container: LinearLayout, endPadding: Int = 0) {
        val section = config.sharedApps
        container.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val heading = when (theme.id) {
                SubscriptionPageTemplates.MINIMAL -> section.title
                else -> section.title.uppercase()
            }
            addView(label(heading, if (theme.id == SubscriptionPageTemplates.MINIMAL) 17f else 18f, primaryColor).apply {
                typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@SubscriptionPageActivity).apply {
                text = "${membershipShareItems.size} apps included"
                textSize = 14f
                setTextColor(primaryColor)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = roundedBackground(
                    if (theme.id == SubscriptionPageTemplates.MIDNIGHT) theme.elevatedSurface else theme.surface,
                    if (theme.id == SubscriptionPageTemplates.MINIMAL) theme.accent else borderColor,
                    if (theme.id == SubscriptionPageTemplates.MIDNIGHT) dp(6) else dp(18)
                )
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(endPadding) })
        })
    }

    private fun addCurrentAppCard() {
        val benefitDescription = config.benefitDescription.trim()
        val benefitTags = config.benefits.filter { it.isNotBlank() }
        if (benefitDescription.isBlank() && benefitTags.isEmpty()) return
        val card = when (theme.id) {
            SubscriptionPageTemplates.MIDNIGHT -> midnightBenefitPanel(benefitDescription, benefitTags)
            SubscriptionPageTemplates.MINIMAL -> minimalBenefitPanel(benefitDescription, benefitTags)
            else -> auroraBenefitPanel(benefitDescription, benefitTags)
        }
        root.addView(card, fullWidthParams(top = if (theme.id == SubscriptionPageTemplates.MIDNIGHT) 24 else 28))
    }

    private fun auroraBenefitPanel(description: String, tags: List<String>): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedBackground(theme.surface, borderColor, dp(20))
            addView(View(this@SubscriptionPageActivity).apply {
                setBackgroundColor(theme.accent)
            }, LinearLayout.LayoutParams(dp(5), ViewGroup.LayoutParams.MATCH_PARENT))
            addView(LinearLayout(this@SubscriptionPageActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(18), dp(18), dp(20))
                addView(label(currentBenefitHeading(), 18f, primaryColor).apply {
                    typeface = Typeface.DEFAULT_BOLD
                })
                if (description.isNotBlank()) {
                    addView(label(description, 17f, mutedTextColor, top = 10))
                }
                if (tags.isNotEmpty()) addView(benefitTagScroller(tags))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun midnightBenefitPanel(description: String, tags: List<String>): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(18))
            background = roundedBackground(theme.surface, theme.accent, dp(8), dp(1))
            addView(LinearLayout(this@SubscriptionPageActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(label("01", 13f, theme.accent).apply {
                    typeface = Typeface.DEFAULT_BOLD
                }, LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(10)
                })
                addView(label(currentBenefitHeading(), 18f, primaryColor).apply {
                    typeface = Typeface.DEFAULT_BOLD
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
            if (description.isNotBlank()) {
                addView(label(description, 16f, mutedTextColor, top = 10))
            }
            if (tags.isNotEmpty()) addView(benefitTagScroller(tags))
        }
    }

    private fun minimalBenefitPanel(description: String, tags: List<String>): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(18))
            background = roundedBackground(theme.elevatedSurface, borderColor, dp(4), dp(1))
            addView(LinearLayout(this@SubscriptionPageActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(label("01", 12f, theme.accent).apply {
                    typeface = Typeface.DEFAULT_BOLD
                }, LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(View(this@SubscriptionPageActivity).apply {
                    setBackgroundColor(theme.accent)
                }, LinearLayout.LayoutParams(0, dp(1), 1f))
            })
            addView(LinearLayout(this@SubscriptionPageActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(currentBenefitHeading(), 16f, primaryColor, top = 12).apply {
                    typeface = Typeface.DEFAULT_BOLD
                })
                if (description.isNotBlank()) {
                    addView(label(description, 15f, mutedTextColor, top = 8).apply { maxLines = 4 })
                }
                if (tags.isNotEmpty()) addView(benefitTagScroller(tags))
            })
        }
    }

    private fun currentBenefitHeading(): String {
        return when (theme.id) {
            SubscriptionPageTemplates.MIDNIGHT -> "CREATIVE POWER UNLOCKED"
            SubscriptionPageTemplates.MINIMAL -> "CURRENT STUDIO BENEFITS"
            else -> "CREATE WITHOUT LIMITS"
        }
    }

    private fun benefitTagScroller(items: List<String>): View {
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(LinearLayout(this@SubscriptionPageActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(14), 0, 0)
                items.forEach { item ->
                    addView(
                        benefitTag(item), LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 0, dp(8), 0)
                        })
                }
            })
        }
    }

    private fun benefitTag(textValue: String): View {
        return TextView(this).apply {
            text = textValue
            textSize = 13f
            setTextColor(primaryColor)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            letterSpacing = 0f
            background = roundedBackground(theme.tagSurface, borderColor, dp(16))
        }
    }

    private fun manualSharedAppRail(items: List<SubscriptionSharedAppItem>): View {
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(LinearLayout(this@SubscriptionPageActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                items.forEachIndexed { index, item ->
                    addView(
                        compactSharedAppCard(item, index),
                        LinearLayout.LayoutParams(dp(238), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, 0, dp(12), 0)
                        }
                    )
                }
            })
        }
    }

    private fun compactSharedAppCard(item: SubscriptionSharedAppItem, index: Int): View {
        val iconColors = listOf(
            Color.rgb(255, 224, 216),
            Color.rgb(255, 238, 197),
            Color.rgb(224, 241, 236),
            Color.rgb(232, 229, 255)
        )
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(178)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = roundedBackground(theme.surface, borderColor, dp(16))
            addView(LinearLayout(this@SubscriptionPageActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    sharedAppIcon(item, iconColors[index % iconColors.size]),
                    LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                        setMargins(0, 0, dp(12), 0)
                    }
                )
                addView(label(item.title, 17f, theme.title).apply {
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 2
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
            addView(label(item.description, 14f, mutedTextColor, top = 12).apply {
                maxLines = 3
                includeFontPadding = false
                setLineSpacing(dp(2).toFloat(), 1f)
            })
        }
    }

    private fun twoRowSharedAppGrid(items: List<SubscriptionSharedAppItem>): View {
        val iconColors = listOf(
            Color.rgb(255, 224, 216),
            Color.rgb(255, 238, 197),
            Color.rgb(224, 241, 236),
            Color.rgb(232, 229, 255)
        )
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(LinearLayout(this@SubscriptionPageActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                items.chunked(2).forEachIndexed { columnIndex, columnItems ->
                    addView(LinearLayout(this@SubscriptionPageActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        columnItems.forEachIndexed { rowIndex, item ->
                            val itemIndex = columnIndex * 2 + rowIndex
                            addView(
                                compactGridAppCard(item, iconColors[itemIndex % iconColors.size]),
                                LinearLayout.LayoutParams(dp(206), dp(82)).apply {
                                    if (rowIndex > 0) topMargin = dp(10)
                                }
                            )
                        }
                    }, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = dp(10) })
                }
            })
        }
    }

    private fun compactGridAppCard(item: SubscriptionSharedAppItem, iconColor: Int): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBackground(theme.surface, borderColor, dp(15))
            addView(
                sharedAppIcon(item, iconColor),
                LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                    setMargins(0, 0, dp(10), 0)
                }
            )
            addView(LinearLayout(this@SubscriptionPageActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(item.title, 15f, theme.title).apply {
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                item.description.takeIf { it.isNotBlank() }?.let { description ->
                    addView(label(description, 12f, theme.muted, top = 2).apply {
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun sharedAppCarousel(items: List<SubscriptionSharedAppItem>): View {
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            carouselScrollView = this
            addView(LinearLayout(this@SubscriptionPageActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                items.forEachIndexed { index, item ->
                    addView(
                        sharedAppCard(item, index),
                        LinearLayout.LayoutParams(dp(288), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, 0, dp(12), 0)
                        })
                }
            })
            setOnScrollChangeListener { _, scrollX, _, _, _ ->
                if (carouselStepPx <= 0) return@setOnScrollChangeListener
                val index = ((scrollX + carouselStepPx / 2) / carouselStepPx)
                    .coerceIn(0, carouselItemCount - 1)
                if (index != carouselCurrentIndex) {
                    carouselCurrentIndex = index
                    updateCarouselDots(index)
                }
            }
            post {
                carouselStepPx = dp(300)
                carouselItemCount = items.size
                carouselCurrentIndex = carouselCurrentIndex.coerceIn(0, carouselItemCount - 1)
                scrollTo(carouselCurrentIndex * carouselStepPx, 0)
                updateCarouselDots(carouselCurrentIndex)
            }
        }
    }

    private fun sharedAppCard(item: SubscriptionSharedAppItem, index: Int): View {
        val iconColors = listOf(
            Color.rgb(255, 224, 216),
            Color.rgb(255, 238, 197),
            Color.rgb(224, 241, 236),
            Color.rgb(232, 229, 255)
        )
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(150)
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = roundedBackground(theme.surface, borderColor, dp(16))
            addView(LinearLayout(this@SubscriptionPageActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    sharedAppIcon(item, iconColors[index % iconColors.size]),
                    LinearLayout.LayoutParams(dp(56), dp(56)).apply {
                        setMargins(0, 0, dp(14), 0)
                    })
                addView(label(item.title, 19f, theme.title).apply {
                    typeface = Typeface.DEFAULT_BOLD
                    setSingleLine(false)
                    maxLines = Int.MAX_VALUE
                    includeFontPadding = false
                    setLineSpacing(dp(2).toFloat(), 1f)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
            addView(label(item.description, 16f, mutedTextColor, top = 12).apply {
                setSingleLine(false)
                maxLines = Int.MAX_VALUE
                includeFontPadding = false
                setLineSpacing(dp(3).toFloat(), 1f)
            })
        }
    }

    private fun sharedAppIcon(item: SubscriptionSharedAppItem, fallbackColor: Int): View {
        val placeholder = TextView(this).apply {
            text = "✦"
            textSize = 21f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(255, 128, 90))
            background = roundedBackground(fallbackColor, fallbackColor, dp(14))
        }
        if (item.icon.isBlank()) return placeholder

        return FrameLayout(this).apply {
            background = roundedBackground(fallbackColor, fallbackColor, dp(16))
            addView(
                placeholder, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            val imageView = ImageView(this@SubscriptionPageActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                visibility = View.GONE
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, dp(14).toFloat())
                    }
                }
                clipToOutline = true
            }
            addView(
                imageView, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            loadBenefitIcon(item.icon, imageView)
        }
    }

    private fun loadBenefitIcon(url: String, imageView: ImageView) {
        thread(name = "subscription-shared-app-icon") {
            val bitmap = runCatching {
                URL(url).openStream().use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            if (bitmap != null) {
                runOnUiThread {
                    imageView.setImageBitmap(bitmap)
                    imageView.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun pageDots(count: Int): LinearLayout {
        return LinearLayout(this).apply {
            carouselDots = this
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, 0)
            repeat(count.coerceAtLeast(1)) { index ->
                addView(View(this@SubscriptionPageActivity).apply {
                    background = roundedBackground(
                        if (index == carouselCurrentIndex) primaryColor else Color.rgb(181, 204, 198),
                        if (index == carouselCurrentIndex) primaryColor else Color.rgb(181, 204, 198),
                        dp(5)
                    )
                }, LinearLayout.LayoutParams(
                    if (index == carouselCurrentIndex) dp(24) else dp(10),
                    dp(8)
                ).apply {
                    setMargins(dp(4), 0, dp(4), 0)
                })
            }
        }
    }

    private fun startCarousel() {
        carouselHandler.removeCallbacks(carouselRunnable)
        if (carouselItemCount > 0) {
            carouselCurrentIndex = carouselCurrentIndex.coerceIn(0, carouselItemCount - 1)
        }
        carouselHandler.postDelayed(carouselRunnable, CAROUSEL_INTERVAL_MS)
    }

    private fun stopCarousel(resetPosition: Boolean = true) {
        carouselHandler.removeCallbacks(carouselRunnable)
        carouselScrollView = null
        carouselDots = null
        carouselItemCount = 0
        if (resetPosition) carouselCurrentIndex = 0
        carouselStepPx = 0
    }

    private fun updateCarouselDots(activeIndex: Int) {
        val dots = carouselDots ?: return
        for (index in 0 until dots.childCount) {
            val dot = dots.getChildAt(index)
            dot.background = roundedBackground(
                if (index == activeIndex) primaryColor else Color.rgb(181, 204, 198),
                if (index == activeIndex) primaryColor else Color.rgb(181, 204, 198),
                dp(5)
            )
            dot.layoutParams = (dot.layoutParams as LinearLayout.LayoutParams).apply {
                width = if (index == activeIndex) dp(24) else dp(10)
            }
        }
        dots.requestLayout()
    }

    private fun badgeForProduct(product: Product): String? {
        purchaseStateBadge(product)?.let { return it }
        return if (product.productType == ProductType.SUBSCRIPTION && product.hasTrial) {
            "FREE TRIAL"
        } else {
            null
        }
    }

    private fun purchaseStateBadge(product: Product): String? {
        if (product.productType == ProductType.SUBSCRIPTION &&
            weeklyPointsInfo?.isVip == true &&
            weeklyPointsInfo?.marketProductId == product.marketProductId
        ) {
            return "CURRENT PLAN"
        }
        val entitlement = PaymentSDK.getEntitlements().firstOrNull {
            it.productId == product.marketProductId ||
                (product.entitlementId != null && it.entitlementId == product.entitlementId)
        } ?: return null
        if (!entitlement.active) return null
        return if (product.productType == ProductType.SUBSCRIPTION) "CURRENT PLAN" else "OWNED"
    }

    private fun productRadioCard(
        group: RadioGroup,
        product: Product,
        badge: String?,
        selected: Boolean
    ): View {
        val radioButton = RadioButton(this).apply {
            id = View.generateViewId()
            tag = product
            isChecked = selected
            buttonTintList = android.content.res.ColorStateList.valueOf(
                if (selected) primaryColor else Color.rgb(139, 164, 156)
            )
        }
        return when (theme.productCardLayout) {
            ProductCardLayout.FEATURE -> featureProductCard(group, radioButton, product, badge, selected)
            ProductCardLayout.OFFER -> offerProductCard(group, radioButton, product, badge, selected)
            ProductCardLayout.COMPACT -> compactProductCard(group, radioButton, product, badge, selected)
        }
    }

    /** Compact visual treatment used by the Aurora and Minimal coin rails. */
    private fun coinProductCard(
        group: RadioGroup,
        product: Product,
        badge: String?,
        selected: Boolean
    ): View {
        val radioButton = RadioButton(this).apply {
            id = View.generateViewId()
            tag = product
            isChecked = selected
            buttonTintList = ColorStateList.valueOf(if (selected) primaryColor else borderColor)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = productCardBackground(selected)
            radioButton.alpha = 0f
            addView(radioButton, LinearLayout.LayoutParams(dp(1), dp(1)))
            badge?.takeIf { it.isNotBlank() }?.let {
                addView(TextView(this@SubscriptionPageActivity).apply {
                    text = it
                    textSize = 9f
                    setTextColor(primaryColor)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(dp(7), dp(3), dp(7), dp(3))
                    maxLines = 1
                    background = roundedBackground(theme.tagSurface, theme.tagSurface, dp(8))
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(24)
                ).apply { bottomMargin = dp(3) })
            }
            val coinIconSize = if (theme.id == SubscriptionPageTemplates.MINIMAL) dp(48) else dp(46)
            addView(productIconTile(product), LinearLayout.LayoutParams(coinIconSize, coinIconSize).apply {
                topMargin = if (badge.isNullOrBlank()) dp(4) else dp(2)
            })
            addView(label("${formatCoinAmount(product.coinsGranted ?: 0.0)} coins", 15f, theme.title, top = 5).apply {
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                maxLines = 1
            })
            productPrice(product)?.let { price ->
                addView(label(price, 15f, primaryColor, top = 2).apply {
                    gravity = Gravity.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                })
            }
            setOnClickListener { selectRadioCard(group, radioButton) }
            radioButton.setOnClickListener { selectRadioCard(group, radioButton) }
        }
    }

    private fun featureProductCard(
        group: RadioGroup,
        radioButton: RadioButton,
        product: Product,
        badge: String?,
        selected: Boolean
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(136)
            background = productCardBackground(selected)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(productIconTile(product), LinearLayout.LayoutParams(dp(94), dp(94)))
            addView(LinearLayout(this@SubscriptionPageActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(product.displayName(), 18f, theme.title).apply {
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    includeFontPadding = false
                    setAutoSizeTextTypeUniformWithConfiguration(
                        14,
                        18,
                        1,
                        android.util.TypedValue.COMPLEX_UNIT_SP
                    )
                    setLineSpacing(dp(1).toFloat(), 1f)
                })
                badge?.takeIf { it.isNotBlank() }?.let {
                    addView(badgeView(it), LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(8) })
                }
                addWeeklyPointsLabel(this, product)
                addCoinValue(this, product, textSize = 15f)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(16)
                marginEnd = dp(12)
            })
            addView(LinearLayout(this@SubscriptionPageActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                productPrice(product)?.let { price ->
                    addView(label(price, 27f, primaryColor).apply {
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                    })
                }
                product.subscriptionPeriod?.takeIf { it.isNotBlank() }?.let { period ->
                    addView(label("per ${period.lowercase()}", 14f, theme.muted, top = 2).apply {
                        gravity = Gravity.CENTER
                    })
                }
            }, LinearLayout.LayoutParams(dp(112), ViewGroup.LayoutParams.WRAP_CONTENT))
            // Aurora uses the selected border as the visual radio state, matching the artwork.
            radioButton.alpha = 0f
            addView(radioButton, LinearLayout.LayoutParams(dp(1), dp(1)))
            setOnClickListener { selectRadioCard(group, radioButton) }
        }
        radioButton.setOnClickListener { selectRadioCard(group, radioButton) }
        return card
    }

    private fun offerProductCard(
        group: RadioGroup,
        radioButton: RadioButton,
        product: Product,
        badge: String?,
        selected: Boolean
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(136)
            setPadding(dp(12), dp(14), dp(16), dp(14))
            background = productCardBackground(selected)
            addView(radioButton, LinearLayout.LayoutParams(dp(36), dp(42)).apply {
                setMargins(0, 0, dp(8), 0)
            })
            addView(productIconTile(product), LinearLayout.LayoutParams(dp(90), dp(90)).apply {
                setMargins(0, 0, dp(14), 0)
            })
            addView(LinearLayout(this@SubscriptionPageActivity).apply {
                orientation = LinearLayout.VERTICAL
                badge?.takeIf { it.isNotBlank() }?.let {
                    addView(badgeView(it), LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(6) })
                }
                addView(LinearLayout(this@SubscriptionPageActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(label(product.displayName(), 20f, theme.title).apply {
                        typeface = Typeface.DEFAULT_BOLD
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                        includeFontPadding = false
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    productPrice(product)?.let { price ->
                        addView(label(price, 18f, primaryColor).apply {
                            typeface = Typeface.DEFAULT_BOLD
                            gravity = Gravity.END
                        }, LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { marginStart = dp(8) })
                    }
                })
                // Keep value information beneath the title, as in the Midnight artwork.
                addWeeklyPointsLabel(this, product)
                addCoinValue(this, product, textSize = 14f)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (product.productType != ProductType.SUBSCRIPTION) {
                addView(label("›", 30f, theme.muted).apply {
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(dp(24), dp(42)))
            }
            setOnClickListener { selectRadioCard(group, radioButton) }
        }
        radioButton.setOnClickListener { selectRadioCard(group, radioButton) }
        return card
    }

    private fun compactProductCard(
        group: RadioGroup,
        radioButton: RadioButton,
        product: Product,
        badge: String?,
        selected: Boolean
    ): View {
        val card = FrameLayout(this).apply {
            minimumHeight = dp(124)
            setPadding(dp(18), dp(16), dp(16), dp(16))
            background = productCardBackground(selected)
            addView(LinearLayout(this@SubscriptionPageActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(product.displayName(), 22f, theme.title).apply {
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 2
                })
                productPrice(product)?.let { price ->
                    addView(LinearLayout(this@SubscriptionPageActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(label(price, 20f, primaryColor).apply {
                            typeface = Typeface.DEFAULT_BOLD
                        })
                        product.subscriptionPeriod?.takeIf { it.isNotBlank() }?.let { period ->
                            addView(label(" / ${period.lowercase()}", 15f, theme.muted))
                        }
                    }, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(5) })
                }
                badge?.takeIf { it.isNotBlank() }?.let {
                    addView(badgeView(it), LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(8) })
                }
                addWeeklyPointsLabel(this, product)
                addCoinValue(this, product, textSize = 14f)
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                rightMargin = dp(54)
            })
            addView(radioButton, FrameLayout.LayoutParams(dp(42), dp(42)).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
            })
            setOnClickListener { selectRadioCard(group, radioButton) }
        }
        radioButton.setOnClickListener { selectRadioCard(group, radioButton) }
        return card
    }

    private fun productTitleAndPrice(product: Product, textSize: Float): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label(product.displayName(), textSize, theme.title).apply {
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            productPrice(product)?.let { price ->
                addView(label(price, textSize, theme.title).apply {
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.END
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(8) })
            }
        }
    }

    private fun addProductSupportingContent(
        container: LinearLayout,
        product: Product,
        badge: String?,
        descriptionLines: Int,
        showValueLine: Boolean = true
    ) {
        badge?.takeIf { it.isNotBlank() }?.let {
            container.addView(badgeView(it), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(7) })
        }
        product.description.takeIf { it.isNotBlank() }?.let { description ->
            container.addView(label(description, 14f, mutedTextColor, top = 7).apply {
                maxLines = descriptionLines
            })
        }
        val purchaseMetadata = listOfNotNull(
            product.subscriptionPeriod?.takeIf { it.isNotBlank() }?.let { "Renews $it" },
            product.trialPeriod?.takeIf { it.isNotBlank() }?.let { "Trial $it" }
        ).joinToString("  •  ")
        if (purchaseMetadata.isNotBlank()) {
            container.addView(label(purchaseMetadata, 13f, theme.muted, top = 5))
        }
        if (showValueLine) {
            container.addView(label(productValueLine(product), 13f, theme.muted, top = 6).apply {
                typeface = Typeface.DEFAULT_BOLD
            })
        }
        addWeeklyPointsLabel(container, product)
        addCoinValue(container, product, textSize = 14f)
    }

    private fun addWeeklyPointsLabel(container: LinearLayout, product: Product) {
        if (product.productType != ProductType.SUBSCRIPTION ||
            !product.weeklyPointsEnabled || product.weeklyPoints <= 0
        ) return
        container.addView(label(
            "${product.weeklyPoints.toLong() * 100L} points every week",
            14f,
            if (theme.id == SubscriptionPageTemplates.MIDNIGHT) theme.accent else primaryColor,
            top = 7
        ).apply { typeface = Typeface.DEFAULT_BOLD })
    }

    private fun addCoinValue(container: LinearLayout, product: Product, textSize: Float) {
        if (product.productType == ProductType.SUBSCRIPTION && product.weeklyPointsEnabled) return
        product.coinsGranted?.takeIf { it > 0 }?.let { coins ->
            container.addView(label(
                "Get ${formatCoinAmount(coins)} credits after purchase",
                textSize,
                primaryColor,
                top = 6
            ).apply { typeface = Typeface.DEFAULT_BOLD })
        }
    }

    private fun productPrice(product: Product): String? =
        (product.localizedPrice ?: product.price).takeUnless { it.isNullOrBlank() }

    private fun productIconTile(product: Product): View {
        val icon = ProductIconResolver.resolve(theme.id, product.productType)
        val radius = when (theme.productCardLayout) {
            ProductCardLayout.FEATURE -> dp(16).toFloat()
            ProductCardLayout.OFFER -> dp(23).toFloat()
            ProductCardLayout.COMPACT -> dp(8).toFloat()
        }
        return ImageView(this).apply {
            setImageResource(productIconResource(product))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = icon.contentDescription
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
            elevation = 0f
        }
    }

    private fun productIconResource(product: Product): Int {
        val productType = product.productType
        return when (theme.id) {
            SubscriptionPageTemplates.MIDNIGHT -> when (productType) {
                ProductType.SUBSCRIPTION -> R.drawable.midnight_membership_mark
                ProductType.CONSUMABLE, ProductType.IAP, ProductType.UNKNOWN -> R.drawable.midnight_coin_stack
            }
            SubscriptionPageTemplates.MINIMAL -> when (productType) {
                ProductType.SUBSCRIPTION -> R.drawable.minimal_vip_mark
                ProductType.CONSUMABLE, ProductType.IAP, ProductType.UNKNOWN -> when {
                    (product.coinsGranted ?: 0.0) >= 20.0 -> R.drawable.minimal_coin_2500
                    (product.coinsGranted ?: 0.0) >= 10.0 -> R.drawable.minimal_coin_1200
                    else -> R.drawable.minimal_coin_mark
                }
            }
            else -> when (productType) {
                ProductType.SUBSCRIPTION -> R.drawable.aurora_plan_mark
                ProductType.CONSUMABLE, ProductType.IAP, ProductType.UNKNOWN -> when {
                    (product.coinsGranted ?: 0.0) >= 10.0 -> R.drawable.aurora_coin_1000
                    (product.coinsGranted ?: 0.0) >= 5.0 -> R.drawable.aurora_coin_500
                    (product.coinsGranted ?: 0.0) >= 2.5 -> R.drawable.aurora_coin_250
                    else -> R.drawable.aurora_coin_100
                }
            }
        }
    }

    private fun productValueLine(product: Product): String {
        return when (product.productType) {
            ProductType.SUBSCRIPTION -> "Premium access, renewed automatically"
            ProductType.CONSUMABLE -> "Instant credits with no recurring charge"
            ProductType.IAP -> "Unlock once and keep access"
            ProductType.UNKNOWN -> "Secure purchase through your app store"
        }
    }

    private fun badgeView(textValue: String): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 12f
            setSingleLine(true)
            setHorizontallyScrolling(true)
            minWidth = dp(112)
            setTextColor(primaryColor)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background =
                roundedBackground(theme.tagSurface, theme.tagSurface, dp(14))
        }
    }

    private fun PaymentChannel.displayName(): String {
        return when (this) {
            PaymentChannel.GOOGLE_PLAY -> "Google Play"
            PaymentChannel.APP_STORE -> "App Store"
            PaymentChannel.WEB_CHECKOUT -> "Web Checkout"
            else -> wireValue.replaceFirstChar { it.uppercase() }
        }
    }

    private fun Product.displayName(): String {
        return name.ifBlank { marketProductId }
    }

    private fun List<RelatedProduct>.toMembershipShareItems(): List<SubscriptionSharedAppItem> {
        return map { product ->
            SubscriptionSharedAppItem(
                title = product.productName.ifBlank { product.productId },
                description = product.description,
                icon = product.icon
            )
        }
    }

    private fun productCardBackground(selected: Boolean): GradientDrawable {
        val radius = when (theme.productCardLayout) {
            ProductCardLayout.FEATURE -> dp(18)
            ProductCardLayout.OFFER -> dp(8)
            ProductCardLayout.COMPACT -> dp(4)
        }
        return roundedBackground(
            if (selected) theme.selectedSurface else theme.surface,
            if (selected) primaryColor else borderColor,
            radius,
            if (selected) dp(2) else dp(1)
        )
    }

    private fun actionHostBackground(): GradientDrawable {
        return roundedBackground(
            when (theme.id) {
                SubscriptionPageTemplates.MIDNIGHT -> theme.elevatedSurface
                SubscriptionPageTemplates.MINIMAL -> theme.pageBackground
                else -> theme.surface
            },
            if (theme.id == SubscriptionPageTemplates.MIDNIGHT) theme.primary else borderColor,
            0,
            dp(1)
        )
    }

    private fun sectionHeading(title: String, subtitle: String): View {
        return when (theme.id) {
            SubscriptionPageTemplates.MIDNIGHT -> LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(View(this@SubscriptionPageActivity).apply {
                    setBackgroundColor(theme.accent)
                }, LinearLayout.LayoutParams(dp(3), dp(44)).apply { marginEnd = dp(12) })
                addView(LinearLayout(this@SubscriptionPageActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(label(title.uppercase(), 18f, theme.title).apply {
                        typeface = Typeface.DEFAULT_BOLD
                    })
                    addView(label(subtitle, 13f, theme.muted, top = 3))
                })
            }
            SubscriptionPageTemplates.MINIMAL -> LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(title, 18f, theme.title).apply { typeface = Typeface.DEFAULT_BOLD })
                addView(View(this@SubscriptionPageActivity).apply {
                    setBackgroundColor(theme.accent)
                }, LinearLayout.LayoutParams(dp(40), dp(2)).apply { topMargin = dp(8) })
                addView(label(subtitle, 13f, theme.muted, top = 8))
            }
            else -> LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(title, 19f, theme.title).apply {
                    typeface = Typeface.DEFAULT_BOLD
                })
                addView(label(subtitle, 14f, theme.muted, top = 4))
            }
        }
    }

    private fun roundedBackground(
        fillColor: Int,
        strokeColor: Int,
        radius: Int,
        strokeWidth: Int = dp(1)
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            cornerRadius = radius.toFloat()
            setStroke(strokeWidth, strokeColor)
        }
    }

    private fun fullWidthParams(top: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(top)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun statusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    companion object {
        private const val TAG = "SubscriptionPage"
        private const val CAROUSEL_INTERVAL_MS = 3000L
    }
}
