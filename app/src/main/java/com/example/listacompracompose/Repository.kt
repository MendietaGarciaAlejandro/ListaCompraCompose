// Repository.kt
package com.example.listacompracompose

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

// Repository.kt
class CompraRepository(private val db: AppDatabase, private val context: Context? = null) {
    private val dao = db.productDao()
    private val templateDao = db.templateDao()
    private val historyDao = db.productHistoryDao()
    private val purchaseLogDao = db.purchaseLogDao()
    private val sessionDao = db.shoppingSessionDao()
    private val sync = SyncManager(db)

    private fun notifyWidget() {
        context?.let { ShoppingListWidget.notifyDataChanged(it) }
    }

    suspend fun syncNow() = sync.reconcile()
    suspend fun pullRemoteChanges() = sync.pullChanges()
    suspend fun fetchInventory() = sync.fetchInventory()

    val allItems: Flow<List<Product>> = dao.getAll()
    val allTemplates: Flow<List<Template>> = templateDao.getAllTemplates()

    suspend fun insert(prod: Product) {
        withContext(Dispatchers.IO) {
            var finalProduct = prod
            try {
                val existing = dao.getByName(prod.name)
                if (existing != null) {
                    val updated = existing.copy(
                        quantity = existing.quantity + prod.quantity,
                        price = if (prod.price > 0) prod.price else existing.price,
                        updatedAt = System.currentTimeMillis()
                    )
                    dao.update(updated)
                    finalProduct = updated
                } else {
                    finalProduct = prod.copy(updatedAt = System.currentTimeMillis())
                    sync.markProductUploadPending(prod.name)
                    dao.insert(finalProduct)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                finalProduct = prod.copy(updatedAt = System.currentTimeMillis())
                sync.markProductUploadPending(prod.name)
                dao.insert(finalProduct)
            }
            upsertHistory(ProductHistory(name = prod.name, price = prod.price, quantity = prod.quantity, category = prod.category))
            if (prod.price > 0) {
                val logId = purchaseLogDao.insert(PurchaseLog(name = prod.name, price = prod.price, quantity = prod.quantity))
                sync.pushPurchaseLog(PurchaseLog(id = logId, name = prod.name, price = prod.price, quantity = prod.quantity))
            }
            notifyWidget()
            sync.pushProduct(finalProduct)
        }
    }

    suspend fun saveTemplate(name: String, products: List<Product>) = withContext(Dispatchers.IO) {
        val template = Template(name, products, updatedAt = System.currentTimeMillis())
        templateDao.insertTemplate(template)
        sync.pushTemplate(template)
    }

    suspend fun loadTemplate(name: String): List<Product> = withContext(Dispatchers.IO) {
        templateDao.getTemplate(name)?.products?.map { it.copy(isChecked = false) } ?: emptyList()
    }

    suspend fun deleteTemplate(name: String) = withContext(Dispatchers.IO) {
        sync.markTemplateDeletePending(name)
        templateDao.deleteTemplate(name)
        sync.pushTemplateDelete(name)
    }

    suspend fun update(prod: Product) {
        withContext(Dispatchers.IO) {
            val updated = prod.copy(updatedAt = System.currentTimeMillis())
            dao.update(updated)
            upsertHistory(ProductHistory(name = prod.name, price = prod.price, quantity = prod.quantity, category = prod.category))
            sync.pushProduct(updated)
        }
    }

    private suspend fun upsertHistory(entry: ProductHistory) {
        val existing = historyDao.getByName(entry.name)
        val merged = entry.copy(isFavorite = existing?.isFavorite ?: false, updatedAt = System.currentTimeMillis())
        historyDao.upsert(merged)
        sync.pushProductHistory(merged)
    }

    suspend fun searchHistory(query: String): List<ProductHistory> =
        withContext(Dispatchers.IO) { historyDao.search(query) }

    suspend fun getFavorites(): List<ProductHistory> =
        withContext(Dispatchers.IO) { historyDao.getFavorites() }

    suspend fun setFavorite(name: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        val updatedAt = System.currentTimeMillis()
        historyDao.setFavorite(name, isFavorite, updatedAt)
        historyDao.getByName(name)?.let { sync.pushProductHistory(it) }
    }

    suspend fun recordShoppingSession(items: List<Product>) = withContext(Dispatchers.IO) {
        val itemCount = items.sumOf { it.quantity }
        val totalSpent = items.sumOf { it.price * it.quantity }
        val session = ShoppingSession(itemCount = itemCount, totalSpent = totalSpent)
        val id = sessionDao.insert(session)
        sync.pushShoppingSession(session.copy(id = id))
    }

    suspend fun getGlobalStats(): GlobalStats = withContext(Dispatchers.IO) {
        val logs = purchaseLogDao.getAllLogs()
        val sessions = sessionDao.getAll()
        if (logs.isEmpty() && sessions.isEmpty()) return@withContext GlobalStats(0.0, 0, 0.0, 0.0, null, emptyList())

        val cal = java.util.Calendar.getInstance()
        val totalTrips = sessions.size
        val totalSpent = sessions.sumOf { it.totalSpent }.takeIf { it > 0 }
            ?: logs.sumOf { it.price * it.quantity }

        // Monthly breakdown using sessions for trip count, logs for spend
        val sessionsByMonth = sessions.groupBy { s ->
            cal.timeInMillis = s.timestamp
            Pair(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1)
        }
        val logsByMonth = logs.groupBy { log ->
            cal.timeInMillis = log.timestamp
            Pair(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1)
        }

        val monthlyBreakdown = (0..5).map { offset ->
            val c = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, -offset) }
            val y = c.get(java.util.Calendar.YEAR)
            val m = c.get(java.util.Calendar.MONTH) + 1
            val key = Pair(y, m)
            val monthSessions = sessionsByMonth[key] ?: emptyList()
            val monthLogs = logsByMonth[key] ?: emptyList()
            // Use session totalSpent if available, else sum logs
            val monthSpent = if (monthSessions.isNotEmpty()) monthSessions.sumOf { it.totalSpent }
                             else monthLogs.sumOf { it.price * it.quantity }
            MonthlyStats(
                year = y, month = m,
                totalSpent = monthSpent,
                tripCount = monthSessions.size,
                itemCount = monthSessions.sumOf { it.itemCount }.takeIf { it > 0 }
                            ?: monthLogs.sumOf { it.quantity }
            )
        }.reversed()

