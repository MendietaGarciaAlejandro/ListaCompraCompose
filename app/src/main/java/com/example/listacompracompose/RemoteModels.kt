package com.example.listacompracompose

import com.squareup.moshi.Json

data class RemoteProduct(
    val id: String? = null,
    val name: String,
    val price: Double,
    val quantity: Int,
    @Json(name = "is_checked") val isChecked: Boolean = false,
    val category: String = "General",
    @Json(name = "updated_at") val updatedAt: String? = null
)

data class RemoteProductHistory(
    val name: String,
    val price: Double,
    val quantity: Int,
    val category: String = "General",
    @Json(name = "is_favorite") val isFavorite: Boolean = false,
    @Json(name = "updated_at") val updatedAt: String? = null
)

data class RemoteTemplateProduct(
    val name: String,
    val price: Double,
    val quantity: Int,
    val category: String = "General"
)

data class RemoteTemplate(
    val id: String? = null,
    val name: String,
    val products: List<RemoteTemplateProduct>,
    @Json(name = "updated_at") val updatedAt: String? = null
)

data class RemotePurchaseLog(
    val id: String? = null,
    val name: String,
    val price: Double,
    val quantity: Int,
    @Json(name = "purchased_at") val purchasedAt: String? = null
)

data class RemoteShoppingSession(
    val id: String? = null,
    @Json(name = "session_at") val sessionAt: String? = null,
    @Json(name = "item_count") val itemCount: Int = 0,
    @Json(name = "total_spent") val totalSpent: Double = 0.0
)

// Fila del inventario de casa (lo gestiona la app InventarioCasa y la web).
// Aquí solo se lee, para avisar de lo que ya hay antes de comprarlo.
data class RemoteInventoryItem(
    val name: String,
    val quantity: Int = 0,
    val status: String = "ok"
)

/** Texto del aviso "ya está en casa", o null si no hay nada que avisar. */
fun stockHintFor(stock: Map<String, RemoteInventoryItem>, name: String): String? {
    val item = stock[name.trim().lowercase()] ?: return null
    if (item.status == "out" || item.quantity <= 0) return null
    return if (item.status == "low") "Queda poco en casa (${item.quantity})"
           else "Ya hay ${item.quantity} en casa"
}
