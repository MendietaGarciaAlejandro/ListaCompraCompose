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

    private fun notifyWidget() {
        context?.let { ShoppingListWidget.notifyDataChanged(it) }
    }

    val allItems: Flow<List<Product>> = dao.getAll()
    val allTemplates: Flow<List<Template>> = templateDao.getAllTemplates()

    suspend fun insert(prod: Product) {
        withContext(Dispatchers.IO) {
            try {
                val existing = dao.getByName(prod.name)
                if (existing != null) {
                    val updated = existing.copy(
                        quantity = existing.quantity + prod.quantity,
                        price = if (prod.price > 0) prod.price else existing.price
                    )
                    dao.update(updated)
                } else {
                    dao.insert(prod)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                dao.insert(prod)
            }
            upsertHistory(ProductHistory(name = prod.name, price = prod.price, quantity = prod.quantity, category = prod.category))
            if (prod.price > 0) {
                purchaseLogDao.insert(PurchaseLog(name = prod.name, price = prod.price, quantity = prod.quantity))
            }
            notifyWidget()
        }
    }

    suspend fun saveTemplate(name: String, products: List<Product>) = withContext(Dispatchers.IO) {
        templateDao.insertTemplate(Template(name, products))
    }

    suspend fun loadTemplate(name: String): List<Product> = withContext(Dispatchers.IO) {
        templateDao.getTemplate(name)?.products?.map { it.copy(isChecked = false) } ?: emptyList()
    }

    suspend fun deleteTemplate(name: String) = withContext(Dispatchers.IO) {
        templateDao.deleteTemplate(name)
    }

    suspend fun update(prod: Product) {
        withContext(Dispatchers.IO) {
            dao.update(prod)
            upsertHistory(ProductHistory(name = prod.name, price = prod.price, quantity = prod.quantity, category = prod.category))
        }
    }

    private suspend fun upsertHistory(entry: ProductHistory) {
        val existing = historyDao.getByName(entry.name)
        historyDao.upsert(entry.copy(isFavorite = existing?.isFavorite ?: false))
    }

    suspend fun searchHistory(query: String): List<ProductHistory> =
        withContext(Dispatchers.IO) { historyDao.search(query) }

    suspend fun getFavorites(): List<ProductHistory> =
        withContext(Dispatchers.IO) { historyDao.getFavorites() }

    suspend fun setFavorite(name: String, isFavorite: Boolean) =
        withContext(Dispatchers.IO) { historyDao.setFavorite(name, isFavorite) }

    suspend fun recordShoppingSession(items: List<Product>) = withContext(Dispatchers.IO) {
        val itemCount = items.sumOf { it.quantity }
        val totalSpent = items.sumOf { it.price * it.quantity }
        sessionDao.insert(ShoppingSession(itemCount = itemCount, totalSpent = totalSpent))
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

    suspend fun delete(name: String) {
        withContext(Dispatchers.IO) {
            try {
                val rows = dao.deleteByName(name)
                println("DELETE operation affected $rows rows for product: $name")
            } catch (e: Exception) {
                e.printStackTrace()
            }
            notifyWidget()
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
                        val updated = product.copy(quantity = product.quantity - 1)
                        dao.update(updated)
                        println("Cantidad reducida a: ${updated.quantity}")
                    } else {
                        dao.deleteByName(name)
                        println("Producto eliminado")
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
            product?.let { dao.update(it.copy(quantity = it.quantity + 1)) }
        }
    }

    suspend fun deleteAll(name: String) {
        withContext(Dispatchers.IO) {
            dao.deleteByName(name)
            notifyWidget()
        }
    }

    suspend fun toggleCheckedStatus(name: String) {
        withContext(Dispatchers.IO) {
            val product = dao.getByName(name)
            product?.let {
                dao.updateCheckedStatus(name, !it.isChecked)
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