        val activeMonths = monthlyBreakdown.count { it.totalSpent > 0 }.coerceAtLeast(1)
        val avgPerMonth = monthlyBreakdown.sumOf { it.totalSpent } / activeMonths
        val avgPerTrip = if (totalTrips > 0) totalSpent / totalTrips else 0.0

        val mostBought = logs.groupBy { it.name }
            .mapValues { (_, v) -> v.sumOf { it.quantity } }
            .maxByOrNull { it.value }?.key

        GlobalStats(
            totalSpent = totalSpent,
            totalTrips = totalTrips,
            avgSpentPerMonth = avgPerMonth,
            avgSpentPerTrip = avgPerTrip,
            mostBoughtProduct = mostBought,
            monthlyBreakdown = monthlyBreakdown
        )
    }

    suspend fun getProductStats(): List<ProductStats> = withContext(Dispatchers.IO) {
        val logs = purchaseLogDao.getAllLogs()
        logs.groupBy { it.name }.map { (name, entries) ->
            val sorted = entries.sortedByDescending { it.timestamp }
            val prices = entries.map { it.price }
            ProductStats(
                name = name,
                purchaseCount = entries.size,
                avgPrice = prices.average(),
                minPrice = prices.min(),
                maxPrice = prices.max(),
                lastPrice = sorted.first().price,
                prevPrice = sorted.getOrNull(1)?.price,
                totalSpent = entries.sumOf { it.price * it.quantity },
                lastTimestamp = sorted.first().timestamp
            )
        }.sortedByDescending { it.purchaseCount }
    }

    // bought = true solo al finalizar la compra: es lo único que debe sumar al inventario de casa
    suspend fun delete(name: String, bought: Boolean = false) {
        withContext(Dispatchers.IO) {
            sync.markProductDeletePending(name, bought)
            try {
                val rows = dao.deleteByName(name)
                println("DELETE operation affected $rows rows for product: $name")
            } catch (e: Exception) {
                e.printStackTrace()
            }
            notifyWidget()
            sync.pushProductDelete(name, bought)
        }
    }

    suspend fun reduceQuantity(name: String) {
        withContext(Dispatchers.IO) {
            try {
                println("Intentando reducir cantidad de: $name")
                val product = dao.getByName(name)
                if (product == null) {
                    println("Producto no encontrado: $name")
                } else {
                    println("Producto encontrado: ${product.name}, cantidad: ${product.quantity}")
                    if (product.quantity > 1) {
                        val updated = product.copy(quantity = product.quantity - 1, updatedAt = System.currentTimeMillis())
                        dao.update(updated)
                        println("Cantidad reducida a: ${updated.quantity}")
                        sync.pushProduct(updated)
                    } else {
                        sync.markProductDeletePending(name)
                        dao.deleteByName(name)
                        println("Producto eliminado")
                        sync.pushProductDelete(name)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun incrementQuantity(name: String) {
        withContext(Dispatchers.IO) {
            val product = dao.getByName(name)
            product?.let {
                val updated = it.copy(quantity = it.quantity + 1, updatedAt = System.currentTimeMillis())
                dao.update(updated)
                sync.pushProduct(updated)
            }
        }
    }

    suspend fun deleteAll(name: String) {
        withContext(Dispatchers.IO) {
            sync.markProductDeletePending(name)
            dao.deleteByName(name)
            notifyWidget()
            sync.pushProductDelete(name)
        }
    }

    suspend fun toggleCheckedStatus(name: String) {
        withContext(Dispatchers.IO) {
            val product = dao.getByName(name)
            product?.let {
                val updatedAt = System.currentTimeMillis()
                dao.updateCheckedStatus(name, !it.isChecked, updatedAt)
                sync.pushProduct(it.copy(isChecked = !it.isChecked, updatedAt = updatedAt))
            }
            notifyWidget()
        }
    }

//    suspend fun clearDatabase() {
//        withContext(Dispatchers.IO) {
//            dao.deleteAllProducts()
//        }
//    }
}
