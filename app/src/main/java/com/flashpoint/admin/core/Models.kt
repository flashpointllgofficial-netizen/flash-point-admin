package com.flashpoint.admin.core

data class Stats(
    val totalOrders: Long = 0,
    val totalCustomers: Long = 0,
    val totalSellers: Long = 0,
    val totalDeliveryPartners: Long = 0,
    val pendingSellers: Long = 0,
    val pendingDelivery: Long = 0,
    val codRevenue: Double = 0.0,
    val returnRequests: Long = 0,
    val exchangeRequests: Long = 0,
)

data class OrderSummary(
    val id: String,
    val customerName: String,
    val totalAmount: Double,
    val status: String,
    val createdAt: Long,
)

data class SellerItem(
    val uid: String,
    val name: String,
    val email: String,
    val phone: String,
    val category: String,
    val status: String, // PENDING, APPROVED, REJECTED, SUSPENDED
)

data class PersonItem(
    val uid: String,
    val name: String,
    val email: String,
    val phone: String,
    val status: String,
    val detail: String,
)

data class OrderItem(
    val id: String,
    val customerName: String,
    val customerUid: String,
    val sellerUid: String,
    val deliveryUid: String,
    val totalAmount: Double,
    val status: String,
    val address: String,
    val phone: String,
    val locationLink: String,
    val createdAt: Long,
    val items: List<String> = emptyList(),
    val deliveredAt: Long = 0,
    val paymentStatus: String = "",
)

data class BannerItem(
    val id: String = "",
    val title: String = "",
    val imageUrl: String = "",
    val imagePublicId: String = "",
    val destinationType: String = "URL", // PRODUCT, SELLER, CATEGORY, URL
    val destinationValue: String = "",
    val enabled: Boolean = true,
    val sortOrder: Long = 0,
    val startAt: Long = 0,
    val expiresAt: Long = 0,
)

data class SentNotification(
    val id: String,
    val title: String,
    val message: String,
    val imageUrl: String,
    val linkUrl: String,
    val buttonText: String,
    val audience: String,
    val targetUid: String,
    val recipients: Long,
    val createdAt: Long,
)

data class HistoryEntry(val status: String, val message: String, val updatedAt: Long, val updatedBy: String)

data class RequestItem(
    val id: String,
    val customerUid: String,
    val sellerUid: String,
    val orderId: String,
    val productId: String,
    val productName: String,
    val quantity: Long,
    val type: String, // RETURN or EXCHANGE
    val reason: String,
    val originalVariant: String,
    val requestedVariant: String,
    val deliveredAt: Long,
    val createdAt: Long,
    val status: String,
    val adminNote: String,
    val refundAmount: Double,
    val refundMethod: String,
    val refundReference: String,
    val refundDate: Long,
    val replacementProductId: String,
    val replacementVariant: String,
    val history: List<HistoryEntry>,
)
