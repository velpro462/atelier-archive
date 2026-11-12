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

    private const val DEFAULT_UA =
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

                // Step 1: Follow redirects and fetch HTML
                val requestBuilder = Request.Builder()
                    .url(rawUrl)
                    .header("User-Agent", DEFAULT_UA)
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
                val noteSection = rootObj.optJSONObject("note")
                    ?: throw IllegalStateException("CORRUPTED NOTE METADATA")
                val noteDetailMap = noteSection.optJSONObject("noteDetailMap")
                    ?: throw IllegalStateException("NOTE DETAIL MAP ABSENT")

                val keys = noteDetailMap.keys()
                if (!keys.hasNext()) {
                    throw IllegalStateException("NOTE DATA HAS EXPIRED OR IS PRIVATE / 笔记不存在或已被删除")
                }
                val noteId = keys.next()
                val noteWrapper = noteDetailMap.getJSONObject(noteId)
                val note = noteWrapper.getJSONObject("note")

                val title = note.optString("title", "").ifBlank { "UNTITLED ARCHIVE · 典藏纪" }
                val desc = note.optString("desc", "")
                val typeStr = note.optString("type", "normal")
                val isVideo = typeStr.equals("video", ignoreCase = true)

                // Author Info
                val userObj = note.optJSONObject("user")
                val authorName = userObj?.optString("nickname", "ATELIER CREATOR") ?: "ATELIER CREATOR"
                val authorAvatar = userObj?.optString("avatar", "") ?: ""

                // Tags
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
                    val videoObj = note.optJSONObject("video")
                    val mediaObj = videoObj?.optJSONObject("media")
                    val streamObj = mediaObj?.optJSONObject("stream")
                    val h264Arr = streamObj?.optJSONArray("h264")
                    val h265Arr = streamObj?.optJSONArray("h265")

                    val firstH264 = h264Arr?.optJSONObject(0)?.optString("masterUrl")
                    val firstH265 = h265Arr?.optJSONObject(0)?.optString("masterUrl")
                    videoUrl = firstH264 ?: firstH265

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
                            if (cleanUrl.isNotBlank()) {
                                images.add(cleanUrl)
                            }
                        }
                    }
                }

                NoteDetail(
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
        }

    private fun extractCleanImageUrl(imgObj: JSONObject): String {
        // 1. Prefer urlDefault directly (keeps signed CDN signature intact)
        val urlDefault = imgObj.optString("urlDefault", "")
        if (urlDefault.isNotBlank()) {
            return ensureHttps(urlDefault)
        }

        // 2. Check infoList array
        val infoList = imgObj.optJSONArray("infoList")
        if (infoList != null && infoList.length() > 0) {
            var candidate = ""
            for (i in 0 until infoList.length()) {
                val item = infoList.optJSONObject(i) ?: continue
                val scene = item.optString("imageScene")
                val u = item.optString("url")
                if (scene == "WB_DFT" && u.isNotBlank()) {
                    candidate = u
                    break
                }
                if (candidate.isBlank() && u.isNotBlank()) {
                    candidate = u
                }
            }
            if (candidate.isNotBlank()) {
                return ensureHttps(candidate)
            }
        }

        // 3. Fallback traceId
        val traceId = imgObj.optString("traceId", "")
        if (traceId.isNotBlank()) {
            return "https://sns-webpic-qc.xhscdn.com/265/$traceId"
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
