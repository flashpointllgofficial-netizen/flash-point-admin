package com.flashpoint.admin.ui.reports

import com.flashpoint.admin.data.ReportData
import java.text.SimpleDateFormat
import java.util.Locale

data class ReportRow(val label: String, val a: String, val b: String = "")

data class Report(
    val cod: Double, val orders: Int, val delivered: Int, val cancelled: Int,
    val daily: List<Pair<String, Double>>, val monthly: List<Pair<String, Double>>,
    val topProducts: List<ReportRow>, val topSellers: List<ReportRow>, val delivery: List<ReportRow>,
    val returnCount: Int, val exchangeCount: Int, val returnRows: List<ReportRow>, val exchangeRows: List<ReportRow>,
    val refundTotal: Double,
)

fun money(x: Double): String = String.format(Locale.US, "%.2f", x)

fun buildReport(d: ReportData, from: Long, to: Long): Report {
    val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val monthFmt = SimpleDateFormat("yyyy-MM", Locale.US)
    val inRange = d.orders.filter { it.createdAt in from..to }
    val delivered = inRange.filter { it.status == "DELIVERED" }

    val topProducts = delivered.flatMap { it.items }.groupBy { it.name }
        .map { (name, list) -> Triple(name, list.sumOf { it.qty }, list.sumOf { it.qty * it.price }) }
        .sortedByDescending { it.second }.take(10)
        .map { ReportRow(it.first.ifBlank { "(unnamed)" }, "${it.second} sold", money(it.third)) }

    val topSellers = delivered.groupBy { it.sellerUid }
        .map { (uid, list) -> Triple(d.sellers[uid].orEmpty().ifBlank { uid }, list.size, list.sumOf { it.total }) }
        .sortedByDescending { it.third }.take(10)
        .map { ReportRow(it.first, "${it.second} orders", money(it.third)) }

    val delivery = inRange.filter { it.deliveryUid.isNotBlank() }.groupBy { it.deliveryUid }
        .map { (uid, list) ->
            ReportRow(d.partners[uid].orEmpty().ifBlank { uid },
                "${list.count { it.status == "DELIVERED" }} delivered", "${list.size} assigned")
        }

    val reqs = d.requests.filter { it.createdAt in from..to }
    val returns = reqs.filter { it.type == "RETURN" }
    val exchanges = reqs.filter { it.type == "EXCHANGE" }
    fun rows(l: List<com.flashpoint.admin.core.RequestItem>) =
        l.groupBy { it.status }.map { ReportRow(it.key, it.value.size.toString()) }.sortedBy { it.label }

    return Report(
        cod = delivered.sumOf { it.total },
        orders = inRange.size, delivered = delivered.size, cancelled = inRange.count { it.status == "CANCELLED" },
        daily = delivered.groupBy { dayFmt.format(it.createdAt) }.map { it.key to it.value.sumOf { o -> o.total } }.sortedBy { it.first },
        monthly = delivered.groupBy { monthFmt.format(it.createdAt) }.map { it.key to it.value.sumOf { o -> o.total } }.sortedBy { it.first },
        topProducts = topProducts, topSellers = topSellers, delivery = delivery,
        returnCount = returns.size, exchangeCount = exchanges.size,
        returnRows = rows(returns), exchangeRows = rows(exchanges),
        refundTotal = returns.filter { it.status == "REFUNDED" }.sumOf { it.refundAmount },
    )
}
