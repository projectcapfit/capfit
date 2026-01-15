package com.jntuh.capfit.data

data class AppNotification(
    val id: String = "",
    val type: NotificationType = NotificationType.GENERIC,
    val title: String = "",
    val message: String = "",
    val fromUserId: String? = null,
    val data: Map<String, String>? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val actionStatus: String = "PENDING"
)
