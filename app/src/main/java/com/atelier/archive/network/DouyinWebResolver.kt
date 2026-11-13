package com.atelier.archive.network

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.atelier.archive.model.NoteDetail
import com.atelier.archive.model.NoteType
import com.atelier.archive.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

object DouyinWebResolver {

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private fun buildExtractJs(isExplicitNote: Boolean): String = """
        (() => {
            try {
                const isExplicitNote = $isExplicitNote;

                // 1. Title
                let title = (document.title || '').replace(/ - 抖音${'$'}/, '').trim();
                const h1 = document.querySelector('h1, [data-e2e="detail-title"], .title');
                if (h1 && h1.innerText && h1.innerText.trim().length > 0) {
                    title = h1.innerText.trim();
                }

                // 2. Author
                let author = '';
                const authorEl = document.querySelector('[data-e2e="user-info"] a, [data-e2e="user-title"], .account-name, .author-name, a[href*="/user/"]');
                if (authorEl && authorEl.innerText) {
                    author = authorEl.innerText.trim();
                }

                // 3. Avatar
                let avatar = '';
                const avatarEl = document.querySelector('img[src*="aweme-avatar"], img[src*="tos-cn-avt"]');
                if (avatarEl && avatarEl.src) {
                    avatar = avatarEl.src;
                }

                // 4. Expected slides indicator count e.g. "1/4", "2 / 4", "1 \u00a0 / \u00a0 4", "1 · / · 8"
                let totalExpected = 0;
                const indEls = document.querySelectorAll('[class*="indicator"], [class*="progress"], [class*="pagination"], [data-e2e*="indicator"], .slider-video *, main *');
                for (const el of indEls) {
                    const txt = el.innerText || '';
                    const m = txt.match(/(\d+)\s*[\u00b7\s]*[\/|\\][\u00b7\s]*(\d+)/);
                    if (m && parseInt(m[2], 10) > 1 && parseInt(m[2], 10) <= 100) {
                        totalExpected = parseInt(m[2], 10);
                        break;
                    }
                }
                if (totalExpected === 0) {
                    const bodyText = document.body.innerText || '';
                    const m = bodyText.match(/(?:^|\s|\n|[^\d])(\d{1,2})\s*[\u00b7\s]*[\/|\\][\u00b7\s]*(\d{1,2})(?:${'$'}|\s|\n|[^\d])/);
                    if (m && parseInt(m[2], 10) > 1 && parseInt(m[2], 10) <= 100) {
                        totalExpected = parseInt(m[2], 10);
                    }
                }
                if (totalExpected === 0) {
                    const pageBtns = document.querySelectorAll('main button[class*="QAUHVhVM"]');
                    if (pageBtns && pageBtns.length > 1) {
                        totalExpected = pageBtns.length;
                    }
                }

                // 5. Active Slide Progression: Cycle through slides on every poll!
                try {
                    // (a) Dispatch ArrowRight keyboard event to document, window, and player
                    ['keydown', 'keyup'].forEach(type => {
                        const evt = new KeyboardEvent(type, {
                            key: 'ArrowRight',
                            code: 'ArrowRight',
                            keyCode: 39,
                            which: 39,
                            bubbles: true,
                            cancelable: true
                        });
                        document.dispatchEvent(evt);
                        window.dispatchEvent(evt);
                        document.body.dispatchEvent(evt);
                        const p = document.querySelector('.slider-video, .basePlayerContainer, [class*="swiper"], [class*="slider"]');
                        if (p) p.dispatchEvent(evt);
                    });

                    // (b) Click all next / right arrow buttons in the slider container
                    const nextBtns = document.querySelectorAll('.slider-video button, .basePlayerContainer button, [class*="arrow"], [class*="next"], [class*="switch"], [class*="slide-btn"]');
                    for (const btn of nextBtns) {
                        const cls = ((btn.className || '') + ' ' + (btn.getAttribute('aria-label') || '')).toLowerCase();
                        if (cls.includes('right') || cls.includes('next') || cls.includes('after') || cls.includes('arrow')) {
                            try { btn.click(); } catch(e) {}
                        }
                    }

                    // (c) Click pagination step items / buttons
                    const pageBtns = document.querySelectorAll('main button[class*="QAUHVhVM"], [class*="indicator"] span, [class*="indicator"] [class*="dot"], [class*="pagination"] *');
                    for (const dot of pageBtns) {
                        try { dot.click(); } catch(e) {}
                    }
                } catch(e) {}

                // 6. Genuine Aweme note slides
                const candidateEls = Array.from(document.querySelectorAll('.basePlayerContainer img, .slider-video img, .dySwiperSlide img, [class*="basePlayer"] img, [class*="swiper"] img, [class*="slider"] img, main img'));

                const images = [];
                const seenTos = new Set();
                for (const img of candidateEls) {
                    const src = img.src || '';
                    if (!src.includes('douyinpic.com') && !src.includes('bytecdn') && !src.includes('byteimg')) continue;

                    // Blacklist
                    if (src.includes('avatar') || src.includes('tos-cn-avt') || src.includes('100x100')) continue;
                    if (src.includes('emoji') || src.includes('sticker') || src.includes('tos-cn-i-tsj2')) continue;
                    const alt = img.getAttribute('alt') || '';
                    if (/^\[.+\]${'$'}/.test(alt.trim())) continue;
                    if (src.includes('image-cut-tos') || src.includes('tplv-dy-resize') || src.includes('pcweb_cover') || src.includes('related_item')) continue;

                    // Whitelist: Must match genuine Douyin note slide parameters
                    const isAwemeSlide = src.includes('tplv-dy-aweme-images') ||
                                         src.includes('biz_tag=aweme_images') ||
                                         src.includes('PackSourceEnum_AWEME_DETAIL') ||
                                         src.includes('tos-cn-i-0813') ||
                                         src.includes('~tplv-dy');

                    if (isAwemeSlide) {
                        // Match unique image asset file ID across all buckets (tos-cn-i-XXXX/FILE_ID)
                        const m = src.match(/tos-cn-[^/?#]+\/([a-zA-Z0-9_-]+)/);
                        const key = m ? m[0] : src.split('?')[0];
                        if (!seenTos.has(key)) {
                            seenTos.add(key);
                            images.push(src);
                        }
                    }
                }

                // 7. Video extraction
                let videoUrl = '';
                if (!isExplicitNote) {
                    const videoEl = document.querySelector('.basePlayerContainer video, [data-e2e="feed-video"] video');
                    if (videoEl && !videoEl.closest('.hideXgVideo') && !videoEl.closest('[class*="recommend"]')) {
                        const vSrc = videoEl.currentSrc || videoEl.src || '';
                        if (vSrc.startsWith('http') && !vSrc.includes('empty')) {
                            videoUrl = vSrc;
                        }
                    }
                }

                return JSON.stringify({
                    title: title,
                    author: author,
                    avatar: avatar,
                    images: images,
                    videoUrl: videoUrl,
                    totalExpected: totalExpected
                });
            } catch(e) {
                return JSON.stringify({ error: e.toString() });
            }
        })()
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolve(
        context: Context,
        targetUrl: String,
        itemId: String = "",
        customCookie: String = "",
        isExplicitNote: Boolean = false
    ): Result<NoteDetail> = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val isCompleted = AtomicBoolean(false)
            val handler = Handler(Looper.getMainLooper())
            var webView: WebView? = null
            val startTime = System.currentTimeMillis()

            val accumulatedImages = LinkedHashSet<String>()
            var bestTitle = ""
            var bestAuthor = ""
            var bestAvatar = ""
            var bestVideo = ""
            var expectedCount = 0

            fun cleanup() {
                try {
                    handler.removeCallbacksAndMessages(null)
                    webView?.stopLoading()
                    webView?.destroy()
                    webView = null
                } catch (_: Exception) {}
            }

            continuation.invokeOnCancellation {
                cleanup()
            }

            fun finishSuccess(detail: NoteDetail) {
                if (isCompleted.compareAndSet(false, true)) {
                    cleanup()
                    continuation.resume(Result.success(detail))
                }
            }

            fun finishError(message: String) {
                if (isCompleted.compareAndSet(false, true)) {
                    cleanup()
                    continuation.resume(Result.failure(IllegalStateException(message)))
                }
            }

            // Set up timeout (9 seconds)
            handler.postDelayed({
                val hasImages = accumulatedImages.isNotEmpty()
                val hasVid = !isExplicitNote && bestVideo.isNotBlank()
                if (hasImages || hasVid) {
                    val isVideo = hasVid && !hasImages
                    val detail = NoteDetail(
                        id = itemId.ifBlank { "douyin_${System.currentTimeMillis()}" },
                        title = bestTitle.ifBlank { "UNTITLED DOUYIN WORK · 抖音作品" },
                        desc = bestTitle.ifBlank { "UNTITLED DOUYIN WORK · 抖音作品" },
                        authorName = bestAuthor.ifBlank { "DOUYIN CREATOR" },
                        authorAvatar = bestAvatar,
                        platform = Platform.DOUYIN,
                        type = if (isVideo) NoteType.VIDEO else NoteType.IMAGE,
                        images = accumulatedImages.toList(),
                        videoUrl = if (isVideo) bestVideo else null,
                        shareUrl = targetUrl,
                        tags = emptyList()
                    )
                    finishSuccess(detail)
                } else {
                    finishError("DOUYIN EXTRACTION TIMEOUT · 页面解析超时，请检查网络或作品状态")
                }
            }, 9000)

            try {
                val wv = WebView(context.applicationContext)
                webView = wv

                wv.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    userAgentString = DESKTOP_UA
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    loadsImagesAutomatically = true
                    blockNetworkImage = false
                }

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(wv, true)
                if (customCookie.isNotBlank()) {
                    cookieManager.setCookie("https://www.douyin.com", customCookie)
                }

                val extractJs = buildExtractJs(isExplicitNote)

                fun evaluatePage() {
                    if (isCompleted.get()) return
                    wv.evaluateJavascript(extractJs) { resultJson ->
                        if (isCompleted.get() || resultJson.isNullOrBlank() || resultJson == "null") return@evaluateJavascript
                        try {
                            val unescaped = if (resultJson.startsWith("\"") && resultJson.endsWith("\"")) {
                                org.json.JSONTokener(resultJson).nextValue().toString()
                            } else {
                                resultJson
                            }
                            val obj = JSONObject(unescaped)
                            val imagesArr = obj.optJSONArray("images")
                            val videoUrl = if (!isExplicitNote) obj.optString("videoUrl", "") else ""
                            val totalExpected = obj.optInt("totalExpected", 0)
                            if (totalExpected > 0 && expectedCount == 0) {
                                expectedCount = totalExpected
                            }

                            val title = obj.optString("title", "")
                            if (title.isNotBlank() && title != "抖音" && bestTitle.isBlank()) {
                                bestTitle = title
                            }
                            val author = obj.optString("author", "")
                            if (author.isNotBlank() && bestAuthor.isBlank()) {
                                bestAuthor = author
                            }
                            val avatar = obj.optString("avatar", "")
                            if (avatar.isNotBlank() && bestAvatar.isBlank()) {
                                bestAvatar = avatar
                            }
                            if (videoUrl.isNotBlank() && bestVideo.isBlank() && !isExplicitNote) {
                                bestVideo = videoUrl
                            }

                            if (imagesArr != null) {
                                for (i in 0 until imagesArr.length()) {
                                    val s = imagesArr.optString(i)
                                    if (s.isNotBlank()) {
                                        accumulatedImages.add(s)
                                    }
                                }
                            }

                            val elapsedMs = System.currentTimeMillis() - startTime

                            // Completion conditions:
                            // 1. All expected slides have been found (e.g. expected 4, found >= 4)
                            val hasAllExpected = expectedCount > 0 && accumulatedImages.size >= expectedCount

                            // 2. Timed slides collection:
                            // - If expectedCount > 0, give at least 5.5s to gather all slides before timing out
                            // - If expectedCount == 0, give at least 3.5s of swiping before accepting accumulated images
                            val hasTimedImages = (expectedCount > 0 && accumulatedImages.isNotEmpty() && elapsedMs > 5500) ||
                                                 (expectedCount == 0 && accumulatedImages.isNotEmpty() && elapsedMs > 3500)

                            // 3. True video post (non-note post, verified video, empty images)
                            val hasVideo = !isExplicitNote && bestVideo.isNotBlank() && accumulatedImages.isEmpty() && elapsedMs > 3000

                            if (hasAllExpected || hasTimedImages || hasVideo) {
                                val isVideo = !isExplicitNote && bestVideo.isNotBlank() && accumulatedImages.isEmpty()
                                val finalTitle = bestTitle.ifBlank { "UNTITLED DOUYIN WORK · 抖音作品" }
                                val finalAuthor = bestAuthor.ifBlank { "DOUYIN CREATOR" }

                                val detail = NoteDetail(
                                    id = itemId.ifBlank { "douyin_${System.currentTimeMillis()}" },
                                    title = finalTitle,
                                    desc = finalTitle,
                                    authorName = finalAuthor,
                                    authorAvatar = bestAvatar,
                                    platform = Platform.DOUYIN,
                                    type = if (isVideo) NoteType.VIDEO else NoteType.IMAGE,
                                    images = accumulatedImages.toList(),
                                    videoUrl = if (isVideo) bestVideo else null,
                                    shareUrl = targetUrl,
                                    tags = emptyList()
                                )
                                finishSuccess(detail)
                            }
                        } catch (_: Exception) {}
                    }
                }

                val pollRunnable = object : Runnable {
                    override fun run() {
                        if (!isCompleted.get()) {
                            evaluatePage()
                            handler.postDelayed(this, 350)
                        }
                    }
                }

                wv.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        handler.post(pollRunnable)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        evaluatePage()
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                    }
                }

                wv.loadUrl(targetUrl)

            } catch (e: Exception) {
                finishError("FAILED TO INITIALIZE WEBVIEW: ${e.message}")
            }
        }
    }
}
