// Repository.kt
package com.example.listacompracompose

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

// Repository.kt
class CompraRepository(private val db: AppDatabase) {
    private val dao = db.productDao()
    private val templateDao = db.templateDao()
    private val historyDao = db.productHistoryDao()
    private val purchaseLogDao = db.purchaseLogDao()

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
            historyDao.upsert(ProductHistory(name = prod.name, price = prod.price, quantity = prod.quantity, category = prod.category))
            if (prod.price > 0) {
                purchaseLogDao.insert(PurchaseLog(name = prod.name, price = prod.price, quantity = prod.quantity))
            }
        }
    }

    suspend fun saveTemplate(name: String, products: List<Product>) = withContext(Dispatchers.IO) {
        templateDao.insertTemplate(Template(name, products))
    }

    suspend fun loadTemplate(name: String): List<Product> = withContext(Dispatchers.IO) {
        templateDao.getTemplate(name)?.products ?: emptyList()
    }

    suspend fun deleteTemplate(name: String) = withContext(Dispatchers.IO) {
        templateDao.deleteTemplate(name)
    }

    suspend fun update(prod: Product) {
        withContext(Dispatchers.IO) {
            dao.update(prod)
            historyDao.upsert(ProductHistory(name = prod.name, price = prod.price, quantity = prod.quantity, category = prod.category))
        }
    }

    suspend fun searchHistory(query: String): List<ProductHistory> =
        withContext(Dispatchers.IO) { historyDao.search(query) }

    suspend fun getFavorites(): List<ProductHistory> =
        withContext(Dispatchers.IO) { historyDao.getFavorites() }

    suspend fun setFavorite(name: String, isFavorite: Boolean) =
        withContext(Dispatchers.IO) { historyDao.setFavorite(name, isFavorite) }

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
        }
    }

    suspend fun toggleCheckedStatus(name: String) {
        withContext(Dispatchers.IO) {
            val product = dao.getByName(name)
            product?.let {
                dao.updateCheckedStatus(name, !it.isChecked)
            }
        }
    }

//    suspend fun clearDatabase() {
//        withContext(Dispatchers.IO) {
//            dao.deleteAllProducts()
//        }
//    }
}
