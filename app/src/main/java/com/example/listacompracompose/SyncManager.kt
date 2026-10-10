package com.example.listacompracompose

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Sincroniza la base de datos local (Room) con Supabase, la misma base de datos
 * que usa la web. Room sigue siendo la fuente de verdad offline: cada escritura
 * local se empuja a Supabase en segundo plano (best-effort), y [reconcile] trae
 * los cambios remotos aplicando "gana el más reciente" comparando updatedAt.
 */
class SyncManager(private val db: AppDatabase) {

    private val moshi = Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()
    private val client = SupabaseConfig.httpClient

    private val productAdapter = moshi.adapter(RemoteProduct::class.java)
    private val productListAdapter =
        moshi.adapter<List<RemoteProduct>>(Types.newParameterizedType(List::class.java, RemoteProduct::class.java))
    private val historyListAdapter =
        moshi.adapter<List<RemoteProductHistory>>(Types.newParameterizedType(List::class.java, RemoteProductHistory::class.java))
    private val templateListAdapter =
        moshi.adapter<List<RemoteTemplate>>(Types.newParameterizedType(List::class.java, RemoteTemplate::class.java))
    private val purchaseLogListAdapter =
        moshi.adapter<List<RemotePurchaseLog>>(Types.newParameterizedType(List::class.java, RemotePurchaseLog::class.java))
    private val sessionListAdapter =
        moshi.adapter<List<RemoteShoppingSession>>(Types.newParameterizedType(List::class.java, RemoteShoppingSession::class.java))

    private val inventoryListAdapter =
        moshi.adapter<List<RemoteInventoryItem>>(Types.newParameterizedType(List::class.java, RemoteInventoryItem::class.java))

    private fun requestBuilder(url: okhttp3.HttpUrl, prefer: String? = null): Request.Builder {
        val builder = Request.Builder().url(url)
            .addHeader("apikey", SupabaseConfig.anonKey)
            .addHeader("Authorization", "Bearer ${SupabaseConfig.anonKey}")
            .addHeader("Content-Type", "application/json")
        if (prefer != null) builder.addHeader("Prefer", prefer)
        return builder
    }

    // ── Push (llamado tras cada escritura local) ────────────────────────────

