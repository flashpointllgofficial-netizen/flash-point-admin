package com.flashpoint.admin.data

import com.flashpoint.admin.core.OrderItem
import com.flashpoint.admin.core.OrderSummary
import com.flashpoint.admin.core.PersonItem
import com.flashpoint.admin.core.SellerItem
import com.flashpoint.admin.core.Stats
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AdminRepository(
    private val db: FirebaseDatabase =
        FirebaseDatabase.getInstance("https://flashpoint-57a82-default-rtdb.firebaseio.com"),
) {
    private fun DataSnapshot.long(k: String) = (child(k).value as? Number)?.toLong() ?: 0L
    private fun DataSnapshot.dbl(k: String) = (child(k).value as? Number)?.toDouble() ?: 0.0
    private fun DataSnapshot.str(k: String) = child(k).value?.toString().orEmpty()

    private fun <T> listen(path: String, map: (DataSnapshot) -> T): Flow<T> = listenQuery(db.getReference(path), map)

    private fun <T> listenQuery(ref: Query, map: (DataSnapshot) -> T): Flow<T> = callbackFlow {
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(map(s)) }
            override fun onCancelled(e: DatabaseError) { close(e.toException()) }
        }
        ref.addValueEventListener(l)
        awaitClose { ref.removeEventListener(l) }
    }

    /** /statistics is maintained by Cloud Functions (added in the backend part). */
    fun stats(): Flow<Stats> = listen("statistics") { s ->
        Stats(
            s.long("totalOrders"), s.long("totalCustomers"), s.long("totalSellers"),
            s.long("totalDeliveryPartners"), s.long("pendingSellers"), s.long("pendingDelivery"),
            s.dbl("codRevenue"), s.long("returnRequests"), s.long("exchangeRequests"),
        )
    }

    suspend fun recentOrders(limit: Int = 5): List<OrderSummary> =
        db.getReference("orders").orderByChild("createdAt").limitToLast(limit).get().await()
            .children.map {
                OrderSummary(it.key.orEmpty(), it.str("customerName"), it.dbl("totalAmount"),
                    it.str("status"), it.long("createdAt"))
            }.sortedByDescending { it.createdAt }

    fun sellers(): Flow<List<SellerItem>> = listen("sellers") { s ->
        s.children.map {
            val status = it.str("status").ifBlank {
                if (it.child("approved").value == true) "APPROVED" else "PENDING"
            }
            SellerItem(it.key.orEmpty(), it.str("name"), it.str("email"), it.str("phone"),
                it.str("category"), status)
        }.sortedBy { it.name.lowercase() }
    }

    /** Also maintains the public list of approved sellers used by guest browsing and the database rules. */
    suspend fun setSellerStatus(uid: String, status: String) {
        db.reference.updateChildren(mapOf(
            "sellers/$uid/status" to status,
            "sellers/$uid/approved" to (status == "APPROVED"),
            "sellers/$uid/updatedAt" to ServerValue.TIMESTAMP,
            "appConfig/approvedSellers/$uid" to (if (status == "APPROVED") true else null),
        )).await()
    }

    private fun DataSnapshot.statusOf(): String = str("status").ifBlank {
        if (child("approved").value == true) "APPROVED" else "PENDING"
    }

    fun deliveryPartners(): Flow<List<PersonItem>> = listen("deliveryPartners") { s ->
        s.children.map {
            PersonItem(it.key.orEmpty(), it.str("name"), it.str("email"), it.str("phone"),
                it.statusOf(), "Vehicle: ${it.str("vehicle").ifBlank { "-" }}")
        }.sortedBy { it.name.lowercase() }
    }

    fun customers(): Flow<List<PersonItem>> = listen("customers") { s ->
        s.children.map {
            PersonItem(it.key.orEmpty(), it.str("name"), it.str("email"), it.str("phone"),
                "ACTIVE", "Address: ${it.str("address").ifBlank { "-" }}")
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun setPartnerStatus(uid: String, status: String) {
        db.getReference("deliveryPartners/$uid").updateChildren(
            mapOf("status" to status, "approved" to (status == "APPROVED"),
                "updatedAt" to ServerValue.TIMESTAMP)
        ).await()
    }

    fun orders(limit: Int = 50): Flow<List<OrderItem>> =
        listenQuery(db.getReference("orders").orderByChild("createdAt").limitToLast(limit)) { s ->
        s.children.map {
            OrderItem(it.key.orEmpty(), it.str("customerName"), it.str("customerUid"),
                it.str("sellerUid"), it.str("deliveryUid"), it.dbl("totalAmount"),
                it.str("status"), it.str("address"), it.str("phone"),
                it.str("locationLink"), it.long("createdAt"),
                items = it.child("items").children.map { i ->
                    "${i.str("productName")} × ${i.long("quantity")} — ${"%.2f".format(i.dbl("price"))}"
                },
                deliveredAt = it.long("deliveredAt"), paymentStatus = it.str("paymentStatus"))
        }.sortedByDescending { it.createdAt }
    }

    suspend fun assignDelivery(orderId: String, partner: PersonItem) {
        db.getReference("orders/$orderId").updateChildren(
            mapOf("deliveryUid" to partner.uid, "deliveryName" to partner.name,
                "deliveryPhone" to partner.phone, "updatedAt" to ServerValue.TIMESTAMP)
        ).await()
    }

    suspend fun cancelOrder(orderId: String) {
        db.getReference("orders/$orderId").updateChildren(
            mapOf("status" to "CANCELLED", "updatedAt" to ServerValue.TIMESTAMP)
        ).await()
    }

    suspend fun recentActivities(): List<Pair<String, Long>> {
        val reqs = db.getReference("returnExchangeRequests").orderByChild("createdAt").limitToLast(3).get().await()
            .children.map { "${it.str("requestType").lowercase()} request: ${it.str("productName")} (${it.str("status")})" to it.long("createdAt") }
        val notes = db.getReference("admin/notifications").orderByChild("createdAt").limitToLast(3).get().await()
            .children.map { "Notification sent: ${it.str("title")}" to it.long("createdAt") }
        return (reqs + notes).sortedByDescending { it.second }
    }

    /** Counts computed in the app (no Cloud Functions). Fine for small and medium data sets. */
    suspend fun computeStats(): Stats {
        val orders = db.getReference("orders").get().await()
        val cod = orders.children.filter { it.str("status") == "DELIVERED" }.sumOf { it.dbl("totalAmount") }
        val sellers = db.getReference("sellers").get().await()
        val partners = db.getReference("deliveryPartners").get().await()
        val customers = db.getReference("customers").get().await()
        val reqs = db.getReference("returnExchangeRequests").get().await()
        return Stats(
            totalOrders = orders.childrenCount,
            totalCustomers = customers.childrenCount,
            totalSellers = sellers.childrenCount,
            totalDeliveryPartners = partners.childrenCount,
            pendingSellers = sellers.children.count { it.statusOf() == "PENDING" }.toLong(),
            pendingDelivery = partners.children.count { it.statusOf() == "PENDING" }.toLong(),
            codRevenue = cod,
            returnRequests = reqs.children.count { it.str("requestType").equals("RETURN", true) }.toLong(),
            exchangeRequests = reqs.children.count { it.str("requestType").equals("EXCHANGE", true) }.toLong(),
        )
    }
}
