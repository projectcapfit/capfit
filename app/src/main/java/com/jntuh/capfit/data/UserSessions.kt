package com.jntuh.capfit.data

data class UserSessions(
    val sessions: List<String> = emptyList(),   // solo session IDs
    val groups: List<String> = emptyList(),     // group session IDs
    val isSessionLive: Boolean = false,
    val isGroupLive: Boolean = false,
    val capturedArea: Double = 0.0
)