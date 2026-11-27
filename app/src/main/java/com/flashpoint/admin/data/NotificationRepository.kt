package com.flashpoint.admin.data

import com.flashpoint.admin.core.SentNotification
import com.flashpoint.admin.core.ValidationException
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class NotificationRepository(
    private val db: FirebaseDatabase =
        FirebaseDatabase.getInstance("https://flashpoint-57a82-default-rtdb.firebaseio.com"),
) {
    private fun DataSnapshot.s(k: String) = child(k).value?.toString().orEmpty()
    private fun DataSnapshot.n(k: String) = (child(k).value as? Number)?.toLong() ?: 0L

    /**
     * audience: SPECIFIC, CUSTOMERS, SELLERS, DELIVERY, EVERYONE.
     * role (SPECIFIC only): customers | sellers | deliveryPartners.
     * Writes persistent in-app records to /notifications/{role}/{uid}/{campaignId} and the
     * campaign to /admin/notifications/{campaignId} (which the push Cloud Function listens to).
     */
    suspend fun send(
        title: String, message: String, imageUrl: String, linkUrl: String, buttonText: String,
        audience: String, role: String, uid: String, adminUid: String,
    ): Int {
        val targets = mutableListOf<Pair<String, String>>() // node to uid
        if (audience == "SPECIFIC") {
            if (!db.getReference("$role/$uid").get().await().exists())
                throw ValidationException("No user found with that UID.")
            targets += role to uid
        } else {
            val nodes = when (audience) {
                "CUSTOMERS" -> listOf("customers")
                "SELLERS" -> listOf("sellers")
                "DELIVERY" -> listOf("deliveryPartners")
                else -> listOf("customers", "sellers", "deliveryPartners")
            }
            for (node in nodes) {
                db.getReference(node).get().await().children.forEach { targets += node to it.key.orEmpty() }
            }
        }
        if (targets.isEmpty()) throw ValidationException("No recipients found for this audience.")

        val id = db.getReference("admin/notifications").push().key.orEmpty()
        fun record(): Map<String, Any> = mapOf(
            "title" to title, "message" to message, "imageUrl" to imageUrl, "linkUrl" to linkUrl,
            "buttonText" to buttonText, "read" to false, "campaignId" to id,
            "createdAt" to ServerValue.TIMESTAMP,
        )
        targets.chunked(400).forEach { chunk ->
            db.reference.updateChildren(
                chunk.associate { (node, u) -> "notifications/$node/$u/$id" to record() }
            ).await()
        }
        db.getReference("admin/notifications/$id").setValue(mapOf(
            "title" to title, "message" to message, "imageUrl" to imageUrl, "linkUrl" to linkUrl,
            "buttonText" to buttonText, "audience" to audience, "targetRole" to role, "targetUid" to uid,
            "recipients" to targets.size, "sentBy" to adminUid, "createdAt" to ServerValue.TIMESTAMP,
        )).await()
        return targets.size
    }

    fun history(): Flow<List<SentNotification>> = callbackFlow {
        val q = db.getReference("admin/notifications").orderByChild("createdAt").limitToLast(50)
        val l = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                trySend(snap.children.map {
                    SentNotification(it.key.orEmpty(), it.s("title"), it.s("message"), it.s("imageUrl"),
                        it.s("linkUrl"), it.s("buttonText"), it.s("audience"), it.s("targetUid"),
                        it.n("recipients"), it.n("createdAt"))
                }.sortedByDescending { it.createdAt })
            }
            override fun onCancelled(e: DatabaseError) { close(e.toException()) }
        }
        q.addValueEventListener(l)
        awaitClose { q.removeEventListener(l) }
    }
}
