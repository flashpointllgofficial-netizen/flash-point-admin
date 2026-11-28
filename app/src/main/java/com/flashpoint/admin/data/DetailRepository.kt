package com.flashpoint.admin.data

import com.flashpoint.admin.core.OrderItem
import com.flashpoint.admin.core.RequestItem
import com.flashpoint.admin.ui.details.DetailKind
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.text.DateFormat
import java.util.Date

data class ProductRow(val id: String, val name: String, val price: Double, val stock: Long, val available: Boolean)

data class Detail(
    val profile: List<Pair<String, String>>,
    val products: List<ProductRow>,
    val orders: List<OrderItem>,
    val requests: List<RequestItem>,
)

class DetailRepository(
    private val db: FirebaseDatabase =
        FirebaseDatabase.getInstance("https://flashpoint-57a82-default-rtdb.firebaseio.com"),
) {
    private fun DataSnapshot.s(k: String) = child(k).value?.toString().orEmpty()
    private fun DataSnapshot.n(k: String) = (child(k).value as? Number)?.toLong() ?: 0L
    private fun DataSnapshot.d(k: String) = (child(k).value as? Number)?.toDouble() ?: 0.0

    suspend fun load(kind: DetailKind, uid: String): Detail {
        val node = when (kind) {
            DetailKind.SELLER -> "sellers"; DetailKind.CUSTOMER -> "customers"; DetailKind.DELIVERY -> "deliveryPartners"
        }
        val field = when (kind) {
            DetailKind.SELLER -> "sellerUid"; DetailKind.CUSTOMER -> "customerUid"; DetailKind.DELIVERY -> "deliveryUid"
        }
        val profile = db.getReference("$node/$uid").get().await().children
            .filter { !it.hasChildren() && it.value != null }
            .map {
                val k = it.key.orEmpty(); val v = it.value
                k to (if (k.endsWith("At") && v is Number) DateFormat.getDateTimeInstance().format(Date(v.toLong())) else v.toString())
            }
        val orders = db.getReference("orders").orderByChild(field).equalTo(uid).get().await().children.map {
            OrderItem(it.key.orEmpty(), it.s("customerName"), it.s("customerUid"), it.s("sellerUid"),
                it.s("deliveryUid"), it.d("totalAmount"), it.s("status"), it.s("address"), it.s("phone"),
                it.s("locationLink"), it.n("createdAt"), deliveredAt = it.n("deliveredAt"))
        }.sortedByDescending { it.createdAt }
        val products = if (kind == DetailKind.SELLER)
            db.getReference("products/$uid").get().await().children.map {
                ProductRow(it.key.orEmpty(), it.s("name"), it.d("price"), it.n("stock"), it.child("available").value == true)
            } else emptyList()
        val requests = if (kind == DetailKind.CUSTOMER)
            RequestRepository(db).requests().first().filter { it.customerUid == uid } else emptyList()
        return Detail(profile, products, orders, requests)
    }
}