    suspend fun pushProduct(product: Product) = withContext(Dispatchers.IO) {
        runCatching {
            if (!SupabaseConfig.isConfigured) return@runCatching
            val remote = RemoteProduct(
                name = product.name,
                price = product.price,
                quantity = product.quantity,
                isChecked = product.isChecked,
                category = product.category
            )
            val url = SupabaseConfig.restUrl("products").toHttpUrl().newBuilder()
                .addQueryParameter("on_conflict", "name")
                .build()
            val body = productListAdapter.toJson(listOf(remote)).toRequestBody(SupabaseConfig.jsonMediaType)
            val request = requestBuilder(url, prefer = "resolution=merge-duplicates,return=representation")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val results = response.body?.string()?.let { productListAdapter.fromJson(it) }
                    val updatedAt = results?.firstOrNull()?.updatedAt
                    if (updatedAt != null) {
                        db.productDao().touchUpdatedAt(product.name, SupabaseConfig.parseTimestamp(updatedAt))
                    }
                } else {
                    Log.w(TAG, "pushProduct falló: ${response.code}")
                }
            }
        }.onFailure { Log.w(TAG, "pushProduct error", it) }
    }

    suspend fun pushProductDelete(name: String) = withContext(Dispatchers.IO) {
        runCatching {
            if (!SupabaseConfig.isConfigured) return@runCatching
            val url = SupabaseConfig.restUrl("products").toHttpUrl().newBuilder()
                .addQueryParameter("name", "eq.$name")
                .build()
            client.newCall(requestBuilder(url).delete().build()).execute().close()
        }.onFailure { Log.w(TAG, "pushProductDelete error", it) }
    }

    suspend fun pushProductHistory(entry: ProductHistory) = withContext(Dispatchers.IO) {
        runCatching {
            if (!SupabaseConfig.isConfigured) return@runCatching
            val remote = RemoteProductHistory(
                name = entry.name,
                price = entry.price,
                quantity = entry.quantity,
                category = entry.category,
                isFavorite = entry.isFavorite
            )
            val url = SupabaseConfig.restUrl("product_history").toHttpUrl().newBuilder()
                .addQueryParameter("on_conflict", "name")
                .build()
            val body = historyListAdapter.toJson(listOf(remote)).toRequestBody(SupabaseConfig.jsonMediaType)
            val request = requestBuilder(url, prefer = "resolution=merge-duplicates,return=minimal")
                .post(body)
                .build()
            client.newCall(request).execute().close()
        }.onFailure { Log.w(TAG, "pushProductHistory error", it) }
    }

    suspend fun pushTemplate(template: Template) = withContext(Dispatchers.IO) {
        runCatching {
            if (!SupabaseConfig.isConfigured) return@runCatching
            val remote = RemoteTemplate(
                name = template.name,
                products = template.products.map {
                    RemoteTemplateProduct(it.name, it.price, it.quantity, it.category)
                }
            )
            val url = SupabaseConfig.restUrl("templates").toHttpUrl().newBuilder()
                .addQueryParameter("on_conflict", "name")
                .build()
            val body = templateListAdapter.toJson(listOf(remote)).toRequestBody(SupabaseConfig.jsonMediaType)
            val request = requestBuilder(url, prefer = "resolution=merge-duplicates,return=minimal")
                .post(body)
                .build()
            client.newCall(request).execute().close()
        }.onFailure { Log.w(TAG, "pushTemplate error", it) }
    }

    suspend fun pushTemplateDelete(name: String) = withContext(Dispatchers.IO) {
        runCatching {
            if (!SupabaseConfig.isConfigured) return@runCatching
            val url = SupabaseConfig.restUrl("templates").toHttpUrl().newBuilder()
                .addQueryParameter("name", "eq.$name")
                .build()
            client.newCall(requestBuilder(url).delete().build()).execute().close()
        }.onFailure { Log.w(TAG, "pushTemplateDelete error", it) }
    }

    suspend fun pushPurchaseLog(log: PurchaseLog) = withContext(Dispatchers.IO) {
        runCatching {
            if (!SupabaseConfig.isConfigured || log.remoteId != null) return@runCatching
            val remote = RemotePurchaseLog(name = log.name, price = log.price, quantity = log.quantity)
            val url = SupabaseConfig.restUrl("purchase_log").toHttpUrl()
            val body = purchaseLogListAdapter.toJson(listOf(remote)).toRequestBody(SupabaseConfig.jsonMediaType)
            val request = requestBuilder(url, prefer = "return=representation").post(body).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val results = response.body?.string()?.let { purchaseLogListAdapter.fromJson(it) }
                    results?.firstOrNull()?.id?.let { db.purchaseLogDao().setRemoteId(log.id, it) }
                } else {
                    Log.w(TAG, "pushPurchaseLog falló: ${response.code}")
                }
            }
        }.onFailure { Log.w(TAG, "pushPurchaseLog error", it) }
    }

    suspend fun pushShoppingSession(session: ShoppingSession) = withContext(Dispatchers.IO) {
        runCatching {
            if (!SupabaseConfig.isConfigured || session.remoteId != null) return@runCatching
            val remote = RemoteShoppingSession(itemCount = session.itemCount, totalSpent = session.totalSpent)
            val url = SupabaseConfig.restUrl("shopping_sessions").toHttpUrl()
            val body = sessionListAdapter.toJson(listOf(remote)).toRequestBody(SupabaseConfig.jsonMediaType)
            val request = requestBuilder(url, prefer = "return=representation").post(body).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val results = response.body?.string()?.let { sessionListAdapter.fromJson(it) }
                    results?.firstOrNull()?.id?.let { db.shoppingSessionDao().setRemoteId(session.id, it) }
                } else {
                    Log.w(TAG, "pushShoppingSession falló: ${response.code}")
                }
            }
        }.onFailure { Log.w(TAG, "pushShoppingSession error", it) }
    }

    // ── Reconciliación completa (push de lo pendiente + pull de lo remoto) ──

    suspend fun reconcile() = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext
        runCatching {
            pushAllPending()
            pullProducts()
            pullProductHistory()
            pullTemplates()
            pullPurchaseLogs()
            pullShoppingSessions()
        }.onFailure { Log.w(TAG, "reconcile error", it) }
    }

    /**
     * Solo trae cambios remotos (sin volver a empujar todo lo local). Pensado para
     * llamarse cada pocos segundos mientras la app está en primer plano, como red de
     * seguridad fiable frente al WebSocket de Realtime (que puede fallar en silencio
     * si el protocolo no encaja exactamente). Barato: son tablas pequeñas de uso personal.
     */
    suspend fun pullChanges() = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext
        runCatching {
            pullProducts()
            pullProductHistory()
            pullTemplates()
        }.onFailure { Log.w(TAG, "pullChanges error", it) }
    }

    private suspend fun pushAllPending() {
        db.productDao().getAllOnce().forEach { pushProduct(it) }
        db.productHistoryDao().getAllOnce().forEach { pushProductHistory(it) }
        db.templateDao().getAllOnce().forEach { pushTemplate(it) }
        db.purchaseLogDao().getAllLogs().filter { it.remoteId == null }.forEach { pushPurchaseLog(it) }
        db.shoppingSessionDao().getAll().filter { it.remoteId == null }.forEach { pushShoppingSession(it) }
    }

    /**
     * Inventario de casa, solo lectura. Devuelve null si no se pudo consultar
     * (sin red, tabla aún no creada...) para que quien llama conserve lo último que tenía.
     */
    suspend fun fetchInventory(): List<RemoteInventoryItem>? = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext null
        runCatching {
            fetchAll("inventory")?.let { inventoryListAdapter.fromJson(it) }
        }.onFailure { Log.w(TAG, "fetchInventory error", it) }.getOrNull()
    }

    private fun fetchAll(table: String): String? {
        val url = SupabaseConfig.restUrl(table).toHttpUrl().newBuilder()
            .addQueryParameter("select", "*")
            .build()
        val request = requestBuilder(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "fetchAll($table) falló: ${response.code}")
                return null
            }
            return response.body?.string()
        }
    }

    private suspend fun pullProducts() {
        val json = fetchAll("products") ?: return
        val remoteItems = productListAdapter.fromJson(json) ?: return
        val syncStart = System.currentTimeMillis()
        val dao = db.productDao()

        remoteItems.forEach { applyRemoteProduct(it) }

        // Propaga borrados hechos en remoto, evitando tocar filas locales muy recientes
        // (que podrían no haberse subido todavía).
        val remoteNames = remoteItems.map { it.name.lowercase() }.toSet()
        for (local in dao.getAllOnce()) {
            if (local.name.lowercase() !in remoteNames && local.updatedAt < syncStart - 5000) {
                dao.deleteByName(local.name)
            }
        }
    }

    private suspend fun pullProductHistory() {
        val json = fetchAll("product_history") ?: return
        val remoteItems = historyListAdapter.fromJson(json) ?: return
        remoteItems.forEach { applyRemoteProductHistory(it) }
    }

    private suspend fun pullTemplates() {
        val json = fetchAll("templates") ?: return
        val remoteItems = templateListAdapter.fromJson(json) ?: return
        val syncStart = System.currentTimeMillis()
        val dao = db.templateDao()

        remoteItems.forEach { applyRemoteTemplate(it) }

        val remoteNames = remoteItems.map { it.name.lowercase() }.toSet()
        for (local in dao.getAllOnce()) {
            if (local.name.lowercase() !in remoteNames && local.updatedAt < syncStart - 5000) {
                dao.deleteTemplate(local.name)
            }
        }
    }

    // ── Aplicar un cambio remoto individual (usado por pull y por Realtime) ─

    suspend fun applyRemoteProduct(remote: RemoteProduct) = withContext(Dispatchers.IO) {
        val dao = db.productDao()
        val remoteUpdatedAt = SupabaseConfig.parseTimestamp(remote.updatedAt)
        val local = dao.getByName(remote.name)
        if (local == null) {
            dao.insert(
                Product(
                    name = remote.name,
                    price = remote.price,
                    quantity = remote.quantity,
                    isChecked = remote.isChecked,
                    category = remote.category,
                    updatedAt = remoteUpdatedAt
                )
            )
        } else if (remoteUpdatedAt > local.updatedAt) {
            dao.update(
                local.copy(
                    price = remote.price,
                    quantity = remote.quantity,
                    isChecked = remote.isChecked,
                    category = remote.category,
                    updatedAt = remoteUpdatedAt
                )
            )
        }
    }

    suspend fun applyRemoteProductDelete(name: String) = withContext(Dispatchers.IO) {
        db.productDao().deleteByName(name)
    }

    suspend fun applyRemoteProductHistory(remote: RemoteProductHistory) = withContext(Dispatchers.IO) {
        val dao = db.productHistoryDao()
        val remoteUpdatedAt = SupabaseConfig.parseTimestamp(remote.updatedAt)
        val local = dao.getByName(remote.name)
        if (local == null || remoteUpdatedAt > local.updatedAt) {
            dao.upsert(
                ProductHistory(
                    name = remote.name,
                    price = remote.price,
                    quantity = remote.quantity,
                    category = remote.category,
                    isFavorite = remote.isFavorite,
                    updatedAt = remoteUpdatedAt
                )
            )
        }
    }

    suspend fun applyRemoteTemplate(remote: RemoteTemplate) = withContext(Dispatchers.IO) {
        val dao = db.templateDao()
        val remoteUpdatedAt = SupabaseConfig.parseTimestamp(remote.updatedAt)
        val local = dao.getTemplate(remote.name)
        if (local == null || remoteUpdatedAt > local.updatedAt) {
            dao.insertTemplate(
                Template(
                    name = remote.name,
                    products = remote.products.map {
                        Product(name = it.name, price = it.price, quantity = it.quantity, category = it.category)
                    },
                    updatedAt = remoteUpdatedAt
                )
            )
        }
    }

    suspend fun applyRemoteTemplateDelete(name: String) = withContext(Dispatchers.IO) {
        db.templateDao().deleteTemplate(name)
    }

    private suspend fun pullPurchaseLogs() {
        val json = fetchAll("purchase_log") ?: return
        val remoteItems = purchaseLogListAdapter.fromJson(json) ?: return
        val dao = db.purchaseLogDao()
        val knownRemoteIds = dao.getAllRemoteIds().toSet()

        for (remote in remoteItems) {
            val id = remote.id ?: continue
            if (id in knownRemoteIds) continue
            dao.insertFromRemote(
                PurchaseLog(
                    name = remote.name,
                    price = remote.price,
                    quantity = remote.quantity,
                    timestamp = SupabaseConfig.parseTimestamp(remote.purchasedAt),
                    remoteId = id
                )
            )
        }
    }

    private suspend fun pullShoppingSessions() {
        val json = fetchAll("shopping_sessions") ?: return
        val remoteItems = sessionListAdapter.fromJson(json) ?: return
        val dao = db.shoppingSessionDao()
        val knownRemoteIds = dao.getAllRemoteIds().toSet()

        for (remote in remoteItems) {
            val id = remote.id ?: continue
            if (id in knownRemoteIds) continue
            dao.insertFromRemote(
                ShoppingSession(
                    timestamp = SupabaseConfig.parseTimestamp(remote.sessionAt),
                    itemCount = remote.itemCount,
                    totalSpent = remote.totalSpent,
                    remoteId = id
                )
            )
        }
    }

    companion object {
        private const val TAG = "SyncManager"
    }
}
