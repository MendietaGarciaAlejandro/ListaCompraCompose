// CompraApp.kt
package com.example.listacompracompose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.listacompracompose.PdfUtils.sharePdf
import com.example.listacompracompose.PdfUtils.shareText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CompraApp(vm: CompraViewModel) {
    val items by vm.items.collectAsState()
    val groupedItems = items.groupBy { it.category }
    val expandedCategories = remember { mutableStateMapOf<String, Boolean>() }

    val templates by vm.templates.collectAsState()
    var tplName by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showLoadDialog by remember { mutableStateOf(false) }

    var showInsert by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showDeleteOptions by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var currentProduct by remember { mutableStateOf<Product?>(null) }
    var categoria by remember { mutableStateOf<String?>(null) }
    var isNewCat by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Lista de la Compra",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                actions = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .combinedClickable(
                                onClick = {
                                    scope.launch {
                                        items.filter { it.isChecked }
                                            .forEach { vm.toggleCheckedStatus(it.name) }
                                    }
                                },
                                onLongClick = { showClearAllDialog = true }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Limpiar selección",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    name = ""; price = ""; qty = ""; categoria = null; isNewCat = false
                    showInsert = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Añadir producto") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxSize()
        ) {
            // Botones de acción
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { tplName = ""; showSaveDialog = true },
                    modifier = Modifier.weight(1f)
                ) { Text("Guardar plantilla", maxLines = 1) }
                FilledTonalButton(
                    onClick = { tplName = ""; showLoadDialog = true },
                    modifier = Modifier.weight(1f)
                ) { Text("Cargar plantilla", maxLines = 1) }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val file = PdfUtils.createShoppingListPdf(context, items)
                        sharePdf(context, file)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Exportar PDF", maxLines = 1) }
                OutlinedButton(
                    onClick = { shareText(context, items) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Compartir", maxLines = 1)
                }
            }

            Spacer(Modifier.height(12.dp))

            if (items.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Tu lista está vacía",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            "Pulsa + para añadir productos",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    groupedItems.forEach { (category, products) ->
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        expandedCategories[category] =
                                            expandedCategories[category] != true
                                    },
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(10.dp),
                                tonalElevation = 2.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = category,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${products.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = if (expandedCategories[category] == true)
                                            Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Toggle categoría",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

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
                                        isNewCat = false
                                        showEdit = true
                                    },
                                    onDelete = {
                                        name = prod.name
                                        categoria = prod.category
                                        showDeleteOptions = true
                                    }
                                )
                            }
                            item { Spacer(Modifier.height(4.dp)) }
                        }
                    }
                }

                // Resumen total
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Total",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "€${"%.2f".format(items.sumOf { it.price * it.quantity })}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val selectedCount = items.count { it.isChecked }
                        if (selectedCount > 0) {
                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                Text(
                                    "$selectedCount en carrito",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Diálogos ──────────────────────────────────────────────────────────────

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Guardar plantilla") },
            text = {
                OutlinedTextField(
                    value = tplName,
                    onValueChange = { tplName = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { vm.saveTemplate(tplName); showSaveDialog = false }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancelar") }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        )
    }

    if (showLoadDialog) {
        AlertDialog(
            onDismissRequest = { showLoadDialog = false },
            title = { Text("Cargar plantilla") },
            text = {
                LazyColumn {
                    items(templates) { tpl ->
                        ListItem(
                            headlineContent = { Text(tpl.name) },
                            trailingContent = {
                                IconButton(onClick = { vm.deleteTemplate(tpl.name) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                                }
                            },
                            modifier = Modifier.clickable {
                                vm.loadTemplate(tpl.name)
                                showLoadDialog = false
                            }
                        )
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLoadDialog = false }) { Text("Cerrar") }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        )
    }

    if (showInsert) {
        ProductFormDialog(
            vm = vm,
            title = "Añadir producto",
            name = name,
            price = price,
            qty = qty,
            categoria = categoria,
            isNewCat = isNewCat,
            categories = vm.getCategories(),
            onNameChange = { name = it },
            onPriceChange = { price = it },
            onQtyChange = { qty = it },
            onCategoriaChange = { categoria = it },
            onIsNewCatChange = { isNewCat = it },
            onConfirm = {
                val n = name; val p = price.toDoubleOrNull() ?: 0.0
                val q = qty.toIntOrNull() ?: 1
                val c = categoria.takeUnless { it.isNullOrBlank() } ?: "General"
                scope.launch { vm.insert(Product(name = n, price = p, quantity = q, category = c)) }
                name = ""; price = ""; qty = ""; categoria = null; isNewCat = false; showInsert = false
            },
            onDismiss = { name = ""; price = ""; qty = ""; categoria = null; isNewCat = false; showInsert = false },
            confirmLabel = "Añadir"
        )
    }

    if (showEdit) {
        ProductFormDialog(
            vm = vm,
            title = "Editar producto",
            name = name,
            price = price,
            qty = qty,
            categoria = categoria,
            isNewCat = isNewCat,
            categories = vm.getCategories(),
            onNameChange = { name = it },
            onPriceChange = { price = it },
            onQtyChange = { qty = it },
            onCategoriaChange = { categoria = it },
            onIsNewCatChange = { isNewCat = it },
            onConfirm = {
                val original = currentProduct
                val n = name; val p = price.toDoubleOrNull() ?: 0.0
                val q = qty.toIntOrNull() ?: 1
                val c = categoria.takeUnless { it.isNullOrBlank() } ?: "General"
                scope.launch { original?.let { vm.update(it.copy(name = n, price = p, quantity = q, category = c)) } }
                currentProduct = null; name = ""; price = ""; qty = ""; categoria = null; isNewCat = false; showEdit = false
            },
            onDismiss = { currentProduct = null; name = ""; price = ""; qty = ""; categoria = null; isNewCat = false; showEdit = false },
            confirmLabel = "Guardar"
        )
    }

    if (showDeleteOptions) {
        val product = items.firstOrNull { it.name == name }
        AlertDialog(
            onDismissRequest = { showDeleteOptions = false },
            title = { Text("Eliminar ${product?.name}") },
            text = { product?.let { Text("Tienes ${it.quantity} unidades en la lista.") } },
            confirmButton = {
                Button(
                    onClick = { val n = name; scope.launch { vm.deleteAll(n); showDeleteOptions = false } },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar todas (${product?.quantity ?: 0})") }
            },
            dismissButton = {
                TextButton(onClick = { val n = name; scope.launch { vm.reduceQuantity(n); showDeleteOptions = false } }) {
                    Text("Eliminar una")
                }
            }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Limpiar lista") },
            text = { Text("¿Quieres eliminar todos los productos de la lista? Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = { scope.launch { items.forEach { vm.deleteAll(it.name) } }; showClearAllDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar todo") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

// ── Formulario reutilizable Añadir/Editar ──────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductFormDialog(
    title: String,
    name: String,
    price: String,
    qty: String,
    categoria: String?,
    isNewCat: Boolean,
    categories: List<String>,
    vm: CompraViewModel,
    onNameChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onQtyChange: (String) -> Unit,
    onCategoriaChange: (String?) -> Unit,
    onIsNewCatChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String
) {
    var suggestions by remember { mutableStateOf<List<ProductHistory>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Campo nombre con sugerencias (ExposedDropdown mantiene el teclado abierto)
                ExposedDropdownMenuBox(
                    expanded = showSuggestions,
                    onExpandedChange = { },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { value ->
                            onNameChange(value)
                            if (value.length >= 3) {
                                scope.launch {
                                    suggestions = vm.searchHistory(value)
                                    showSuggestions = suggestions.isNotEmpty()
                                }
                            } else {
                                suggestions = emptyList()
                                showSuggestions = false
                            }
                        },
                        label = { Text("Nombre *") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = showSuggestions,
                        onDismissRequest = { showSuggestions = false }
                    ) {
                        suggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(suggestion.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Text(
                                            "€${"%.2f".format(suggestion.price)} · x${suggestion.quantity} · ${suggestion.category}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                },
                                onClick = {
                                    onNameChange(suggestion.name)
                                    onPriceChange(if (suggestion.price > 0) suggestion.price.toString() else "")
                                    onQtyChange(suggestion.quantity.toString())
                                    onCategoriaChange(suggestion.category)
                                    onIsNewCatChange(false)
                                    showSuggestions = false
                                }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = price,
                        onValueChange = onPriceChange,
                        label = { Text("Precio €") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = qty,
                        onValueChange = onQtyChange,
                        label = { Text("Cantidad") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
                // Dropdown categoría
                var expanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = if (isNewCat) categoria.orEmpty() else (categoria ?: "General"),
                        onValueChange = { if (isNewCat) onCategoriaChange(it) },
                        label = { Text("Categoría") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = !isNewCat,
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        trailingIcon = {
                            if (!isNewCat) {
                                IconButton(onClick = { expanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Seleccionar")
                                }
                            }
                        }
                    )
                    if (!isNewCat) {
                        Box(modifier = Modifier.matchParentSize().clickable { expanded = true })
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        (listOf("General") + categories + listOf("+ Nueva categoría...")).distinct()
                            .forEach { catOpt ->
                                DropdownMenuItem(text = { Text(catOpt) }, onClick = {
                                    expanded = false
                                    if (catOpt == "+ Nueva categoría...") {
                                        onIsNewCatChange(true)
                                        onCategoriaChange("")
                                    } else {
                                        onCategoriaChange(catOpt)
                                        onIsNewCatChange(false)
                                    }
                                })
                            }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    )
}

// ── Fila de producto ───────────────────────────────────────────────────────

@Composable
fun ProductItemRow(
    product: Product,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 3.dp),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = product.isChecked,
                onCheckedChange = { onToggle() }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (product.isChecked) TextDecoration.LineThrough else null,
                    color = if (product.isChecked) MaterialTheme.colorScheme.outline
                            else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "€${"%.2f".format(product.price)} · x${product.quantity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    textDecoration = if (product.isChecked) TextDecoration.LineThrough else null
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Editar",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
