// ViewModel.kt
package com.example.listacompracompose

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CompraViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = CompraRepository(AppDatabase.getInstance(application))
    val items = repo.allItems.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val templates = repo.allTemplates.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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

    fun saveTemplate(name: String) = viewModelScope.launch { repo.saveTemplate(name, items.value) }
    fun loadTemplate(name: String) = viewModelScope.launch {
        val list = repo.loadTemplate(name)
        // clear current and re-insert loaded
        items.value.forEach { repo.delete(it.name) }
        list.forEach { repo.insert(it) }
    }

    fun deleteTemplate(name: String) = viewModelScope.launch {
        repo.deleteTemplate(name)
    }

    fun toggleCheckedStatus(name: String) = viewModelScope.launch {
        repo.toggleCheckedStatus(name)
    }

    suspend fun insertWithCheck(prod: Product): Boolean {
        return try {
            repo.insert(prod)
            true
        } catch (e: SQLiteConstraintException) {
            false // Producto ya existe
        }
    }

    fun reduceQuantity(name: String) = viewModelScope.launch {
        repo.reduceQuantity(name)
    }

    fun deleteAll(name: String) = viewModelScope.launch {
        repo.deleteAll(name)
    }

    /**
     * Devuelve la lista de categorías disponibles sin duplicados.
     */
    fun getCategories(): List<String> = items.value.map { it.category }.distinct().sorted()


//    fun clearDatabase() = viewModelScope.launch {
//        repo.clearDatabase()
//    }
}