package com.example.listacompracompose

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicInteger

/**
 * Conexión en vivo (WebSocket) a Supabase Realtime, para reflejar al instante en el móvil
 * los cambios hechos desde la web. Usa el protocolo Phoenix Channels que expone Supabase
 * ("postgres_changes"). Si la conexión falla o se cae, [SyncManager.reconcile] (llamado al
 * abrir la app, al recuperar red y cada 30 min) actúa como red de seguridad, así que un fallo
 * aquí nunca deja la app permanentemente desincronizada.
 */
class RealtimeManager(private val db: AppDatabase) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val moshi = Moshi.Builder().build()
    private val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(mapType)

    private val sync = SyncManager(db)
    private var webSocket: WebSocket? = null
    private val refCounter = AtomicInteger(1)
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private var stopped = true

    private val watchedTables = listOf("products", "product_history", "templates")

    fun start() {
        if (!SupabaseConfig.isConfigured || !stopped) return
        stopped = false
        connect()
    }

    fun stop() {
        stopped = true
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        webSocket?.close(1000, "app en segundo plano")
        webSocket = null
    }

    /** Fuerza una reconexión inmediata, p.ej. cuando vuelve la conexión a internet. */
    fun reconnectNow() {
        if (stopped) return
        webSocket?.close(1000, "reconectando")
        connect()
    }

    private fun connect() {
        runCatching {
            val wsBase = SupabaseConfig.baseUrl
                .replaceFirst("https://", "wss://")
                .replaceFirst("http://", "ws://")
            val url = "$wsBase/realtime/v1/websocket?apikey=${SupabaseConfig.anonKey}&vsn=1.0.0"
            val request = Request.Builder().url(url).build()
            webSocket = SupabaseConfig.httpClient.newWebSocket(request, listener)
        }.onFailure { Log.w(TAG, "No se pudo conectar a Realtime", it) }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "Realtime conectado")
            reconnectAttempts = 0
            watchedTables.forEach { table -> joinChannel(webSocket, table) }
            startHeartbeat(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleMessage(text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "Realtime conexión perdida", t)
            scheduleReconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!stopped) scheduleReconnect()
        }
    }

    private fun joinChannel(ws: WebSocket, table: String) {
        val message = mapOf(
            "topic" to "realtime:public:$table",
            "event" to "phx_join",
            "payload" to mapOf(
                "config" to mapOf(
                    "postgres_changes" to listOf(
                        mapOf("event" to "*", "schema" to "public", "table" to table)
                    )
                ),
                "access_token" to SupabaseConfig.anonKey
            ),
            "ref" to refCounter.getAndIncrement().toString()
        )
        ws.send(mapAdapter.toJson(message))
    }

    private fun startHeartbeat(ws: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                delay(25_000)
                val heartbeat = mapOf(
                    "topic" to "phoenix",
                    "event" to "heartbeat",
                    "payload" to emptyMap<String, Any?>(),
                    "ref" to refCounter.getAndIncrement().toString()
                )
                ws.send(mapAdapter.toJson(heartbeat))
            }
        }
    }

    private fun scheduleReconnect() {
        if (stopped) return
        heartbeatJob?.cancel()
        reconnectAttempts++
        val delayMs = minOf(30_000L, 2_000L * reconnectAttempts)
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            if (!stopped) connect()
        }
    }

    private fun handleMessage(text: String) {
        scope.launch {
            runCatching {
                val message = mapAdapter.fromJson(text) ?: return@launch
                if (message["event"] as? String != "postgres_changes") return@launch

                @Suppress("UNCHECKED_CAST")
                val payload = message["payload"] as? Map<String, Any?> ?: return@launch
                @Suppress("UNCHECKED_CAST")
                val data = payload["data"] as? Map<String, Any?> ?: return@launch

                val type = data["type"] as? String ?: return@launch
                val table = data["table"] as? String ?: return@launch
                @Suppress("UNCHECKED_CAST")
                val record = data["record"] as? Map<String, Any?>
                @Suppress("UNCHECKED_CAST")
                val oldRecord = data["old_record"] as? Map<String, Any?>

                when (table) {
                    "products" -> handleProductChange(type, record, oldRecord)
                    "product_history" -> handleHistoryChange(type, record)
                    "templates" -> handleTemplateChange(type, record, oldRecord)
                }
            }.onFailure { Log.w(TAG, "Mensaje de Realtime no procesado", it) }
        }
    }

    private suspend fun handleProductChange(type: String, record: Map<String, Any?>?, oldRecord: Map<String, Any?>?) {
        if (type == "DELETE") {
            val name = oldRecord?.get("name") as? String ?: return
            sync.applyRemoteProductDelete(name)
            return
        }
        record?.toRemoteProduct()?.let { sync.applyRemoteProduct(it) }
    }

    private suspend fun handleHistoryChange(type: String, record: Map<String, Any?>?) {
        if (type == "DELETE") return // el historial no se borra por sincronización
        record?.toRemoteProductHistory()?.let { sync.applyRemoteProductHistory(it) }
    }

    private suspend fun handleTemplateChange(type: String, record: Map<String, Any?>?, oldRecord: Map<String, Any?>?) {
        if (type == "DELETE") {
            val name = oldRecord?.get("name") as? String ?: return
            sync.applyRemoteTemplateDelete(name)
            return
        }
        record?.toRemoteTemplate()?.let { sync.applyRemoteTemplate(it) }
    }

    companion object {
        private const val TAG = "RealtimeManager"
    }
}

// ── Conversión defensiva de un registro JSON crudo a los DTO remotos ──────────

private fun Map<String, Any?>.toRemoteProduct(): RemoteProduct? {
    val name = this["name"] as? String ?: return null
    return RemoteProduct(
        id = this["id"] as? String,
        name = name,
        price = (this["price"] as? Number)?.toDouble() ?: 0.0,
        quantity = (this["quantity"] as? Number)?.toInt() ?: 1,
        isChecked = this["is_checked"] as? Boolean ?: false,
        category = this["category"] as? String ?: "General",
        updatedAt = this["updated_at"] as? String
    )
}

private fun Map<String, Any?>.toRemoteProductHistory(): RemoteProductHistory? {
    val name = this["name"] as? String ?: return null
    return RemoteProductHistory(
        name = name,
        price = (this["price"] as? Number)?.toDouble() ?: 0.0,
        quantity = (this["quantity"] as? Number)?.toInt() ?: 1,
        category = this["category"] as? String ?: "General",
        isFavorite = this["is_favorite"] as? Boolean ?: false,
        updatedAt = this["updated_at"] as? String
    )
}

private fun Map<String, Any?>.toRemoteTemplate(): RemoteTemplate? {
    val name = this["name"] as? String ?: return null
    @Suppress("UNCHECKED_CAST")
    val rawProducts = this["products"] as? List<Map<String, Any?>> ?: emptyList()
    return RemoteTemplate(
        id = this["id"] as? String,
        name = name,
        products = rawProducts.map {
            RemoteTemplateProduct(
                name = it["name"] as? String ?: "",
                price = (it["price"] as? Number)?.toDouble() ?: 0.0,
                quantity = (it["quantity"] as? Number)?.toInt() ?: 1,
                category = it["category"] as? String ?: "General"
            )
        },
        updatedAt = this["updated_at"] as? String
    )
}
