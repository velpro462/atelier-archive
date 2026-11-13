package com.atelier.archive.data

import android.content.Context
import android.content.SharedPreferences
import com.atelier.archive.model.HistoryItem
import com.atelier.archive.model.NoteDetail
import com.atelier.archive.model.NoteType
import com.atelier.archive.model.Platform
import org.json.JSONArray
import org.json.JSONObject

object HistoryManager {

    private const val PREF_NAME = "atelier_history_prefs"
    private const val KEY_HISTORY = "history_records_json"
    private const val MAX_RECORDS = 100

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun addRecord(context: Context, note: NoteDetail, originalInput: String = "") {
        val list = getHistory(context).toMutableList()
        // Remove existing item with same id or shareUrl if present
        list.removeAll { it.id == note.id || it.shareUrl == note.shareUrl }

        val cover = if (note.images.isNotEmpty()) {
            note.images[0]
        } else ""

        val count = if (note.type == NoteType.VIDEO) 1 else note.images.size

        val newItem = HistoryItem(
            id = note.id,
            title = note.title,
            authorName = note.authorName,
            authorAvatar = note.authorAvatar,
            platform = note.platform,
            type = note.type,
            coverUrl = cover,
            shareUrl = note.shareUrl,
            itemCount = count,
            timestamp = System.currentTimeMillis(),
            originalInput = originalInput,
            cachedNote = note
        )

        list.add(0, newItem)
        if (list.size > MAX_RECORDS) {
            list.subList(MAX_RECORDS, list.size).clear()
        }

        saveList(context, list)
    }

    @Synchronized
    fun getHistory(context: Context): List<HistoryItem> {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val list = mutableListOf<HistoryItem>()

        return try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val platformStr = obj.optString("platform", "XHS")
                val platform = if (platformStr == "DY" || platformStr.contains("DOUYIN")) {
                    Platform.DOUYIN
                } else {
                    Platform.XIAOHONGSHU
                }

                val typeStr = obj.optString("type", "IMAGE")
                val type = if (typeStr == "VIDEO") NoteType.VIDEO else NoteType.IMAGE

                var cachedNote: NoteDetail? = null
                val noteObj = obj.optJSONObject("cachedNote")
                if (noteObj != null) {
                    val notePlatformStr = noteObj.optString("platform", platformStr)
                    val notePlatform = if (notePlatformStr == "DY" || notePlatformStr.contains("DOUYIN")) {
                        Platform.DOUYIN
                    } else {
                        Platform.XIAOHONGSHU
                    }
                    val noteTypeStr = noteObj.optString("type", typeStr)
                    val noteType = if (noteTypeStr == "VIDEO") NoteType.VIDEO else NoteType.IMAGE

                    val imagesList = mutableListOf<String>()
                    val imgsArr = noteObj.optJSONArray("images")
                    if (imgsArr != null) {
                        for (j in 0 until imgsArr.length()) {
                            val u = imgsArr.optString(j)
                            if (u.isNotBlank()) imagesList.add(u)
                        }
                    }

                    val tagsList = mutableListOf<String>()
                    val tagsArr = noteObj.optJSONArray("tags")
                    if (tagsArr != null) {
                        for (j in 0 until tagsArr.length()) {
                            tagsList.add(tagsArr.optString(j))
                        }
                    }

                    val videoUrl = if (noteObj.has("videoUrl") && !noteObj.isNull("videoUrl")) {
                        noteObj.getString("videoUrl")
                    } else null

                    cachedNote = NoteDetail(
                        id = noteObj.optString("id", obj.optString("id")),
                        title = noteObj.optString("title", obj.optString("title")),
                        desc = noteObj.optString("desc", obj.optString("title")),
                        authorName = noteObj.optString("authorName", obj.optString("authorName")),
                        authorAvatar = noteObj.optString("authorAvatar", obj.optString("authorAvatar")),
                        platform = notePlatform,
                        type = noteType,
                        images = imagesList,
                        videoUrl = videoUrl,
                        shareUrl = noteObj.optString("shareUrl", obj.optString("shareUrl")),
                        tags = tagsList
                    )
                }

                list.add(
                    HistoryItem(
                        id = obj.optString("id", ""),
                        title = obj.optString("title", ""),
                        authorName = obj.optString("authorName", ""),
                        authorAvatar = obj.optString("authorAvatar", ""),
                        platform = platform,
                        type = type,
                        coverUrl = obj.optString("coverUrl", ""),
                        shareUrl = obj.optString("shareUrl", ""),
                        itemCount = obj.optInt("itemCount", 1),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        originalInput = obj.optString("originalInput", ""),
                        cachedNote = cachedNote
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun deleteRecord(context: Context, id: String) {
        val list = getHistory(context).toMutableList()
        list.removeAll { it.id == id }
        saveList(context, list)
    }

    @Synchronized
    fun clearHistory(context: Context) {
        getPrefs(context).edit().remove(KEY_HISTORY).apply()
    }

    private fun saveList(context: Context, list: List<HistoryItem>) {
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("title", item.title)
            obj.put("authorName", item.authorName)
            obj.put("authorAvatar", item.authorAvatar)
            obj.put("platform", item.platform.code)
            obj.put("type", item.type.name)
            obj.put("coverUrl", item.coverUrl)
            obj.put("shareUrl", item.shareUrl)
            obj.put("itemCount", item.itemCount)
            obj.put("timestamp", item.timestamp)
            obj.put("originalInput", item.originalInput)

            if (item.cachedNote != null) {
                val n = item.cachedNote
                val noteObj = JSONObject()
                noteObj.put("id", n.id)
                noteObj.put("title", n.title)
                noteObj.put("desc", n.desc)
                noteObj.put("authorName", n.authorName)
                noteObj.put("authorAvatar", n.authorAvatar)
                noteObj.put("platform", n.platform.code)
                noteObj.put("type", n.type.name)
                noteObj.put("shareUrl", n.shareUrl)
                if (n.videoUrl != null) {
                    noteObj.put("videoUrl", n.videoUrl)
                }
                val imgsArr = JSONArray()
                for (img in n.images) {
                    imgsArr.put(img)
                }
                noteObj.put("images", imgsArr)
                val tagsArr = JSONArray()
                for (tag in n.tags) {
                    tagsArr.put(tag)
                }
                noteObj.put("tags", tagsArr)

                obj.put("cachedNote", noteObj)
            }

            arr.put(obj)
        }
        getPrefs(context).edit().putString(KEY_HISTORY, arr.toString()).apply()
    }
}
