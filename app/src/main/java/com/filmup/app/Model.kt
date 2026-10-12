package com.filmup.app

import kotlinx.serialization.Serializable

@Serializable
data class Clip(
    val id: String,
    val uri: String,
    val durationMs: Long,
    val inMs: Long,
    val outMs: Long,
    val width: Int,
    val height: Int,
    val caption: String = ""
) {
    val lengthMs: Long get() = outMs - inMs
}

@Serializable
data class Project(
    val clips: List<Clip> = emptyList(),
    val musicUri: String? = null,
    val filter: String = "None",
    val muteOriginal: Boolean = false
)
