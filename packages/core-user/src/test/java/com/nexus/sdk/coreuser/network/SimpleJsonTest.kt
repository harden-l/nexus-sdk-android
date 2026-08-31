package com.nexus.sdk.coreuser.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleJsonTest {
    @Test
    fun stringifiesValues() {
        val json = SimpleJson.stringify(
            mapOf(
                "uid" to "user_10001",
                "level" to 1,
                "is_has_sim" to false,
                "email" to null
            )
        )

        assertTrue(json.contains("\"uid\":\"user_10001\""))
        assertTrue(json.contains("\"level\":1"))
        assertTrue(json.contains("\"is_has_sim\":false"))
        assertTrue(!json.contains("email"))
    }

    @Test
    fun readsPrimitiveValues() {
        val json = """
            {
              "code": 1,
              "uid": "user_10001",
              "bound": true
            }
        """.trimIndent()

        assertEquals(1, SimpleJson.findInt(json, "code"))
        assertEquals("user_10001", SimpleJson.findString(json, "uid"))
        assertEquals(true, SimpleJson.findBoolean(json, "bound"))
    }

    @Test
    fun readsRootAndDataValues() {
        val json = """
            {
              "code": 1,
              "message": "success",
              "data": {
                "uid": "user_10001",
                "email": "user@example.com",
                "phone": "",
                "email_bound": true,
                "phone_bound": false
              }
            }
        """.trimIndent()

        assertEquals(1, SimpleJson.findRootInt(json, "code"))
        assertEquals("success", SimpleJson.findRootString(json, "message"))
        assertEquals("user_10001", SimpleJson.findDataString(json, "uid"))
        assertEquals("user@example.com", SimpleJson.findDataString(json, "email"))
        assertEquals(true, SimpleJson.findDataBoolean(json, "email_bound"))
        assertEquals(false, SimpleJson.findDataBoolean(json, "phone_bound"))
    }

    @Test
    fun readsDynamicDataObjectMap() {
        val json = """
            {
              "code": 1,
              "data": {
                "uid": "user_10001",
                "level": 3,
                "vip": true,
                "campaign": "summer",
                "limits": {
                  "daily": 10
                },
                "features": ["a", "b"]
              }
            }
        """.trimIndent()

        val values = SimpleJson.findDataObjectMap(json).filterKeys { it != "uid" }

        assertEquals(3L, values["level"])
        assertEquals(true, values["vip"])
        assertEquals("summer", values["campaign"])
        assertEquals(mapOf("daily" to 10L), values["limits"])
        assertEquals("[\"a\", \"b\"]", values["features"])
    }
}
