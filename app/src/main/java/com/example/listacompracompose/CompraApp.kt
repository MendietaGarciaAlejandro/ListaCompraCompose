// CompraApp.kt
package com.example.listacompracompose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompraApp(vm: CompraViewModel) {
    val items by vm.items.collectAsState()

    // Depuración: Imprime los items en la consola
    LaunchedEffect(items) {
        println("Items actualizados: ${items.size}")
        items.forEach { println("- ${it.name} (${it.quantity})") }
    }

    var showInsert by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) } // Nuevo: Diálogo de edición
    var showDeleteOptions by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var currentProduct by remember { mutableStateOf<Product?>(null) } // Producto actualmente seleccionado
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lista de Compras") },
                actions = {
                    // Botón para limpiar selecciones
                    IconButton(onClick = {
                        scope.launch {
                            items.filter { it.isChecked }
                                .forEach { vm.toggleCheckedStatus(it.name) }
                        }
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpiar selección")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                name = ""
                price = ""
                qty = ""
                showInsert = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Añadir producto")
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            if (items.isEmpty()) {
                Text("Lista vacía", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(items) { prod ->
                        // CORRECCIÓN: Usamos un diseño personalizado en lugar de ListItem
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = prod.isChecked,
                                onCheckedChange = { vm.toggleCheckedStatus(prod.name) },
                                modifier = Modifier.padding(end = 16.dp)
                            )

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = prod.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    textDecoration = if (prod.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                    color = if (prod.isChecked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.onSurface
                                )

                                Text(
                                    text = "€${"%.2f".format(prod.price)} x ${prod.quantity}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textDecoration = if (prod.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                    color = if (prod.isChecked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row {
                                IconButton(
                                    onClick = {
                                        currentProduct = prod
                                        name = prod.name
                                        price = prod.price.toString()
                                        qty = prod.quantity.toString()
                                        showEdit = true
                                    }
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar")
                                }

                                IconButton(
                                    onClick = {
                                        name = prod.name
                                        showDeleteOptions = true
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                val total = items.sumOf { it.price * it.quantity }
                Text("Total: €${"%.2f".format(total)}", style = MaterialTheme.typography.titleMedium)

                // Mostrar total de seleccionados
                val selectedCount = items.count { it.isChecked }
                if (selectedCount > 0) {
                    Text(
                        "$selectedCount producto(s) en el carrito",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    // Diálogo para añadir nuevo producto
    if (showInsert) {
        AlertDialog(
            onDismissRequest = { showInsert = false },
            title = { Text("Agregar Producto") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Precio (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { qty = it },
                        label = { Text("Cantidad") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val n = name               // capturamos el nombre
                        val p = price.toDoubleOrNull() ?: 0.0
                        val q = qty.toIntOrNull() ?: 1
                        scope.launch {
                            vm.insert(Product(name = n, price = p, quantity = q))
                        }
                        // ahora limpiamos los estados
                        name = ""
                        price = ""
                        qty = ""
                        showInsert = false
                    }
                ) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { name = ""; price = ""; qty = ""; showInsert = false }) {
                    Text("Cancelar")
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        )
    }

    // Diálogo para editar producto existente
    if (showEdit) {
        AlertDialog(
            onDismissRequest = { showEdit = false },
            title = { Text("Editar Producto") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Precio") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { qty = it },
                        label = { Text("Cantidad") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        // Capturamos en locales antes de limpiar
                        val original = currentProduct
                        val n = name
                        val p = price.toDoubleOrNull() ?: 0.0
                        val q = qty.toIntOrNull() ?: 1

                        scope.launch {
                            original?.let {
                                val updated = it.copy(name = n, price = p, quantity = q)
                                vm.update(updated)
                            }
                        }

                        // Ahora sí limpiamos los estados
                        currentProduct = null
                        name = ""
                        price = ""
                        qty = ""
                        showEdit = false
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    currentProduct = null
                    name = ""
                    price = ""
                    qty = ""
                    showEdit = false
                }) {
                    Text("Cancelar")
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        )
    }

    // Diálogo de opciones de eliminación
    if (showDeleteOptions) {
        val product = items.firstOrNull { it.name == name }
        AlertDialog(
            onDismissRequest = { showDeleteOptions = false },
            title = { Text("Eliminar ${product?.name}") },
            text = {
                product?.let {
                    Text("Actualmente tienes ${it.quantity} unidades")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val n = name
                        scope.launch {
                            vm.deleteAll(n)
                            showDeleteOptions = false
                        }
                    }
                ) {
                    Text("Eliminar todos (${product?.quantity ?: 0})")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val n = name
                        scope.launch {
                            vm.reduceQuantity(n)
                            showDeleteOptions = false
                        }
                    }
                ) {
                    Text("Eliminar solo uno")
                }
            }
        )
    }
}
