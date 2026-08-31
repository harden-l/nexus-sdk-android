package com.nexus.sdk.growth.bi

import com.nexus.sdk.growth.bi.UserPropertiesStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UserPropertiesStoreTest {
    @Test
    fun storesValidPropertiesAndFiltersInvalidKeys() {
        val store = UserPropertiesStore()

        store.set(
            mapOf(
                "plan" to "free",
                "country" to "US",
                "bad-key" to "ignored"
            )
        )

        assertEquals("free", store.snapshot()["plan"])
        assertEquals("US", store.snapshot()["country"])
        assertFalse(store.snapshot().containsKey("bad-key"))
    }

    @Test
    fun nullValueClearsProperty() {
        val store = UserPropertiesStore()

        store.set(mapOf("plan" to "free"))
        store.set(mapOf("plan" to null))

        assertFalse(store.snapshot().containsKey("plan"))
    }
}
