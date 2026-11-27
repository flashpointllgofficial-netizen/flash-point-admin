package com.flashpoint.admin.data

import com.flashpoint.admin.core.BannerItem
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class BannerRepository(
    private val db: FirebaseDatabase =
        FirebaseDatabase.getInstance("https://flashpoint-57a82-default-rtdb.firebaseio.com"),
) {
    private val ref = db.getReference("admin/banners")

    private fun DataSnapshot.s(k: String) = child(k).value?.toString().orEmpty()
    private fun DataSnapshot.n(k: String) = (child(k).value as? Number)?.toLong() ?: 0L

    fun banners(): Flow<List<BannerItem>> = callbackFlow {
        val l = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                trySend(snap.children.map {
                    BannerItem(it.key.orEmpty(), it.s("title"), it.s("imageUrl"), it.s("imagePublicId"),
                        it.s("destinationType").ifBlank { "URL" }, it.s("destinationValue"),
                        it.child("enabled").value == true, it.n("sortOrder"), it.n("startAt"), it.n("expiresAt"))
                }.sortedBy { it.sortOrder })
            }
            override fun onCancelled(e: DatabaseError) { close(e.toException()) }
        }
        ref.addValueEventListener(l)
        awaitClose { ref.removeEventListener(l) }
    }

    suspend fun save(b: BannerItem, nextOrder: Long) {
        val isNew = b.id.isBlank()
        val id = if (isNew) ref.push().key.orEmpty() else b.id
        val map = mutableMapOf<String, Any>(
            "title" to b.title, "imageUrl" to b.imageUrl, "imagePublicId" to b.imagePublicId,
            "destinationType" to b.destinationType, "destinationValue" to b.destinationValue,
            "enabled" to b.enabled, "startAt" to b.startAt, "expiresAt" to b.expiresAt,
            "updatedAt" to ServerValue.TIMESTAMP,
        )
        if (isNew) { map["sortOrder"] = nextOrder; map["createdAt"] = ServerValue.TIMESTAMP }
        ref.child(id).updateChildren(map).await()
    }

    suspend fun delete(id: String) { ref.child(id).removeValue().await() }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        ref.child(id).child("enabled").setValue(enabled).await()
    }

    suspend fun swapOrder(a: BannerItem, b: BannerItem) {
        db.reference.updateChildren(mapOf(
            "admin/banners/${a.id}/sortOrder" to b.sortOrder,
            "admin/banners/${b.id}/sortOrder" to a.sortOrder,
        )).await()
    }
}
