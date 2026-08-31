package com.nexus.sdk.payment.subscription_template

import com.nexus.sdk.payment.products.ProductType

/** Built-in subscription page templates selectable through SubscriptionPageConfig.templateId. */
object SubscriptionPageTemplates {
    const val AURORA = "aurora"
    const val MIDNIGHT = "midnight"
    const val MINIMAL = "minimal"

    val supportedIds: Set<String> = setOf(AURORA, MIDNIGHT, MINIMAL)
}

internal data class SubscriptionPageTheme(
    val id: String,
    val sharedAppsInteraction: SharedAppsInteraction,
    val productCardLayout: ProductCardLayout,
    val dark: Boolean,
    val pageBackground: Int,
    val surface: Int,
    val elevatedSurface: Int,
    val primary: Int,
    val accent: Int,
    val title: Int,
    val body: Int,
    val muted: Int,
    val border: Int,
    val selectedSurface: Int,
    val tagSurface: Int
)

internal enum class SharedAppsInteraction {
    AUTO_CAROUSEL,
    MANUAL_RAIL,
    TWO_ROW_GRID
}

internal enum class ProductCardLayout {
    FEATURE,
    OFFER,
    COMPACT
}

internal data class ProductIconSpec(
    val symbol: String,
    val contentDescription: String
)

internal object ProductIconResolver {
    fun resolve(templateId: String, productType: ProductType): ProductIconSpec {
        return when (SubscriptionPageThemeResolver.normalize(templateId)) {
            SubscriptionPageTemplates.MIDNIGHT -> when (productType) {
                ProductType.SUBSCRIPTION -> ProductIconSpec("∞", "Unlimited membership")
                ProductType.CONSUMABLE -> ProductIconSpec("ϟ", "Creation energy")
                ProductType.IAP -> ProductIconSpec("◈", "Permanent unlock")
                ProductType.UNKNOWN -> ProductIconSpec("○", "Product")
            }
            SubscriptionPageTemplates.MINIMAL -> when (productType) {
                ProductType.SUBSCRIPTION -> ProductIconSpec("✓", "Membership included")
                ProductType.CONSUMABLE -> ProductIconSpec("◎", "Credit balance")
                ProductType.IAP -> ProductIconSpec("□", "One-time purchase")
                ProductType.UNKNOWN -> ProductIconSpec("·", "Product")
            }
            else -> when (productType) {
                ProductType.SUBSCRIPTION -> ProductIconSpec("♛", "Premium membership")
                ProductType.CONSUMABLE -> ProductIconSpec("✦", "Creation credits")
                ProductType.IAP -> ProductIconSpec("↗", "Feature unlock")
                ProductType.UNKNOWN -> ProductIconSpec("•", "Product")
            }
        }
    }
}

internal object SubscriptionPageThemeResolver {
    fun resolve(templateId: String): SubscriptionPageTheme {
        return when (normalize(templateId)) {
            SubscriptionPageTemplates.MIDNIGHT -> midnight
            SubscriptionPageTemplates.MINIMAL -> minimal
            else -> aurora
        }
    }

    fun normalize(templateId: String): String {
        return when (templateId.trim().lowercase()) {
            SubscriptionPageTemplates.MIDNIGHT -> SubscriptionPageTemplates.MIDNIGHT
            SubscriptionPageTemplates.MINIMAL -> SubscriptionPageTemplates.MINIMAL
            else -> SubscriptionPageTemplates.AURORA
        }
    }

    private val aurora = SubscriptionPageTheme(
        id = SubscriptionPageTemplates.AURORA,
        sharedAppsInteraction = SharedAppsInteraction.AUTO_CAROUSEL,
        productCardLayout = ProductCardLayout.FEATURE,
        dark = false,
        pageBackground = argb(0xF7F8FC),
        surface = argb(0xFFFFFF),
        elevatedSurface = argb(0xF2F7FF),
        primary = argb(0x1264E8),
        accent = argb(0x08A6B5),
        title = argb(0x141827),
        body = argb(0x323849),
        muted = argb(0x666E80),
        border = argb(0xDCE0EA),
        selectedSurface = argb(0xF4FBFF),
        tagSurface = argb(0xE6FAF7)
    )

    private val midnight = SubscriptionPageTheme(
        id = SubscriptionPageTemplates.MIDNIGHT,
        sharedAppsInteraction = SharedAppsInteraction.MANUAL_RAIL,
        productCardLayout = ProductCardLayout.OFFER,
        dark = true,
        pageBackground = argb(0x13161C),
        surface = argb(0x23272F),
        elevatedSurface = argb(0x2B2F3A),
        primary = argb(0x58BFFF),
        accent = argb(0x63E6BF),
        title = argb(0xFFFFFF),
        body = argb(0xE2E6EE),
        muted = argb(0xA6AEBE),
        border = argb(0x484E5B),
        selectedSurface = argb(0x273953),
        tagSurface = argb(0x234741)
    )

    private val minimal = SubscriptionPageTheme(
        id = SubscriptionPageTemplates.MINIMAL,
        sharedAppsInteraction = SharedAppsInteraction.TWO_ROW_GRID,
        productCardLayout = ProductCardLayout.COMPACT,
        dark = false,
        pageBackground = argb(0xFFFFFF),
        surface = argb(0xFFFFFF),
        elevatedSurface = argb(0xF6F8FB),
        primary = argb(0xF04B3A),
        accent = argb(0xC28A2C),
        title = argb(0x161B22),
        body = argb(0x374151),
        muted = argb(0x6B7280),
        border = argb(0xD1D5DB),
        selectedSurface = argb(0xFFF8F5),
        tagSurface = argb(0xF8F3E5)
    )

    private fun argb(rgb: Int): Int = (0xFF000000L or rgb.toLong()).toInt()
}
