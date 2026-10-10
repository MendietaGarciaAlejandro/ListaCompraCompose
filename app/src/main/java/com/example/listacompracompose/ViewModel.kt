// ViewModel.kt
package com.example.listacompracompose

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CompraViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val repo = CompraRepository(db, application)
    val items = repo.allItems.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val templates = repo.allTemplates.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ── Inventario de casa (solo lectura, para avisar de lo que ya hay) ──────
    private val stockPrefs = application.getSharedPreferences("compra_stock", Context.MODE_PRIVATE)
    private val _stock = MutableStateFlow(readCachedStock())
    val stock: StateFlow<Map<String, RemoteInventoryItem>> = _stock.asStateFlow()

    private suspend fun refreshStock() {
        val items = repo.fetchInventory() ?: return
        _stock.value = items.associateBy { it.name.trim().lowercase() }
        // Se guarda lo último visto para que el aviso siga saliendo sin cobertura en la tienda
        stockPrefs.edit()
            .putStringSet("items", items.map { "${it.quantity}|${it.status}|${it.name}" }.toSet())
            .apply()
    }

    private fun readCachedStock(): Map<String, RemoteInventoryItem> =
        stockPrefs.getStringSet("items", emptySet()).orEmpty().mapNotNull { line ->
            val parts = line.split("|", limit = 3)
            if (parts.size < 3) return@mapNotNull null
            RemoteInventoryItem(name = parts[2], quantity = parts[0].toIntOrNull() ?: 0, status = parts[1])
        }.associateBy { it.name.trim().lowercase() }

    // ── Sincronización con Supabase (Realtime + polling de seguridad) ────────
    private val realtime = RealtimeManager(db)

    init {
        viewModelScope.launch { repo.syncNow() }
        SyncWorker.schedule(application)
        realtime.start()
        NetworkMonitor(application).observe {
            viewModelScope.launch { repo.syncNow() }
            realtime.reconnectNow()
        }
        // Red de seguridad fiable: mientras la app está abierta, trae cambios remotos
        // cada pocos segundos. No depende de que el WebSocket de Realtime funcione.
        viewModelScope.launch {
            while (true) {
                delay(5_000)
                repo.pullRemoteChanges()
            }
        }
        // El inventario cambia poco: basta con refrescarlo de vez en cuando.
        viewModelScope.launch {
            while (true) {
                refreshStock()
                delay(30_000)
            }
        }
    }


    override fun onCleared() {
        super.onCleared()
        realtime.stop()
    }

    // ── Tema ──────────────────────────────────────────────────────────────────
    private val prefs = application.getSharedPreferences("compra_settings", Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(
        ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name)!!)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    // ── Productos ─────────────────────────────────────────────────────────────
    fun insert(prod: Product) = viewModelScope.launch { repo.insert(prod) }
    fun update(prod: Product) = viewModelScope.launch { repo.update(prod) }
    fun deleteByName(name: String) = viewModelScope.launch { repo.delete(name) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                CompraViewModel(app)
            }
        }
    }

    // ── Plantillas ────────────────────────────────────────────────────────────
    fun saveTemplate(name: String) = viewModelScope.launch { repo.saveTemplate(name, items.value) }
    suspend fun getTemplateProducts(name: String): List<Product> = repo.loadTemplate(name)
    fun loadTemplate(name: String) = viewModelScope.launch {
        val list = repo.loadTemplate(name)
        items.value.forEach { repo.delete(it.name) }
        list.forEach { repo.insert(it) }
    }
    fun deleteTemplate(name: String) = viewModelScope.launch { repo.deleteTemplate(name) }

    // ── Estado de items ───────────────────────────────────────────────────────
    fun toggleCheckedStatus(name: String) = viewModelScope.launch { repo.toggleCheckedStatus(name) }
    fun incrementQuantity(name: String) = viewModelScope.launch { repo.incrementQuantity(name) }
    fun reduceQuantity(name: String) = viewModelScope.launch { repo.reduceQuantity(name) }
    fun deleteAll(name: String) = viewModelScope.launch { repo.deleteAll(name) }

    suspend fun insertWithCheck(prod: Product): Boolean {
        return try { repo.insert(prod); true } catch (e: SQLiteConstraintException) { false }
    }

    fun getCategories(): List<String> = items.value.map { it.category }.distinct().sorted()

    // ── Orden de categorías ───────────────────────────────────────────────────
    private val _categoryOrder = MutableStateFlow(
        prefs.getString("category_order", "")
            ?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    )
    val categoryOrder: StateFlow<List<String>> = _categoryOrder.asStateFlow()

    fun saveCategoryOrder(order: List<String>) {
        _categoryOrder.value = order
        prefs.edit().putString("category_order", order.joinToString(",")).apply()
    }

    // ── Historial y favoritos ─────────────────────────────────────────────────
    suspend fun getSuggestions(query: String): List<ProductHistory> =
        if (query.length < 3) repo.getFavorites()
        else repo.searchHistory(query)

    suspend fun getFavorites(): List<ProductHistory> = repo.getFavorites()

    fun toggleFavorite(entry: ProductHistory) = viewModelScope.launch {
        repo.setFavorite(entry.name, !entry.isFavorite)
    }

    // ── Estadísticas ──────────────────────────────────────────────────────────
    suspend fun getProductStats(): List<ProductStats> = repo.getProductStats()
    suspend fun getGlobalStats(): GlobalStats = repo.getGlobalStats()

    fun finalizeShoppingTrip(items: List<Product>) = viewModelScope.launch {
        repo.recordShoppingSession(items)
        items.forEach { repo.delete(it.name) }
        ShoppingListWidget.notifyDataChanged(getApplication())
    }

    // ── Plantillas automáticas ────────────────────────────────────────────────
    suspend fun currentListMatchesTemplate(): Boolean {
        val currentNames = items.value.map { it.name }.toSet()
        if (currentNames.isEmpty()) return false
        return templates.value.any { tpl ->
            val tplNames = repo.loadTemplate(tpl.name).map { it.name }.toSet()
            tplNames == currentNames
        }
    }
}
