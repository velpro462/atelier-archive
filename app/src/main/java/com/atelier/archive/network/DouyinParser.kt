package com.atelier.archive.network

import android.content.Context
import com.atelier.archive.model.NoteDetail
import com.atelier.archive.model.NoteType
import com.atelier.archive.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object DouyinParser {

    private const val MOBILE_UA =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val URL_REGEX = Pattern.compile("https?://[a-zA-Z0-9.\\-_/]+")
    private val ID_REGEX = Pattern.compile("(?:video|note)/([0-9]{18,21})")
    private val DIGITS_REGEX = Pattern.compile("([0-9]{18,21})")

    private data class ApiParseResult(
        val note: NoteDetail? = null,
        val restrictionReason: String? = null
    )

    suspend fun parseFromText(
        rawText: String,
        customCookie: String = "",
        context: Context? = null
    ): Result<NoteDetail> = withContext(Dispatchers.IO) {
        runCatching {
            val matcher = URL_REGEX.matcher(rawText)
            if (!matcher.find()) {
                throw IllegalArgumentException("NO VALID DOUYIN LINK FOUND / 未检测到有效的抖音链接")
            }
            val rawUrl = matcher.group(0) ?: throw IllegalArgumentException("INVALID LINK")

            // Step 1: Follow redirects to find the target item ID and real URL
            val reqBuilder = Request.Builder()
                .url(rawUrl)
                .header("User-Agent", MOBILE_UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", "https://www.douyin.com/")

            if (customCookie.isNotBlank()) {
                reqBuilder.header("Cookie", customCookie)
            }

            val response = client.newCall(reqBuilder.build()).execute()
            val finalUrl = response.request.url.toString()
            val html = response.body?.string().orEmpty()

            // Extract itemId (e.g. 7659378371922580709)
            var itemId = ""
            val idMatcher = ID_REGEX.matcher(finalUrl)
            if (idMatcher.find()) {
                itemId = idMatcher.group(1) ?: ""
            }
            if (itemId.isBlank()) {
                val digitsMatcher = DIGITS_REGEX.matcher(finalUrl)
                if (digitsMatcher.find()) {
                    itemId = digitsMatcher.group(1) ?: ""
                }
            }
            if (itemId.isBlank()) {
                val htmlMatcher = DIGITS_REGEX.matcher(html)
                if (htmlMatcher.find()) {
                    itemId = htmlMatcher.group(1) ?: ""
                }
            }

            if (itemId.isBlank()) {
                throw IllegalStateException("FAILED TO RESOLVE DOUYIN ITEM ID / 无法识别抖音作品ID")
            }

            // Check if explicitly a photo note
            val isExplicitNote = rawText.contains("图文") ||
                    rawText.contains("动图") ||
                    rawText.contains("图集") ||
                    rawText.contains("图片") ||
                    finalUrl.contains("/note/") ||
                    rawUrl.contains("/note/") ||
                    finalUrl.contains("/slides/") ||
                    finalUrl.contains("is_slides=1")

            // Step 2: Try Method A2 (fast API for standard video notes)
            // If explicit note, do NOT accept video from iteminfo API!
            if (!isExplicitNote) {
                val itemResult = tryParseItemInfo(itemId, finalUrl, customCookie)
                if (itemResult.note != null && itemResult.note.type == NoteType.VIDEO) {
                    return@runCatching itemResult.note
                }
            }

            // Step 3: Try Method A1 (slidesinfo API)
            val slidesResult = tryParseSlidesInfo(itemId, finalUrl, customCookie)
            if (slidesResult.note != null) {
                return@runCatching slidesResult.note
            }

            // Step 4: Primary resolver for Douyin notes & protected works: Headless WebView
            if (context != null) {
                val targetWebUrl = if (itemId.isNotBlank()) {
                    if (finalUrl.contains("/video/") && !finalUrl.contains("/slides/") && !finalUrl.contains("is_slides=1")) {
                        "https://www.douyin.com/video/$itemId"
                    } else {
                        "https://www.douyin.com/note/$itemId"
                    }
                } else finalUrl
                val webResult = DouyinWebResolver.resolve(context, targetWebUrl, itemId, customCookie, isExplicitNote = isExplicitNote)
                if (webResult.isSuccess) {
                    val note = webResult.getOrNull()
                    if (note != null && (note.images.isNotEmpty() || (!isExplicitNote && note.videoUrl != null))) {
                        return@runCatching note
                    }
                }
            }

            // Step 5: Fallback to Method B (HTML RENDER_DATA / SSR)
            if (html.isNotBlank()) {
                val htmlNote = tryParseFromHtml(html, itemId, finalUrl)
                if (htmlNote != null) {
                    if (!isExplicitNote || htmlNote.type == NoteType.IMAGE) {
                        return@runCatching htmlNote
                    }
                }
            }

            throw IllegalStateException("FAILED TO EXTRACT DOUYIN ASSETS · 未能成功提取作品原画，请检查网络或在凭证配置中填入 Cookie")
        }
    }

    /**
     * Method A1: Fetch via iesdouyin slidesinfo endpoint (Photo notes / Slides)
     */
    private fun tryParseSlidesInfo(itemId: String, finalUrl: String, customCookie: String): ApiParseResult {
        return try {
            val apiUrl = "https://www.iesdouyin.com/web/api/v2/aweme/slidesinfo/?aweme_ids=[$itemId]&aid=1128"
            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", MOBILE_UA)
                .header("Referer", "https://www.iesdouyin.com/share/note/$itemId/")
                .apply {
                    if (customCookie.isNotBlank()) header("Cookie", customCookie)
                }
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return ApiParseResult()

            val jsonStr = resp.body?.string() ?: return ApiParseResult()
            val root = JSONObject(jsonStr)

            val awemeDetails = root.optJSONArray("aweme_details")
            if (awemeDetails != null && awemeDetails.length() > 0) {
                val item = awemeDetails.getJSONObject(0)
                return ApiParseResult(note = parseAwemeItem(item, itemId, finalUrl))
            }

            ApiParseResult()
        } catch (_: Exception) {
            ApiParseResult()
        }
    }

    /**
     * Method A2: Fetch via iesdouyin iteminfo endpoint (Video notes)
     */
    private fun tryParseItemInfo(itemId: String, finalUrl: String, customCookie: String): ApiParseResult {
        return try {
            val apiUrl = "https://www.iesdouyin.com/web/api/v2/aweme/iteminfo/?item_ids=$itemId"
            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", MOBILE_UA)
                .header("Referer", "https://www.iesdouyin.com/")
                .apply {
                    if (customCookie.isNotBlank()) header("Cookie", customCookie)
                }
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return ApiParseResult()

            val jsonStr = resp.body?.string() ?: return ApiParseResult()
            val root = JSONObject(jsonStr)
            val itemList = root.optJSONArray("item_list")
            if (itemList != null && itemList.length() > 0) {
                val item = itemList.getJSONObject(0)
                return ApiParseResult(note = parseAwemeItem(item, itemId, finalUrl))
            }
            ApiParseResult()
        } catch (_: Exception) {
            ApiParseResult()
        }
    }

    /**
     * Method B: Parse HTML RENDER_DATA or _ROUTER_DATA
     */
    private fun tryParseFromHtml(html: String, itemId: String, finalUrl: String): NoteDetail? {
        return try {
            val renderPattern = Pattern.compile("<script\\s+id=\"RENDER_DATA\"[^>]*>(.+?)</script>")
            val matcher = renderPattern.matcher(html)
            if (matcher.find()) {
                val rawEncoded = matcher.group(1) ?: return null
                val decodedJson = URLDecoder.decode(rawEncoded, "UTF-8")
                val root = JSONObject(decodedJson)

                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = root.optJSONObject(key) ?: continue
                    val videoDetail = child.optJSONObject("videoDetail")
                    if (videoDetail != null) {
                        return parseAwemeItem(videoDetail, itemId, finalUrl)
                    }
                    val aweme = child.optJSONObject("aweme")
                    if (aweme != null) {
                        return parseAwemeItem(aweme, itemId, finalUrl)
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseAwemeItem(item: JSONObject, itemId: String, finalUrl: String): NoteDetail {
        val desc = item.optString("desc", "").ifBlank { "UNTITLED DOUYIN WORK · 抖音作品" }
        val authorObj = item.optJSONObject("author")
        val authorName = authorObj?.optString("nickname", "DOUYIN CREATOR") ?: "DOUYIN CREATOR"
        val rawAvatar = authorObj?.optJSONObject("avatar_thumb")
            ?.optJSONArray("url_list")?.optString(0) ?: ""
        val authorAvatar = if (rawAvatar.isNotBlank()) ensureHttps(rawAvatar) else ""

        // Check if Photo Gallery (images)
        val imagesArray = item.optJSONArray("images")
        val isPhotoNote = imagesArray != null && imagesArray.length() > 0

        val images = mutableListOf<String>()
        var videoUrl: String? = null

        if (isPhotoNote && imagesArray != null) {
            for (i in 0 until imagesArray.length()) {
                val imgObj = imagesArray.optJSONObject(i) ?: continue
                val urlList = imgObj.optJSONArray("url_list")
                if (urlList != null && urlList.length() > 0) {
                    val highRes = urlList.optString(0)
                    if (highRes.isNotBlank()) {
                        images.add(ensureHttps(highRes))
                    }
                }
            }
        } else {
            // Video Note: Extract watermark-free play url
            val videoObj = item.optJSONObject("video")
            val playAddr = videoObj?.optJSONObject("play_addr")
            val urlList = playAddr?.optJSONArray("url_list")
            if (urlList != null && urlList.length() > 0) {
                var bestUrl = urlList.optString(0)
                if (bestUrl.contains("playwm")) {
                    bestUrl = bestUrl.replace("playwm", "play")
                }
                videoUrl = ensureHttps(bestUrl)
            }
        }

        val type = if (isPhotoNote) NoteType.IMAGE else NoteType.VIDEO

        return NoteDetail(
            id = itemId,
            title = desc,
            desc = desc,
            authorName = authorName,
            authorAvatar = authorAvatar,
            platform = Platform.DOUYIN,
            type = type,
            images = images,
            videoUrl = videoUrl,
            shareUrl = finalUrl,
            tags = emptyList()
        )
    }

    private fun ensureHttps(url: String): String {
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") -> url.replaceFirst("http://", "https://")
            else -> url
        }
    }
}
