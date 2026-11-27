package com.flashpoint.admin.data

import com.flashpoint.admin.core.OrderSummary
import com.flashpoint.admin.core.SellerItem
import com.flashpoint.admin.core.Stats
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
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

    private fun <T> listen(path: String, map: (DataSnapshot) -> T): Flow<T> = callbackFlow {
        val ref = db.getReference(path)
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

    suspend fun setSellerStatus(uid: String, status: String) {
        db.getReference("sellers/$uid").updateChildren(
            mapOf("status" to status, "approved" to (status == "APPROVED"),
                "updatedAt" to ServerValue.TIMESTAMP)
        ).await()
    }
}
