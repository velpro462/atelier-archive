package com.atelier.archive.network

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.atelier.archive.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

object MediaDownloader {

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun downloadImages(
        context: Context,
        noteId: String,
        platform: Platform,
        urls: List<String>,
        onProgress: (current: Int, total: Int, percent: Float) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var successCount = 0
            val total = urls.size
            val referer = if (platform == Platform.XIAOHONGSHU) "https://www.xiaohongshu.com/" else "https://www.douyin.com/"

            urls.forEachIndexed { index, url ->
                val isWebp = url.contains(".webp", ignoreCase = true)
                val ext = if (isWebp) "webp" else "jpg"
                val mime = if (isWebp) "image/webp" else "image/jpeg"
                val fileName = "ARCHIVE_${platform.code}_${noteId}_${index + 1}_${System.currentTimeMillis()}.$ext"
                val success = downloadStreamToGallery(
                    context = context,
                    url = url,
                    referer = referer,
                    fileName = fileName,
                    mimeType = mime,
                    isImage = true
                )
                if (success) {
                    successCount++
                }
                val pct = ((index + 1).toFloat() / total).coerceIn(0f, 1f)
                onProgress(index + 1, total, pct)
            }

            if (successCount == 0 && urls.isNotEmpty()) {
                throw IllegalStateException("FAILED TO ARCHIVE IMAGES TO GALLERY")
            }
            successCount
        }
    }

    suspend fun downloadSingleImage(
        context: Context,
        noteId: String,
        platform: Platform,
        url: String,
        pageIndex: Int,
        onProgress: (bytesRead: Long, totalBytes: Long, percent: Float) -> Unit
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(0L, 100L, 0f)
            val referer = if (platform == Platform.XIAOHONGSHU) "https://www.xiaohongshu.com/" else "https://www.douyin.com/"
            val isWebp = url.contains(".webp", ignoreCase = true)
            val ext = if (isWebp) "webp" else "jpg"
            val mime = if (isWebp) "image/webp" else "image/jpeg"
            val fileName = "ARCHIVE_${platform.code}_${noteId}_p${pageIndex + 1}_${System.currentTimeMillis()}.$ext"
            val success = downloadStreamToGallery(
                context = context,
                url = url,
                referer = referer,
                fileName = fileName,
                mimeType = mime,
                isImage = true,
                onByteProgress = { read, total ->
                    val pct = if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else 0f
                    onProgress(read, total, pct)
                }
            )
            onProgress(100L, 100L, 1f)
            if (!success) {
                throw IllegalStateException("FAILED TO ARCHIVE IMAGE TO GALLERY")
            }
            true
        }
    }

    suspend fun downloadVideo(
        context: Context,
        noteId: String,
        platform: Platform,
        url: String,
        onProgress: (bytesRead: Long, totalBytes: Long, percent: Float) -> Unit
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(0L, 100L, 0f)
            val referer = if (platform == Platform.XIAOHONGSHU) "https://www.xiaohongshu.com/" else "https://www.douyin.com/"
            val fileName = "ARCHIVE_VIDEO_${platform.code}_${noteId}_${System.currentTimeMillis()}.mp4"
            val success = downloadStreamToGallery(
                context = context,
                url = url,
                referer = referer,
                fileName = fileName,
                mimeType = "video/mp4",
                isImage = false,
                onByteProgress = { read, total ->
                    val pct = if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else 0f
                    onProgress(read, total, pct)
                }
            )
            onProgress(100L, 100L, 1f)
            if (!success) {
                throw IllegalStateException("FAILED TO ARCHIVE VIDEO TO STORAGE")
            }
            true
        }
    }

    private fun downloadStreamToGallery(
        context: Context,
        url: String,
        referer: String,
        fileName: String,
        mimeType: String,
        isImage: Boolean,
        onByteProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): Boolean {
        val request = Request.Builder()
            .url(url)
            .header("Referer", referer)
            .header("User-Agent", UA)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return false

        val body = response.body ?: return false
        val totalBytes = body.contentLength()
        val resolver = context.contentResolver

        val collectionUri: Uri = if (isImage) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        }

        val relativePath = if (isImage) {
            "${Environment.DIRECTORY_PICTURES}/AtelierArchive"
        } else {
            "${Environment.DIRECTORY_MOVIES}/AtelierArchive"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val itemUri = resolver.insert(collectionUri, values) ?: return false

        return try {
            resolver.openOutputStream(itemUri)?.use { outputStream: OutputStream ->
                body.byteStream().use { inputStream: InputStream ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    var totalRead: Long = 0
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        onByteProgress?.invoke(totalRead, totalBytes)
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
            }
            true
        } catch (e: Exception) {
            resolver.delete(itemUri, null, null)
            false
        }
    }
}
