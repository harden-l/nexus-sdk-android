package com.nexus.sdk.growth.bi

internal class UserPropertiesStore {
    private val properties = linkedMapOf<String, Any?>()

    @Synchronized
    fun set(values: Map<String, Any?>) {
        values.forEach { (key, value) ->
            if (isValidKey(key)) {
                if (value == null) {
                    properties.remove(key)
                } else {
                    properties[key] = value
                }
            }
        }
    }

    @Synchronized
    fun snapshot(): Map<String, Any?> = properties.toMap()

    private fun isValidKey(key: String): Boolean {
        return key.isNotBlank() && key.length <= 40 && key.matches(Regex("[A-Za-z][A-Za-z0-9_]*"))
    }
}
