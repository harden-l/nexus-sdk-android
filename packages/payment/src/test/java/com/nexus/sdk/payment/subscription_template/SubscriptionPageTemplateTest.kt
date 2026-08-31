package com.nexus.sdk.payment.subscription_template

import com.nexus.sdk.payment.products.ProductType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SubscriptionPageTemplateTest {
    @Test
    fun resolvesSupportedTemplatesAndFallsBackToAurora() {
        assertEquals(SubscriptionPageTemplates.AURORA, SubscriptionPageConfig().templateId)
        assertEquals(SubscriptionPageTemplates.AURORA, SubscriptionPageThemeResolver.resolve("").id)
        assertEquals(SubscriptionPageTemplates.AURORA, SubscriptionPageThemeResolver.resolve("default_subscription_v1").id)
        assertEquals(SubscriptionPageTemplates.MIDNIGHT, SubscriptionPageThemeResolver.resolve("MIDNIGHT").id)
        assertEquals(SubscriptionPageTemplates.MINIMAL, SubscriptionPageThemeResolver.resolve("minimal").id)
    }

    @Test
    fun assignsDistinctSharedAppInteractionsToEachTemplate() {
        assertEquals(
            SharedAppsInteraction.AUTO_CAROUSEL,
            SubscriptionPageThemeResolver.resolve(SubscriptionPageTemplates.AURORA).sharedAppsInteraction
        )
        assertEquals(
            SharedAppsInteraction.MANUAL_RAIL,
            SubscriptionPageThemeResolver.resolve(SubscriptionPageTemplates.MIDNIGHT).sharedAppsInteraction
        )
        assertEquals(
            SharedAppsInteraction.TWO_ROW_GRID,
            SubscriptionPageThemeResolver.resolve(SubscriptionPageTemplates.MINIMAL).sharedAppsInteraction
        )
    }

    @Test
    fun assignsDistinctProductLayoutsAndIconsToEachTemplate() {
        val themes = listOf(
            SubscriptionPageThemeResolver.resolve(SubscriptionPageTemplates.AURORA),
            SubscriptionPageThemeResolver.resolve(SubscriptionPageTemplates.MIDNIGHT),
            SubscriptionPageThemeResolver.resolve(SubscriptionPageTemplates.MINIMAL)
        )
        assertEquals(3, themes.map { it.productCardLayout }.toSet().size)

        themes.forEach { theme ->
            val subscription = ProductIconResolver.resolve(theme.id, ProductType.SUBSCRIPTION)
            val consumable = ProductIconResolver.resolve(theme.id, ProductType.CONSUMABLE)
            val iap = ProductIconResolver.resolve(theme.id, ProductType.IAP)
            assertNotEquals(subscription.symbol, consumable.symbol)
            assertNotEquals(subscription.symbol, iap.symbol)
            assertNotEquals(consumable.symbol, iap.symbol)
        }
        listOf(ProductType.SUBSCRIPTION, ProductType.CONSUMABLE, ProductType.IAP).forEach { productType ->
            assertEquals(
                3,
                themes.map { ProductIconResolver.resolve(it.id, productType).symbol }.toSet().size
            )
        }
    }
}
