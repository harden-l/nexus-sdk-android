package com.nexus.sdk.payment.entitlement

import android.content.Context
import android.content.SharedPreferences
import com.nexus.sdk.payment.config.PaymentChannel
import com.nexus.sdk.payment.products.Product
import com.nexus.sdk.payment.order_verify.OrderStatus
import com.nexus.sdk.payment.order_verify.OrderVerificationResult
import org.json.JSONArray
import org.json.JSONObject

data class Entitlement(
    val entitlementId: String,
    val productId: String,
    val orderId: String,
    val channel: PaymentChannel,
    val startedTime: Long? = null,
    val endsTime: Long? = null,
    val active: Boolean = true
)

internal class EntitlementStore(
    context: Context? = null,
    productId: String = "test"
) {
    private val preferences: SharedPreferences? = context?.getSharedPreferences(
        "nexus_payment_${productId}_entitlements",
        Context.MODE_PRIVATE
    )
    private val memoryStates = mutableMapOf<String, State>()

    @Synchronized
    fun grant(
        product: Product,
        orderId: String,
        channel: PaymentChannel,
        verification: OrderVerificationResult?,
        uid: String = DEFAULT_UID,
        deliveryId: String = orderId
    ): Entitlement? {
        val state = load(uid)
        if (!state.deliveredOrderIds.add(deliveryId)) return null
        val entitlementId = product.entitlementId ?: product.marketProductId
        val entitlement = Entitlement(
            entitlementId = entitlementId,
            productId = product.marketProductId,
            orderId = orderId,
            channel = channel,
            startedTime = verification?.startedTime,
            endsTime = verification?.endsTime,
            active = verification?.status?.let { it == OrderStatus.SUCCESS } ?: true
        )
        state.entitlements[entitlementId] = entitlement
        save(uid, state)
        return entitlement
    }

    @Synchronized
    fun revoke(
        product: Product,
        orderId: String,
        channel: PaymentChannel,
        verification: OrderVerificationResult?,
        uid: String = DEFAULT_UID
    ) {
        val state = load(uid)
        val entitlementId = product.entitlementId ?: product.marketProductId
        state.entitlements[entitlementId] = Entitlement(
            entitlementId = entitlementId,
            productId = product.marketProductId,
            orderId = orderId,
            channel = channel,
            startedTime = verification?.startedTime,
            endsTime = verification?.endsTime,
            active = false
        )
        save(uid, state)
    }

    @Synchronized
    fun getEntitlements(uid: String = DEFAULT_UID): List<Entitlement> = load(uid).entitlements.values.toList()

    @Synchronized
    fun hasDelivered(orderId: String, uid: String = DEFAULT_UID): Boolean = orderId in load(uid).deliveredOrderIds

    @Synchronized
    fun clear(uid: String = DEFAULT_UID) {
        memoryStates.remove(uid)
        preferences?.edit()?.remove(storageKey(uid))?.apply()
    }

    private fun load(uid: String): State {
        memoryStates[uid]?.let { return it }
        val raw = preferences?.getString(storageKey(uid), null)
        val state = raw?.let(::decode) ?: State()
        memoryStates[uid] = state
        return state
    }

    private fun save(uid: String, state: State) {
        memoryStates[uid] = state
        preferences?.edit()?.putString(storageKey(uid), encode(state))?.commit()
    }

    private fun storageKey(uid: String) = "user_$uid"

    private fun encode(state: State): String {
        val root = JSONObject()
        root.put("delivered_order_ids", JSONArray(state.deliveredOrderIds.toList()))
        val values = JSONArray()
        state.entitlements.values.forEach { entitlement ->
            values.put(JSONObject().apply {
                put("entitlement_id", entitlement.entitlementId)
                put("product_id", entitlement.productId)
                put("order_id", entitlement.orderId)
                put("channel", entitlement.channel.wireValue)
                put("started_time", entitlement.startedTime ?: JSONObject.NULL)
                put("ends_time", entitlement.endsTime ?: JSONObject.NULL)
                put("active", entitlement.active)
            })
        }
        root.put("entitlements", values)
        return root.toString()
    }

    private fun decode(raw: String): State = runCatching {
        val root = JSONObject(raw)
        val delivered = mutableSetOf<String>()
        root.optJSONArray("delivered_order_ids")?.let { values ->
            repeat(values.length()) { index -> values.optString(index).takeIf(String::isNotBlank)?.let(delivered::add) }
        }
        val entitlements = linkedMapOf<String, Entitlement>()
        root.optJSONArray("entitlements")?.let { values ->
            repeat(values.length()) { index ->
                val value = values.optJSONObject(index) ?: return@repeat
                val entitlementId = value.optString("entitlement_id")
                val channel = PaymentChannel.entries.firstOrNull { it.wireValue == value.optString("channel") }
                    ?: return@repeat
                if (entitlementId.isBlank()) return@repeat
                entitlements[entitlementId] = Entitlement(
                    entitlementId = entitlementId,
                    productId = value.optString("product_id"),
                    orderId = value.optString("order_id"),
                    channel = channel,
                    startedTime = value.optNullableLong("started_time"),
                    endsTime = value.optNullableLong("ends_time"),
                    active = value.optBoolean("active", false)
                )
            }
        }
        State(delivered, entitlements)
    }.getOrDefault(State())

    private fun JSONObject.optNullableLong(key: String): Long? {
        return if (has(key) && !isNull(key)) optLong(key) else null
    }

    private data class State(
        val deliveredOrderIds: MutableSet<String> = mutableSetOf(),
        val entitlements: LinkedHashMap<String, Entitlement> = linkedMapOf()
    )

    private companion object {
        const val DEFAULT_UID = "default"
    }
}
