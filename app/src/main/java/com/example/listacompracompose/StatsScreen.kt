package com.example.listacompracompose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: CompraViewModel, onBack: () -> Unit) {
    var productStats by remember { mutableStateOf<List<ProductStats>>(emptyList()) }
    var globalStats by remember { mutableStateOf<GlobalStats?>(null) }
    var loading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val items by vm.items.collectAsState()

    LaunchedEffect(Unit) {
        productStats = vm.getProductStats()
        globalStats = vm.getGlobalStats()
        loading = false
    }

    val dateFormat = remember { SimpleDateFormat("dd/MM/yy", Locale("es")) }
    val monthFormat = remember { SimpleDateFormat("MMM yy", Locale("es")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Estadísticas", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val file = PdfUtils.createShoppingListPdf(context, items)
                        PdfUtils.sharePdf(context, file)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Exportar PDF", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (productStats.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sin datos todavía", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
                    Text("Añade productos con precio para ver estadísticas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outlineVariant, textAlign = TextAlign.Center)
                }
            }
            return@Scaffold
        }

        val global = globalStats
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Resumen global ────────────────────────────────────────────────
            if (global != null) {
                item {
                    Text("Resumen global", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                GlobalStatItem("Gasto total", "€${"%.2f".format(global.totalSpent)}")
                                VerticalDivider(modifier = Modifier.height(40.dp))
                                GlobalStatItem("Compras", "${global.totalTrips}")
                                VerticalDivider(modifier = Modifier.height(40.dp))
                                GlobalStatItem("Productos", "${productStats.size}")
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                GlobalStatItem("Promedio/mes", "€${"%.2f".format(global.avgSpentPerMonth)}")
                                VerticalDivider(modifier = Modifier.height(40.dp))
                                GlobalStatItem("Promedio/compra", "€${"%.2f".format(global.avgSpentPerTrip)}")
                                if (global.mostBoughtProduct != null) {
                                    VerticalDivider(modifier = Modifier.height(40.dp))
                                    GlobalStatItem("Más comprado", global.mostBoughtProduct, small = true)
                                }
                            }
                        }
                    }
                }

                // ── Gráfico mensual ───────────────────────────────────────────
                item {
                    Spacer(Modifier.height(4.dp))
                    Text("Gasto mensual", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            val maxSpent = global.monthlyBreakdown.maxOfOrNull { it.totalSpent }?.takeIf { it > 0 } ?: 1.0
                            Row(
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                global.monthlyBreakdown.forEach { month ->
                                    val fraction = (month.totalSpent / maxSpent).toFloat().coerceIn(0f, 1f)
                                    val isCurrentMonth = month.month == Calendar.getInstance().get(Calendar.MONTH) + 1
                                        && month.year == Calendar.getInstance().get(Calendar.YEAR)
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Bottom
                                    ) {
                                        if (month.totalSpent > 0) {
                                            Text(
                                                "€${"%.0f".format(month.totalSpent)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(Modifier.height(2.dp))
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height((fraction * 90).dp.coerceAtLeast(4.dp))
                                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                .background(
                                                    if (isCurrentMonth) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.primaryContainer
                                                )
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                global.monthlyBreakdown.forEach { month ->
                                    val cal = Calendar.getInstance().apply { set(month.year, month.month - 1, 1) }
                                    Text(
                                        monthFormat.format(cal.time).replaceFirstChar { it.uppercase() },
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            // Tabla mensual detallada
                            global.monthlyBreakdown.filter { it.totalSpent > 0 }.reversed().forEach { month ->
                                val cal = Calendar.getInstance().apply { set(month.year, month.month - 1, 1) }
                                val label = monthFormat.format(cal.time).replaceFirstChar { it.uppercase() }
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(56.dp))
                                    Text(
                                        "${month.tripCount} compra${if (month.tripCount > 1) "s" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        "${month.itemCount} items",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        "€${"%.2f".format(month.totalSpent)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Por producto ──────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(4.dp))
                Text("Por producto", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }

            items(productStats) { s ->
                ProductStatCard(s, dateFormat)
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun GlobalStatItem(label: String, value: String, small: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = if (small) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f), textAlign = TextAlign.Center)
    }
}

@Composable
private fun ProductStatCard(s: ProductStats, dateFormat: SimpleDateFormat) {
    val trendColor = when {
        s.trend > 0.005 -> Color(0xFFD32F2F)
        s.trend < -0.005 -> Color(0xFF388E3C)
        else -> MaterialTheme.colorScheme.outline
    }
    val trendText = when {
        s.trend > 0.005 -> "↑ +€${"%.2f".format(s.trend)} vs anterior"
        s.trend < -0.005 -> "↓ −€${"%.2f".format(-s.trend)} vs anterior"
        s.prevPrice != null -> "→ precio estable"
        else -> "Solo un registro"
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Text("${s.purchaseCount}×", color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PriceChip("Último", "€${"%.2f".format(s.lastPrice)}")
                PriceChip("Medio", "€${"%.2f".format(s.avgPrice)}")
                PriceChip("Mín", "€${"%.2f".format(s.minPrice)}")
                PriceChip("Máx", "€${"%.2f".format(s.maxPrice)}")
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(trendText, style = MaterialTheme.typography.labelMedium, color = trendColor)
                Text(
                    "Total €${"%.2f".format(s.totalSpent)} · ${dateFormat.format(Date(s.lastTimestamp))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun PriceChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
