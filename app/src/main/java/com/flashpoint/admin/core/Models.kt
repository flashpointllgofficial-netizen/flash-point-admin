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
