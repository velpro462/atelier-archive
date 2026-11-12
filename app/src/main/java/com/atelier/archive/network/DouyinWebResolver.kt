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

    private const val EXTRACT_JS = """
        (() => {
            try {
                // Title
                let title = (document.title || '').replace(/ - 抖音${'$'}/, '').trim();
                const h1 = document.querySelector('h1');
                if (h1 && h1.innerText && h1.innerText.trim().length > 0) {
                    title = h1.innerText.trim();
                }

                // Author
                let author = '';
                const authorEl = document.querySelector('[data-e2e="user-title"], [data-e2e="user-info"] a, .account-name, .author-name');
                if (authorEl && authorEl.innerText) {
                    author = authorEl.innerText.trim();
                }

                // Avatar
                let avatar = '';
                const avatarEl = document.querySelector('img[src*="avatar"]');
                if (avatarEl && avatarEl.src) {
                    avatar = avatarEl.src;
                }

                // Images (High-Res CDN images, excluding UI icons & avatars)
                const imgEls = Array.from(document.querySelectorAll('img'));
                const images = [];
                const seenTos = new Set();
                for (const img of imgEls) {
                    const src = img.src || '';
                    if (src.includes('douyinpic.com') && src.includes('tos-cn') && !src.includes('avatar') && !src.includes('100x100')) {
                        const m = src.match(/tos-cn[^\/~?]+/);
                        const key = m ? m[0] : src;
                        if (!seenTos.has(key)) {
                            seenTos.add(key);
                            images.push(src);
                        }
                    }
                }

                // Video
                let videoUrl = '';
                const videoEl = document.querySelector('video');
                if (videoEl) {
                    videoUrl = videoEl.currentSrc || videoEl.src || '';
                }

                return JSON.stringify({
                    title: title,
                    author: author,
                    avatar: avatar,
                    images: images,
                    videoUrl: videoUrl,
                    pageReady: (images.length > 0 || videoUrl.length > 0)
                });
            } catch(e) {
                return JSON.stringify({ error: e.toString(), pageReady: false });
            }
        })()
    """

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolve(
        context: Context,
        targetUrl: String,
        itemId: String = "",
        customCookie: String = ""
    ): Result<NoteDetail> = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val isCompleted = AtomicBoolean(false)
            val handler = Handler(Looper.getMainLooper())
            var webView: WebView? = null

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

            // Set up timeout (8 seconds)
            handler.postDelayed({
                finishError("DOUYIN EXTRACTION TIMEOUT · 页面解析超时，请检查网络或作品状态")
            }, 8000)

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

                fun evaluatePage() {
                    if (isCompleted.get()) return
                    wv.evaluateJavascript(EXTRACT_JS) { resultJson ->
                        if (isCompleted.get() || resultJson.isNullOrBlank() || resultJson == "null") return@evaluateJavascript
                        try {
                            val unescaped = if (resultJson.startsWith("\"") && resultJson.endsWith("\"")) {
                                org.json.JSONTokener(resultJson).nextValue().toString()
                            } else {
                                resultJson
                            }
                            val obj = JSONObject(unescaped)
                            val pageReady = obj.optBoolean("pageReady", false)
                            val imagesArr = obj.optJSONArray("images")
                            val videoUrl = obj.optString("videoUrl", "")

                            val imgList = mutableListOf<String>()
                            if (imagesArr != null) {
                                for (i in 0 until imagesArr.length()) {
                                    val s = imagesArr.optString(i)
                                    if (s.isNotBlank() && !imgList.contains(s)) {
                                        imgList.add(s)
                                    }
                                }
                            }

                            if (pageReady && (imgList.isNotEmpty() || videoUrl.isNotBlank())) {
                                val isVideo = videoUrl.isNotBlank() && imgList.size <= 1
                                val title = obj.optString("title", "").ifBlank { "UNTITLED DOUYIN WORK · 抖音作品" }
                                val author = obj.optString("author", "").ifBlank { "DOUYIN CREATOR" }
                                val avatar = obj.optString("avatar", "")

                                val detail = NoteDetail(
                                    id = itemId.ifBlank { "douyin_${System.currentTimeMillis()}" },
                                    title = title,
                                    desc = title,
                                    authorName = author,
                                    authorAvatar = avatar,
                                    platform = Platform.DOUYIN,
                                    type = if (isVideo) NoteType.VIDEO else NoteType.IMAGE,
                                    images = imgList,
                                    videoUrl = if (isVideo) videoUrl else null,
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
