package com.faster.tibot.data.rootfs

data class MirrorSource(
    val id: String,
    val name: String,
    val url: String,
)

data class SpeedResult(
    val mirrorId: String,
    val latencyMs: Long,
    val error: String? = null,
)
