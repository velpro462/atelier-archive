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
    fun addRecord(context: Context, note: NoteDetail) {
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
            timestamp = System.currentTimeMillis()
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
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
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
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("authorName", item.authorName)
                put("authorAvatar", item.authorAvatar)
                put("platform", item.platform.code)
                put("type", item.type.name)
                put("coverUrl", item.coverUrl)
                put("shareUrl", item.shareUrl)
                put("itemCount", item.itemCount)
                put("timestamp", item.timestamp)
            }
            arr.put(obj)
        }
        getPrefs(context).edit().putString(KEY_HISTORY, arr.toString()).apply()
    }
}
