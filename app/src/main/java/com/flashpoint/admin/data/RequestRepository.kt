package com.flashpoint.admin.data

import com.flashpoint.admin.core.HistoryEntry
import com.flashpoint.admin.core.RequestItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class RequestRepository(
    private val db: FirebaseDatabase =
        FirebaseDatabase.getInstance("https://flashpoint-57a82-default-rtdb.firebaseio.com"),
) {
    private fun DataSnapshot.s(k: String) = child(k).value?.toString().orEmpty()
    private fun DataSnapshot.n(k: String) = (child(k).value as? Number)?.toLong() ?: 0L
    private fun DataSnapshot.d(k: String) = (child(k).value as? Number)?.toDouble() ?: 0.0

    fun requests(): Flow<List<RequestItem>> = callbackFlow {
        val ref = db.getReference("returnExchangeRequests")
        val l = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                trySend(snap.children.map {
                    RequestItem(
                        it.key.orEmpty(), it.s("customerUid"), it.s("sellerUid"), it.s("orderId"),
                        it.s("productId"), it.s("productName"), it.n("quantity"),
                        it.s("requestType").uppercase(), it.s("reason"), it.s("originalVariant"),
                        it.s("requestedVariant"), it.n("deliveredAt"), it.n("createdAt"), it.s("status"),
                        it.s("adminNote"), it.d("refundAmount"), it.s("refundMethod"), it.s("refundReference"),
                        it.n("refundDate"), it.s("replacementProductId"), it.s("replacementVariant"),
                        it.child("statusHistory").children.map { h ->
                            HistoryEntry(h.s("status"), h.s("message"), h.n("updatedAt"), h.s("updatedBy"))
                        }.sortedBy { h -> h.updatedAt },
                    )
                }.sortedByDescending { it.createdAt })
            }
            override fun onCancelled(e: DatabaseError) { close(e.toException()) }
        }
        ref.addValueEventListener(l)
        awaitClose { ref.removeEventListener(l) }
    }

    suspend fun names(node: String): Map<String, String> =
        db.getReference(node).get().await().children.associate {
            it.key.orEmpty() to it.child("name").value?.toString().orEmpty()
        }

    /** One atomic write: new status, extra fields, a history entry and an in-app notice for the customer. */
    suspend fun update(r: RequestItem, status: String, message: String, extra: Map<String, Any>) {
        val base = "returnExchangeRequests/${r.id}"
        val histId = db.getReference("$base/statusHistory").push().key.orEmpty()
        val notifId = db.getReference("notifications/customers/${r.customerUid}").push().key.orEmpty()
        val adminUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val kind = if (r.type == "EXCHANGE") "exchange" else "return"
        val readable = status.replace('_', ' ').lowercase()

        val u = HashMap<String, Any>()
        u["$base/status"] = status
        u["$base/updatedAt"] = ServerValue.TIMESTAMP
        if (message.isNotBlank()) u["$base/adminNote"] = message
        extra.forEach { (k, v) -> u["$base/$k"] = v }
        u["$base/statusHistory/$histId"] = mapOf(
            "status" to status, "message" to message,
            "updatedAt" to ServerValue.TIMESTAMP, "updatedBy" to adminUid,
        )
        u["notifications/customers/${r.customerUid}/$notifId"] = mapOf(
            "title" to "Your $kind request was updated",
            "message" to "${r.productName}: $readable." + if (message.isNotBlank()) " $message" else "",
            "imageUrl" to "", "linkUrl" to "", "buttonText" to "", "read" to false,
            "campaignId" to "", "createdAt" to ServerValue.TIMESTAMP,
        )
        db.reference.updateChildren(u).await()
    }
}
