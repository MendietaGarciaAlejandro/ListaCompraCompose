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

    val allItems: Flow<List<Product>> = dao.getAll()
    val allTemplates: Flow<List<Template>> = templateDao.getAllTemplates()

    suspend fun insert(prod: Product) {
        withContext(Dispatchers.IO) {
            try {
                // Primero intenta actualizar si existe
                val existing = dao.getByName(prod.name)
                if (existing != null) {
                    // Si existe, suma las cantidades
                    val updated = existing.copy(
                        quantity = existing.quantity + prod.quantity,
                        price = if (prod.price > 0) prod.price else existing.price
                    )
                    dao.update(updated)
                } else {
                    // Si no existe, inserta nuevo
                    dao.insert(prod)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback: insertar normalmente
                dao.insert(prod)
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
        }
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
