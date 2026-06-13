package com.example.listacompracompose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: CompraViewModel, onBack: () -> Unit) {
    var stats by remember { mutableStateOf<List<ProductStats>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        stats = vm.getProductStats()
        loading = false
    }

    val totalSpent = stats.sumOf { it.totalSpent }
    val totalPurchases = stats.sumOf { it.purchaseCount }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yy", Locale("es")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Estadísticas",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = MaterialTheme.colorScheme.onPrimary)
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

        if (stats.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Sin datos todavía", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(8.dp))
                    Text("Añade productos con precio para ver estadísticas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Tarjeta resumen global
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatSummaryItem("Productos distintos", "${stats.size}")
                        VerticalDivider(modifier = Modifier.height(48.dp))
                        StatSummaryItem("Compras registradas", "$totalPurchases")
                        VerticalDivider(modifier = Modifier.height(48.dp))
                        StatSummaryItem("Gasto total", "€${"%.2f".format(totalSpent)}")
                    }
                }
            }

            item {
                Text(
                    "Por producto",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(stats) { s ->
                ProductStatCard(s, dateFormat)
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun StatSummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
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
                    Text(
                        "${s.purchaseCount}×",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
