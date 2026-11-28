package com.flashpoint.admin.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ReportsScreen(vm: ReportsViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var fromText by remember(s.fromText) { mutableStateOf(s.fromText) }
    var toText by remember(s.toText) { mutableStateOf(s.toText) }
    var dateError by remember { mutableStateOf<String?>(null) }
    val data = s.data
    val report = remember(data, s.from, s.to) { data?.let { buildReport(it, s.from, s.to) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ALL" to "All time", "TODAY" to "Today", "7D" to "Last 7 days", "30D" to "Last 30 days").forEach { (k, label) ->
                FilterChip(s.preset == k, { dateError = null; vm.preset(k) }, { Text(label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(fromText, { fromText = it }, Modifier.weight(1f), singleLine = true, label = { Text("From (yyyy-MM-dd)") })
            OutlinedTextField(toText, { toText = it }, Modifier.weight(1f), singleLine = true, label = { Text("To (yyyy-MM-dd)") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ dateError = vm.applyCustom(fromText, toText) }) { Text("Apply dates") }
            OutlinedButton({ vm.load() }, enabled = !s.loading) { Text("Refresh") }
        }
        dateError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        when {
            s.loading -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() }
            s.error != null -> Text(s.error!!, color = MaterialTheme.colorScheme.error)
            report == null -> Unit
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("COD revenue", money(report.cod), Modifier.weight(1f))
                    Stat("Orders", report.orders.toString(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("Delivered", report.delivered.toString(), Modifier.weight(1f))
                    Stat("Cancelled", report.cancelled.toString(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("Returns", report.returnCount.toString(), Modifier.weight(1f))
                    Stat("Exchanges", report.exchangeCount.toString(), Modifier.weight(1f))
                }
                Stat("Refund total", money(report.refundTotal), Modifier.fillMaxWidth())

                Chart("Daily sales", report.daily.takeLast(14))
                Chart("Monthly sales", report.monthly.takeLast(12))
                Table("Daily sales (delivered, COD)", report.daily.reversed().map { ReportRow(it.first, money(it.second)) })
                Table("Monthly sales", report.monthly.reversed().map { ReportRow(it.first, money(it.second)) })
                Table("Top-selling products", report.topProducts)
                Table("Top-performing sellers", report.topSellers)
                Table("Delivery performance", report.delivery)
                Table("Return statistics", report.returnRows)
                Table("Exchange statistics", report.exchangeRows)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Chart(title: String, points: List<Pair<String, Double>>) {
    val color = MaterialTheme.colorScheme.primary
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (points.isEmpty()) { Text("No data in this period.", style = MaterialTheme.typography.bodySmall); return@Column }
            val max = (points.maxOf { it.second }).coerceAtLeast(1.0)
            Canvas(Modifier.fillMaxWidth().height(110.dp).padding(top = 10.dp)) {
                val slot = size.width / points.size
                points.forEachIndexed { i, p ->
                    val h = (size.height * (p.second / max)).toFloat()
                    drawRoundRect(color, Offset(i * slot + slot * 0.15f, size.height - h), Size(slot * 0.7f, h), CornerRadius(6f))
                }
            }
            Text("${points.first().first} → ${points.last().first} • max ${money(max)}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Table(title: String, rows: List<ReportRow>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (rows.isEmpty()) Text("No data in this period.", style = MaterialTheme.typography.bodySmall)
            rows.forEach { r ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(r.label, Modifier.weight(1.4f), style = MaterialTheme.typography.bodyMedium)
                    Text(r.a, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    if (r.b.isNotEmpty()) Text(r.b, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
