package com.example.listacompracompose

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
            // El producto vuelve a existir en local con un borrado de ese nombre aún sin subir.
            // Si era una compra, tiene que llegar antes al servidor para que sume al inventario:
            // hasta entonces no subimos el nuevo. Si solo se quitó de la lista, ya no vale.
            val pending = db.pendingDeleteDao().get(PendingDelete.KIND_PRODUCT, product.name)
            if (pending != null) {
                if (!pending.bought) discardPendingDelete(PendingDelete.KIND_PRODUCT, product.name)
                else if (!attemptPendingDelete(pending)) return@runCatching
            }
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
                    db.pendingUploadDao().delete(product.name)
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

    /**
     * [bought] distingue "lo he comprado" de "lo quito de la lista". En Supabase hay un
     * trigger que mete en el inventario de casa todo producto que se borra estando marcado,
     * así que antes de borrarlo se le pone la marca que toca: marcado solo si se ha comprado
     * y estaba marcado en el móvil, desmarcado en cualquier otro caso.
     *
     * El borrado tiene que estar apuntado antes con [markProductDeletePending]: si no llega
     * al servidor se queda pendiente y se reintenta en [reconcile] y [pullChanges].
     */
    suspend fun pushProductDelete(name: String, bought: Boolean = false) = withContext(Dispatchers.IO) {
        runCatching {
            if (!SupabaseConfig.isConfigured) return@runCatching
            attemptPendingDelete(PendingDelete(PendingDelete.KIND_PRODUCT, name, bought))
        }.onFailure { Log.w(TAG, "pushProductDelete error", it) }
    }

    /**
     * Hay que llamarlo antes de borrar el producto en local: guarda si estaba marcado aquí y
     * con qué cantidad, porque pudo cambiarse sin red y no haber llegado al servidor. Solo cuenta para
     * el inventario lo que se ha comprado y además estaba marcado.
     */
    suspend fun markProductDeletePending(name: String, bought: Boolean = false) = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext
        db.pendingUploadDao().delete(name)
        val pendingDao = db.pendingDeleteDao()
        val local = db.productDao().getByName(name)
        val previous = pendingDao.get(PendingDelete.KIND_PRODUCT, name)
        // Ya borrado en local y con su borrado apuntado: no pisamos lo que se guardó entonces.
        if (local == null && previous != null) return@withContext
        val counts = bought && local?.isChecked == true
        var quantity = local?.quantity
        if (previous?.bought == true) {
            // Queda una compra anterior sin subir y el producto se volvió a añadir (el nuevo
            // no ha llegado al servidor): esa compra no se puede perder.
            if (!counts) return@withContext
            if (quantity != null && previous.quantity != null) quantity += previous.quantity
        }
        pendingDao.insert(PendingDelete(PendingDelete.KIND_PRODUCT, name, counts, quantity))
    }

    /**
     * Hay que llamarlo antes de crear un producto nuevo en local. Hasta que el servidor lo
     * confirme, [pullProducts] no lo trata como borrado en remoto por no encontrarlo allí.
     */
    suspend fun markProductUploadPending(name: String) = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext
        db.pendingUploadDao().insert(PendingUpload(name))
    }

    /** Reintenta subir los productos nuevos que no llegaron. Para en el primero que falla. */
    private suspend fun pushPendingUploads() {
        val dao = db.pendingUploadDao()
        for (name in dao.getAllNames()) {
            val local = db.productDao().getByName(name)
            if (local == null) {
                dao.delete(name)
                continue
            }
            pushProduct(local)
            if (dao.count(name) > 0) return
        }
    }

    suspend fun markTemplateDeletePending(name: String) = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext
        db.pendingDeleteDao().insert(PendingDelete(PendingDelete.KIND_TEMPLATE, name))
    }

    private suspend fun discardPendingDelete(kind: String, name: String) {
        deleteLock.withLock { db.pendingDeleteDao().delete(kind, name) }
    }

    /**
     * Manda al servidor un borrado pendiente y lo da por hecho solo si responde bien.
     * Devuelve false si el servidor lo rechaza; si no hay red lanza la excepción.
     * El cerrojo evita borrar en remoto un producto que se acaba de volver a añadir.
     */
    private suspend fun attemptPendingDelete(pending: PendingDelete): Boolean = deleteLock.withLock {
        val dao = db.pendingDeleteDao()
        val current = dao.get(pending.kind, pending.name) ?: return@withLock true
        val table = if (current.kind == PendingDelete.KIND_TEMPLATE) "templates" else "products"
        val url = SupabaseConfig.restUrl(table).toHttpUrl().newBuilder()
            .addQueryParameter("name", "eq.${current.name}")
            .build()
        if (current.kind == PendingDelete.KIND_PRODUCT) {
            // Deja la marca del servidor como estaba en el móvil al borrar, para que el
            // trigger del inventario cuente justo lo comprado.
            // La cantidad solo importa si va a sumar al inventario.
            val quantity = if (current.bought && current.quantity != null) ""","quantity":${current.quantity}""" else ""
            val check = """{"is_checked":${current.bought}$quantity}""".toRequestBody(SupabaseConfig.jsonMediaType)
            val checked = client.newCall(requestBuilder(url).patch(check).build()).execute().use { it.isSuccessful }
            if (!checked) {
                Log.w(TAG, "No se pudo actualizar la marca de ${current.name} antes de borrarlo")
                return@withLock false
            }
        }
        val deleted = client.newCall(requestBuilder(url).delete().build()).execute().use { response ->
            if (!response.isSuccessful) Log.w(TAG, "Borrado de ${current.name} falló: ${response.code}")
            response.isSuccessful
        }
        if (deleted) dao.delete(current.kind, current.name)
        deleted
    }

    /** Reintenta los borrados que no llegaron. Si no hay red lo deja para la próxima vez. */
    private suspend fun flushPendingDeletes() {
        for (pending in db.pendingDeleteDao().getAll()) {
            try {
                if (attemptPendingDelete(pending) && pending.kind == PendingDelete.KIND_PRODUCT) {
                    // Si se volvió a añadir mientras tanto, ahora sí se puede subir el nuevo.
                    db.productDao().getByName(pending.name)?.let { pushProduct(it) }
                }
            } catch (e: java.io.IOException) {
                Log.w(TAG, "Borrados pendientes: sin conexión", e)
                return
            }
        }
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
            discardPendingDelete(PendingDelete.KIND_TEMPLATE, template.name)
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
            attemptPendingDelete(PendingDelete(PendingDelete.KIND_TEMPLATE, name))
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
            flushPendingDeletes()
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
            flushPendingDeletes()
            pushPendingUploads()
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
        // Los que aún no se han podido subir no cuentan: no estar en remoto no es un borrado.
        val remoteNames = remoteItems.map { it.name.lowercase() }.toSet()
        val notUploaded = db.pendingUploadDao().getAllNames().map { it.lowercase() }.toSet()
        for (local in dao.getAllOnce()) {
            val key = local.name.lowercase()
            if (key !in remoteNames && key !in notUploaded && local.updatedAt < syncStart - 5000) {
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
        // Borrado en local pero aún sin subir: la fila remota es la que va a desaparecer, así
        // que ni la resucitamos ni dejamos que pise a un producto vuelto a añadir.
        if (db.pendingDeleteDao().get(PendingDelete.KIND_PRODUCT, remote.name) != null) return@withContext
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
        // Un producto que aún no ha subido no puede ser el que se ha borrado en remoto.
        if (db.pendingUploadDao().count(name) > 0) return@withContext
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
        if (local == null && db.pendingDeleteDao().get(PendingDelete.KIND_TEMPLATE, remote.name) != null) return@withContext
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

        // Compartido entre las instancias de Repository, SyncWorker y RealtimeManager.
        private val deleteLock = Mutex()
    }
}
