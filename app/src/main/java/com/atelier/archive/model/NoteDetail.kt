package com.atelier.archive.model

enum class Platform(val displayName: String, val code: String) {
    XIAOHONGSHU("RED ARCHIVE · 小红书", "XHS"),
    DOUYIN("DOUYIN ARCHIVE · 抖音", "DY")
}

enum class NoteType {
    IMAGE,
    VIDEO
}

data class NoteDetail(
    val id: String,
    val title: String,
    val desc: String,
    val authorName: String,
    val authorAvatar: String,
    val platform: Platform,
    val type: NoteType,
    val images: List<String>,
    val videoUrl: String?,
    val shareUrl: String,
    val tags: List<String> = emptyList()
)

sealed interface ParseState {
    object Idle : ParseState
    object Parsing : ParseState
    data class Success(val note: NoteDetail) : ParseState
    data class Error(val message: String) : ParseState
}

sealed interface DownloadState {
    object Idle : DownloadState
    data class Progress(val current: Int, val total: Int) : DownloadState
    data class Success(val count: Int) : DownloadState
    data class Error(val message: String) : DownloadState
}
