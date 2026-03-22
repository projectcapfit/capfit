package com.jntuh.capfit.data


data class Group(
    //   groups/{groupId}/{document}
    val groupId: String = "",
    val name: String = "",
    val leaderId: String = "",
    val memberIds: List<String> = emptyList(),
    val rewardRatio: Map<String, Double> = emptyMap(),
    val activeSessionId: String? = null
)

