package com.atelier.archive.network

import com.atelier.archive.model.NoteDetail
import com.atelier.archive.model.Platform

object ArchiveDispatcher {

    fun detectPlatform(text: String): Platform? {
        val lower = text.lowercase()
        return when {
            lower.contains("douyin.com") || lower.contains("iesdouyin.com") -> Platform.DOUYIN
            lower.contains("xhslink.com") || lower.contains("xiaohongshu.com") -> Platform.XIAOHONGSHU
            else -> null
        }
    }

    suspend fun parse(rawText: String, customCookie: String = ""): Result<NoteDetail> {
        val platform = detectPlatform(rawText)
            ?: return Result.failure(
                IllegalArgumentException("UNSUPPORTED DISPATCH LINK · 请输入有效的小红书或抖音分享链接")
            )

        return when (platform) {
            Platform.DOUYIN -> DouyinParser.parseFromText(rawText, customCookie)
            Platform.XIAOHONGSHU -> XhsParser.parseFromText(rawText, customCookie)
        }
    }
}
