package com.flashpoint.admin.data

import com.flashpoint.admin.core.RequestItem
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

data class ReportItem(val name: String, val qty: Long, val price: Double)

data class ReportOrder(
    val id: String, val sellerUid: String, val deliveryUid: String, val total: Double,
    val status: String, val createdAt: Long, val deliveredAt: Long, val items: List<ReportItem>,
)

data class ReportData(
    val orders: List<ReportOrder>,
    val requests: List<RequestItem>,
    val sellers: Map<String, String>,
    val partners: Map<String, String>,
)

class ReportRepository(
    private val db: FirebaseDatabase =
        FirebaseDatabase.getInstance("https://flashpoint-57a82-default-rtdb.firebaseio.com"),
) {
    private fun DataSnapshot.s(k: String) = child(k).value?.toString().orEmpty()
    private fun DataSnapshot.n(k: String) = (child(k).value as? Number)?.toLong() ?: 0L
    private fun DataSnapshot.d(k: String) = (child(k).value as? Number)?.toDouble() ?: 0.0

    suspend fun load(): ReportData {
        val reqRepo = RequestRepository(db)
        val orders = db.getReference("orders").get().await().children.map { o ->
            ReportOrder(
                o.key.orEmpty(), o.s("sellerUid"), o.s("deliveryUid"), o.d("totalAmount"), o.s("status"),
                o.n("createdAt"), o.n("deliveredAt"),
                o.child("items").children.map { i -> ReportItem(i.s("productName"), i.n("quantity"), i.d("price")) },
            )
        }
        return ReportData(orders, reqRepo.requests().first(), reqRepo.names("sellers"), reqRepo.names("deliveryPartners"))
    }
}
