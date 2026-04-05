package com.jntuh.capfit.data

import com.google.firebase.firestore.PropertyName

data class TrackingSession(
    val sessionId: String = "",
    val userId: String = "",
    val userName: String = "Unknown",
    val groupId: String? = null,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val points: List<TrackPoint> = emptyList(),
    val distance: Double = 0.0,
    val area: Double = 0.0,
    val date: String = "",

    val geohash: String = "",

    @get:PropertyName("xMin") @set:PropertyName("xMin") var xMin: Double = 0.0,
    @get:PropertyName("xMax") @set:PropertyName("xMax") var xMax: Double = 0.0,
    @get:PropertyName("yMin") @set:PropertyName("yMin") var yMin: Double = 0.0,
    @get:PropertyName("yMax") @set:PropertyName("yMax") var yMax: Double = 0.0,

    @get:PropertyName("isLive") @set:PropertyName("isLive") var isLive: Boolean = false
)