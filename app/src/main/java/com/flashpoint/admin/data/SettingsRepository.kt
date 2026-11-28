package com.flashpoint.admin.data

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.tasks.await

data class AppSettings(
    val deliveryCharge: Double = 0.0,
    val earningPerDelivery: Double = 0.0,
    val freeDeliveryAbove: Double = 0.0, // 0 = disabled
)

/** Stored in /appConfig so the Customer and Delivery apps can read it; only Admin can write it. */
class SettingsRepository(
    private val db: FirebaseDatabase =
        FirebaseDatabase.getInstance("https://flashpoint-57a82-default-rtdb.firebaseio.com"),
) {
    private val ref = db.getReference("appConfig")
    private fun com.google.firebase.database.DataSnapshot.d(k: String) = (child(k).value as? Number)?.toDouble() ?: 0.0

    suspend fun load(): AppSettings {
        val s = ref.get().await()
        return AppSettings(s.d("deliveryCharge"), s.d("earningPerDelivery"), s.d("freeDeliveryAbove"))
    }

    suspend fun save(a: AppSettings) {
        ref.updateChildren(mapOf(
            "deliveryCharge" to a.deliveryCharge,
            "earningPerDelivery" to a.earningPerDelivery,
            "freeDeliveryAbove" to a.freeDeliveryAbove,
            "updatedAt" to ServerValue.TIMESTAMP,
        )).await()
    }
}
