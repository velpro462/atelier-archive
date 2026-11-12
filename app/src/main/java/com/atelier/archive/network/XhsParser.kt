package com.atelier.archive.network

import com.atelier.archive.model.NoteDetail
import com.atelier.archive.model.NoteType
import com.atelier.archive.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object XhsParser {

    private const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val URL_REGEX = Pattern.compile("https?://[a-zA-Z0-9.\\-_/]+")
    private val STATE_REGEX = Pattern.compile("window\\.__INITIAL_STATE__\\s*=\\s*(\\{.+?\\})\\s*</script>", Pattern.DOTALL)

    suspend fun parseFromText(rawText: String, customCookie: String = ""): Result<NoteDetail> =
        withContext(Dispatchers.IO) {
            runCatching {
                val matcher = URL_REGEX.matcher(rawText)
                if (!matcher.find()) {
                    throw IllegalArgumentException("NO VALID XHS URL FOUND IN CLIPBOARD / 未检测到有效的小红书链接")
                }
                val rawUrl = matcher.group(0) ?: throw IllegalArgumentException("INVALID LINK FORMAT")

                // Step 1: Follow redirects and fetch HTML using Mobile UA (bypasses login redirect)
                val uaToUse = if (customCookie.isNotBlank()) DESKTOP_UA else MOBILE_UA
                val requestBuilder = Request.Builder()
                    .url(rawUrl)
                    .header("User-Agent", uaToUse)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("Referer", "https://www.xiaohongshu.com/")

                if (customCookie.isNotBlank()) {
                    requestBuilder.header("Cookie", customCookie)
                }

                val response = client.newCall(requestBuilder.build()).execute()
                val finalUrl = response.request.url.toString()
                val html = response.body?.string() ?: throw IllegalStateException("EMPTY RESPONSE BODY")

                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ERROR ${response.code}: UNABLE TO REACH NOTE")
                }

                if (finalUrl.contains("/login")) {
                    throw IllegalStateException("小红书访问受限，请在右上角凭证配置中填入 Cookie 后重试")
                }

                // Step 2: Extract window.__INITIAL_STATE__
                val stateMatcher = STATE_REGEX.matcher(html)
                val jsonString = if (stateMatcher.find()) {
                    stateMatcher.group(1)
                } else {
                    val marker = "window.__INITIAL_STATE__="
                    val startIdx = html.indexOf(marker)
                    if (startIdx != -1) {
                        val scriptEnd = html.indexOf("</script>", startIdx)
                        if (scriptEnd != -1) {
                            html.substring(startIdx + marker.length, scriptEnd).trim()
                        } else null
                    } else null
                } ?: throw IllegalStateException("FAILED TO LOCATE SSR DATA (反爬验证或笔记已下架)")

                val sanitizedJson = jsonString
                    .replace(":undefined", ":null")
                    .replace(": undefined", ": null")

                val rootObj = JSONObject(sanitizedJson)

                // Try Branch 1: Mobile H5 template (noteData -> data -> noteData, or noteData -> note)
                val mobileNote = rootObj.optJSONObject("noteData")?.let { noteDataObj ->
                    noteDataObj.optJSONObject("data")?.optJSONObject("noteData")
                        ?: noteDataObj.optJSONObject("note")
                        ?: noteDataObj.optJSONObject("noteData")
                }

                if (mobileNote != null) {
                    return@runCatching parseMobileNote(mobileNote, finalUrl, sanitizedJson)
                }

                // Try Branch 2: Desktop SSR template (note -> noteDetailMap -> noteId -> note)
                val desktopNoteSection = rootObj.optJSONObject("note")
                val noteDetailMap = desktopNoteSection?.optJSONObject("noteDetailMap")
                if (noteDetailMap != null) {
                    val keys = noteDetailMap.keys()
                    if (keys.hasNext()) {
                        val noteId = keys.next()
                        val noteWrapper = noteDetailMap.optJSONObject(noteId)
                        val note = noteWrapper?.optJSONObject("note")
                        if (note != null) {
                            return@runCatching parseDesktopNote(note, noteId, finalUrl, sanitizedJson)
                        }
                    }
                }

                throw IllegalStateException("CORRUPTED NOTE METADATA / 笔记已被删除或需要登录查看")
            }
        }

    private fun parseMobileNote(note: JSONObject, finalUrl: String, rawJson: String? = null): NoteDetail {
        val noteId = note.optString("noteId", "")
        val title = note.optString("title", "").ifBlank { "UNTITLED NOTE" }
        val desc = note.optString("desc", "")
        val typeStr = note.optString("type", "normal")
        val isVideo = typeStr.equals("video", ignoreCase = true)

        val userObj = note.optJSONObject("user")
        val authorName = userObj?.optString("nickName")?.ifBlank {
            userObj.optString("nickname", "XHS CREATOR")
        } ?: "XHS CREATOR"
        val authorAvatar = userObj?.optString("avatar", "") ?: ""

        val tagList = mutableListOf<String>()
        val tagArray = note.optJSONArray("tagList")
        if (tagArray != null) {
            for (i in 0 until tagArray.length()) {
                val tag = tagArray.optJSONObject(i)?.optString("name")
                if (!tag.isNullOrBlank()) tagList.add(tag)
            }
        }

        val images = mutableListOf<String>()
        var videoUrl: String? = null

        if (isVideo) {
            videoUrl = extractCleanVideoUrl(note, rawJson)

            val imageList = note.optJSONArray("imageList")
            if (imageList != null && imageList.length() > 0) {
                val cover = extractCleanImageUrl(imageList.getJSONObject(0))
                if (cover.isNotBlank()) images.add(cover)
            }
        } else {
            val imageList = note.optJSONArray("imageList")
            if (imageList != null) {
                for (i in 0 until imageList.length()) {
                    val imgObj = imageList.optJSONObject(i) ?: continue
                    val cleanUrl = extractCleanImageUrl(imgObj)
                    if (cleanUrl.isNotBlank() && !images.contains(cleanUrl)) {
                        images.add(cleanUrl)
                    }
                }
            }
        }

        return NoteDetail(
            id = noteId,
            title = title,
            desc = desc,
            authorName = authorName,
            authorAvatar = authorAvatar,
            platform = Platform.XIAOHONGSHU,
            type = if (isVideo) NoteType.VIDEO else NoteType.IMAGE,
            images = images,
            videoUrl = videoUrl,
            shareUrl = finalUrl,
            tags = tagList
        )
    }

    private fun parseDesktopNote(note: JSONObject, noteId: String, finalUrl: String, rawJson: String? = null): NoteDetail {
        val title = note.optString("title", "").ifBlank { "UNTITLED NOTE" }
        val desc = note.optString("desc", "")
        val typeStr = note.optString("type", "normal")
        val isVideo = typeStr.equals("video", ignoreCase = true)

        val userObj = note.optJSONObject("user")
        val authorName = userObj?.optString("nickname", "XHS CREATOR") ?: "XHS CREATOR"
        val authorAvatar = userObj?.optString("avatar", "") ?: ""

        val tagList = mutableListOf<String>()
        val tagArray = note.optJSONArray("tagList")
        if (tagArray != null) {
            for (i in 0 until tagArray.length()) {
                val tag = tagArray.optJSONObject(i)?.optString("name")
                if (!tag.isNullOrBlank()) tagList.add(tag)
            }
        }

        val images = mutableListOf<String>()
        var videoUrl: String? = null

        if (isVideo) {
            videoUrl = extractCleanVideoUrl(note, rawJson)

            val imageList = note.optJSONArray("imageList")
            if (imageList != null && imageList.length() > 0) {
                val cover = extractCleanImageUrl(imageList.getJSONObject(0))
                if (cover.isNotBlank()) images.add(cover)
            }
        } else {
            val imageList = note.optJSONArray("imageList")
            if (imageList != null) {
                for (i in 0 until imageList.length()) {
                    val imgObj = imageList.optJSONObject(i) ?: continue
                    val cleanUrl = extractCleanImageUrl(imgObj)
                    if (cleanUrl.isNotBlank() && !images.contains(cleanUrl)) {
                        images.add(cleanUrl)
                    }
                }
            }
        }

        return NoteDetail(
            id = noteId,
            title = title,
            desc = desc,
            authorName = authorName,
            authorAvatar = authorAvatar,
            platform = Platform.XIAOHONGSHU,
            type = if (isVideo) NoteType.VIDEO else NoteType.IMAGE,
            images = images,
            videoUrl = videoUrl,
            shareUrl = finalUrl,
            tags = tagList
        )
    }

    private fun extractCleanVideoUrl(note: JSONObject, rawJson: String? = null): String? {
        val videoObj = note.optJSONObject("video")

        // Priority 1: Direct uncompressed original master video key uploaded by creator (100% watermark-free)
        val originKey = videoObj?.optJSONObject("consumer")?.optString("originVideoKey")?.takeIf { it.isNotBlank() }
            ?: videoObj?.optString("originVideoKey")?.takeIf { it.isNotBlank() }
            ?: videoObj?.optJSONObject("media")?.optString("originVideoKey")?.takeIf { it.isNotBlank() }
            ?: rawJson?.let {
                val m = Pattern.compile("\"originVideoKey\"\\s*:\\s*\"([^\"]+)\"").matcher(it)
                if (m.find()) m.group(1) else null
            }

        if (!originKey.isNullOrBlank()) {
            val cleanKey = originKey.removePrefix("/")
            return "https://sns-video-bd.xhscdn.com/$cleanKey"
        }

        // Priority 2: Filter stream list to prioritize non-watermarked streams
        val mediaObj = videoObj?.optJSONObject("media")
        val streamObj = mediaObj?.optJSONObject("stream")
        val streamCandidates = mutableListOf<JSONObject>()

        fun addStreams(arr: org.json.JSONArray?) {
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { streamCandidates.add(it) }
                }
            }
        }
        addStreams(streamObj?.optJSONArray("h264"))
        addStreams(streamObj?.optJSONArray("h265"))
        addStreams(streamObj?.optJSONArray("av1"))

        // Look for stream without "wm" or "watermark" in streamDesc, qualityType, etc.
        val cleanStream = streamCandidates.firstOrNull { s ->
            val desc = s.optString("streamDesc", "")
            val quality = s.optString("qualityType", "")
            val format = s.optString("format", "")
            !desc.contains("wm", ignoreCase = true) &&
            !desc.contains("watermark", ignoreCase = true) &&
            !quality.contains("wm", ignoreCase = true) &&
            !format.contains("wm", ignoreCase = true) &&
            !s.optBoolean("watermark", false)
        }

        if (cleanStream != null) {
            val cleanUrl = cleanStream.optString("masterUrl").takeIf { it.isNotBlank() }
                ?: cleanStream.optJSONArray("backupUrls")?.optString(0)?.takeIf { it.isNotBlank() }
            if (!cleanUrl.isNullOrBlank()) {
                return ensureHttps(cleanUrl)
            }
        }

        // Priority 3: Fallback to any masterUrl or video source
        val fallback = streamCandidates.firstOrNull()?.optString("masterUrl")?.takeIf { it.isNotBlank() }
            ?: videoObj?.optString("src")?.takeIf { it.isNotBlank() }
            ?: videoObj?.optString("url")?.takeIf { it.isNotBlank() }

        return fallback?.let { ensureHttps(it) }
    }

    private fun extractCleanImageUrl(imgObj: JSONObject): String {
        // 1. Direct fileId from object (raw lossless original stored on public CDN without watermark)
        val fileId = imgObj.optString("fileId", "").trim()
        if (fileId.isNotBlank()) {
            return "https://sns-img-hw.xhscdn.com/$fileId"
        }

        // 2. Direct traceId from object
        val traceId = imgObj.optString("traceId", "").trim()
        if (traceId.isNotBlank()) {
            return "https://sns-img-hw.xhscdn.com/$traceId"
        }

        // 3. Extract clean ID from url or urlDefault (bypasses !h5_1080jpg and !nd_dft... watermarked modifiers)
        val rawUrl = imgObj.optString("url", "").ifBlank {
            imgObj.optString("urlDefault", "")
        }

        if (rawUrl.isNotBlank()) {
            val cleanId = extractFileIdFromUrl(rawUrl)
            if (cleanId.isNotBlank()) {
                return "https://sns-img-hw.xhscdn.com/$cleanId"
            }
        }

        // 4. Check infoList array
        val infoList = imgObj.optJSONArray("infoList")
        if (infoList != null && infoList.length() > 0) {
            for (i in 0 until infoList.length()) {
                val item = infoList.optJSONObject(i) ?: continue
                val u = item.optString("url", "")
                val cleanId = extractFileIdFromUrl(u)
                if (cleanId.isNotBlank()) {
                    return "https://sns-img-hw.xhscdn.com/$cleanId"
                }
            }
        }

        // 5. Ultimate fallback
        if (rawUrl.isNotBlank()) {
            return ensureHttps(rawUrl)
        }

        return ""
    }

    private fun extractFileIdFromUrl(url: String): String {
        val bangIdx = url.indexOf('!')
        val target = if (bangIdx != -1) url.substring(0, bangIdx) else url
        val qIdx = target.indexOf('?')
        val cleanPath = if (qIdx != -1) target.substring(0, qIdx) else target

        val segments = cleanPath.split("/")
        if (segments.size >= 2 && segments[segments.size - 2] == "notes_uhdr") {
            return "notes_uhdr/" + segments.last()
        }
        val last = segments.lastOrNull().orEmpty()
        if (last.length >= 20) {
            return last
        }
        return ""
    }

    private fun ensureHttps(url: String): String {
        var clean = url.trim()
        if (clean.startsWith("http://")) {
            clean = "https://" + clean.substring(7)
        }
        return clean
    }
}
