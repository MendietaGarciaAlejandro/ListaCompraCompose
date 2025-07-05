// CompraApp.kt
package com.example.listacompracompose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.listacompracompose.PdfUtils.sharePdf
import com.example.listacompracompose.PdfUtils.shareText
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompraApp(vm: CompraViewModel) {
    val items by vm.items.collectAsState()
    val groupedItems = items.groupBy { it.category }
    val expandedCategories = remember { mutableStateMapOf<String, Boolean>() }

    // Depuración: Imprime los items en la consola
    LaunchedEffect(items) {
        println("Items actualizados: ${items.size}")
        items.forEach { println("- ${it.name} (${it.quantity})") }
    }

    val templates by vm.templates.collectAsState()
    var tplName by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showLoadDialog by remember { mutableStateOf(false) }


    var showInsert by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) } // Nuevo: Diálogo de edición
    var showDeleteOptions by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var currentProduct by remember { mutableStateOf<Product?>(null) } // Producto actualmente seleccionado
    var categoria by remember { mutableStateOf<String?>(null) }
    var isNewCat by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { tplName = ""; showSaveDialog = true }) { Text("Guardar Plantilla") }
                Button(onClick = { tplName = ""; showLoadDialog = true }) { Text("Cargar Plantilla") }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = {
                    // Exportar PDF
                    val file = PdfUtils.createShoppingListPdf(context, items)
                    sharePdf(context, file)
                }) {
                    Text("Exportar PDF")
                }
                Button(onClick = {
                    // Compartir como texto
                    shareText(context, items)
                }) {
                    Text("Compartir Lista")
                }
            }

            Spacer(Modifier.height(16.dp))
            if (items.isEmpty()) {
                Text("Lista vacía", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    groupedItems.forEach { (category, products) ->
                        // Encabezado de categoría
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedCategories[category] = expandedCategories[category] != true
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 8.dp)
                                )
                                Icon(
                                    imageVector = if (expandedCategories[category] == true)
                                        Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle categoría"
                                )
                            }
                            HorizontalDivider()
                        }

                        // Items dentro de la categoría si está expandida
                        if (expandedCategories[category] == true) {
                            items(products) { prod ->
                                ProductItemRow(
                                    product = prod,
                                    onToggle = { vm.toggleCheckedStatus(prod.name) },
                                    onEdit = {
                                        currentProduct = prod
                                        name = prod.name
                                        price = prod.price.toString()
                                        qty = prod.quantity.toString()
                                        categoria = prod.category
                                        showEdit = true
                                    },
                                    onDelete = {
                                        name = prod.name
                                        categoria = prod.category
                                        showDeleteOptions = true
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
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

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Guardar Plantilla") },
            text = {
                OutlinedTextField(
                    value = tplName,
                    onValueChange = { tplName = it },
                    label = { Text("Nombre plantilla") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveTemplate(tplName)
                    showSaveDialog = false
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancelar") } },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        )
    }

    if (showLoadDialog) {
        AlertDialog(
            onDismissRequest = { showLoadDialog = false },
            title = { Text("Cargar Plantilla") },
            text = {
                LazyColumn {
                    items(templates) { tpl ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = tpl.name,
                                Modifier
                                    .weight(1f)
                                    .clickable {
                                        vm.loadTemplate(tpl.name)
                                        showLoadDialog = false
                                    }
                                    .padding(8.dp)
                            )
                            IconButton(onClick = {
                                vm.deleteTemplate(tpl.name)
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar plantilla")
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showLoadDialog = false }) { Text("Cerrar") } },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        )
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
                    Spacer(Modifier.height(8.dp))
                    // Spinner categorías
                    var expanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = if (isNewCat) categoria.orEmpty() else (categoria ?: "General"),
                            onValueChange = { if (isNewCat) categoria = it },
                            label = { Text("Categoría") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { if (!isNewCat) expanded = true },
                            readOnly = !isNewCat,
                            singleLine = true
                        )
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.fillMaxWidth()) {
                            (vm.getCategories() + "Nueva categoría...").distinct().forEach { catOpt ->
                                DropdownMenuItem(text = { Text(catOpt) }, onClick = {
                                    expanded = false
                                    if (catOpt == "Nueva categoría...") {
                                        isNewCat = true
                                        categoria = ""
                                    } else {
                                        categoria = catOpt
                                        isNewCat = false
                                    }
                                })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = name
                    val p = price.toDoubleOrNull() ?: 0.0
                    val q = qty.toIntOrNull() ?: 1
                    val c = categoria.takeUnless { it.isNullOrBlank() } ?: "General"
                    scope.launch {
                        if (showEdit && currentProduct != null) vm.update(currentProduct!!.copy(name = n, price = p, quantity = q, category = c))
                        else vm.insert(Product(name = n, price = p, quantity = q, category = c))
                    }
                    name = ""; price = ""; qty = ""; categoria = null; isNewCat = false; showInsert = false; showEdit = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        name = ""; price = ""; qty = ""; categoria = ""; showInsert = false
                    }
                ) {
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

@Composable
fun ProductItemRow(
    product: Product,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = product.isChecked,
            onCheckedChange = { onToggle() },
            modifier = Modifier.padding(end = 16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (product.isChecked) TextDecoration.LineThrough else null
            )
            Text(
                text = "€${"%.2f".format(product.price)} x ${product.quantity}",
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (product.isChecked) TextDecoration.LineThrough else null
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Editar")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Eliminar")
        }
    }
}
