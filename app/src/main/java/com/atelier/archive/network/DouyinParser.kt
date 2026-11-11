package com.atelier.archive.network

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

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val URL_REGEX = Pattern.compile("https?://[a-zA-Z0-9.\\-_/]+")
    private val ID_REGEX = Pattern.compile("(?:video|note)/([0-9]{18,21})")
    private val DIGITS_REGEX = Pattern.compile("([0-9]{18,21})")

    suspend fun parseFromText(rawText: String, customCookie: String = ""): Result<NoteDetail> =
        withContext(Dispatchers.IO) {
            runCatching {
                val matcher = URL_REGEX.matcher(rawText)
                if (!matcher.find()) {
                    throw IllegalArgumentException("NO VALID DOUYIN LINK FOUND / 未检测到有效的抖音链接")
                }
                val rawUrl = matcher.group(0) ?: throw IllegalArgumentException("INVALID LINK")

                // Step 1: Follow redirects to find the target item ID
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

                // Extract itemId (e.g. 7391234567890123456)
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
                    // Search in html
                    val htmlMatcher = DIGITS_REGEX.matcher(html)
                    if (htmlMatcher.find()) {
                        itemId = htmlMatcher.group(1) ?: ""
                    }
                }

                if (itemId.isBlank()) {
                    throw IllegalStateException("FAILED TO RESOLVE DOUYIN ITEM ID / 无法识别抖音作品ID")
                }

                // Step 2: Try Method A (iesdouyin Web API)
                var parsedNote = tryParseFromApi(itemId, finalUrl, customCookie)

                // Step 3: If API fails, fallback to Method B (HTML RENDER_DATA / SSR)
                if (parsedNote == null && html.isNotBlank()) {
                    parsedNote = tryParseFromHtml(html, itemId, finalUrl)
                }

                parsedNote ?: throw IllegalStateException("FAILED TO EXTRACT DOUYIN ASSETS (作品可能已删除或隐私不可见)")
            }
        }

    /**
     * Method A: Fetch via iesdouyin open endpoint
     */
    private fun tryParseFromApi(itemId: String, finalUrl: String, customCookie: String): NoteDetail? {
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
            if (!resp.isSuccessful) return null

            val jsonStr = resp.body?.string() ?: return null
            val root = JSONObject(jsonStr)
            val itemList = root.optJSONArray("item_list") ?: return null
            if (itemList.length() == 0) return null

            val item = itemList.getJSONObject(0)
            parseAwemeItem(item, itemId, finalUrl)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Method B: Parse HTML RENDER_DATA or _ROUTER_DATA
     */
    private fun tryParseFromHtml(html: String, itemId: String, finalUrl: String): NoteDetail? {
        return try {
            // Find <script id="RENDER_DATA" type="application/json">...</script>
            val renderPattern = Pattern.compile("<script\\s+id=\"RENDER_DATA\"[^>]*>(.+?)</script>")
            val matcher = renderPattern.matcher(html)
            if (matcher.find()) {
                val rawEncoded = matcher.group(1) ?: return null
                val decodedJson = URLDecoder.decode(rawEncoded, "UTF-8")
                val root = JSONObject(decodedJson)

                // Search recursively or inspect root keys
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
        val authorAvatar = authorObj?.optJSONObject("avatar_thumb")
            ?.optJSONArray("url_list")?.optString(0) ?: ""

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
                        images.add(highRes)
                    }
                }
            }
        } else {
            // Video Note: Extract watermark-free play url
            val videoObj = item.optJSONObject("video")
            val playAddr = videoObj?.optJSONObject("play_addr")
            val urlList = playAddr?.optJSONArray("url_list")

            var rawPlayUrl = urlList?.optString(0).orEmpty()
            if (rawPlayUrl.isNotBlank()) {
                // Key trick: replace "playwm" with "play" to remove Douyin watermark
                val cleanPlayUrl = rawPlayUrl.replace("playwm", "play")
                // Resolve 302 redirect to direct CDN mp4
                videoUrl = resolveDirectVideoCdnUrl(cleanPlayUrl)
            }

            // Cover image
            val coverUrl = videoObj?.optJSONObject("cover")
                ?.optJSONArray("url_list")?.optString(0).orEmpty()
            if (coverUrl.isNotBlank()) {
                images.add(coverUrl)
            }
        }

        return NoteDetail(
            id = itemId,
            title = desc,
            desc = desc,
            authorName = authorName,
            authorAvatar = authorAvatar,
            platform = Platform.DOUYIN,
            type = if (isPhotoNote) NoteType.IMAGE else NoteType.VIDEO,
            images = images,
            videoUrl = videoUrl,
            shareUrl = finalUrl,
            tags = listOf("DOUYIN", if (isPhotoNote) "PHOTO" else "VIDEO")
        )
    }

    /**
     * Resolves the 302 redirect from aweme.snssdk.com to the direct v26-web.douyinvod.com CDN URL
     */
    private fun resolveDirectVideoCdnUrl(playUrl: String): String {
        return try {
            val req = Request.Builder()
                .url(playUrl)
                .header("User-Agent", MOBILE_UA)
                .header("Referer", "https://www.douyin.com/")
                .build()

            val resp = client.newCall(req).execute()
            resp.request.url.toString()
        } catch (_: Exception) {
            playUrl
        }
    }
}
