package com.aras.client.ui.rdrx

enum class RdrXStatus {
    OFFLINE,
    CONNECTING,
    VERIFYING,
    CONNECTED,
    ERROR,
}

data class RdrXNode(
    val id: String,
    val name: String,
    val country: String,
    val protocol: String,
    val pingMs: Int? = null,
)

data class RdrXMenuItem(
    val id: String,
    val label: String,
)

enum class RdrXSort(val label: String) {
    PING("پینگ"),
    NAME("کشور"),
}
