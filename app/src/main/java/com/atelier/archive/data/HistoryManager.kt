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

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun addRecord(context: Context, note: NoteDetail, originalInput: String = "") {
        val list = getHistory(context).toMutableList()
        list.removeAll { it.id == note.id || it.shareUrl == note.shareUrl }

        val cover = note.images.firstOrNull().orEmpty()
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
        val jsonStr = getPrefs(context).getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                val platform = parsePlatform(obj.optString("platform", "XHS"))
                val type = if (obj.optString("type", "IMAGE") == "VIDEO") NoteType.VIDEO else NoteType.IMAGE
                val cachedNote = obj.optJSONObject("cachedNote")?.let { parseCachedNote(it, obj) }

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
            }
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
                put("originalInput", item.originalInput)
                if (item.cachedNote != null) {
                    put("cachedNote", serializeNote(item.cachedNote))
                }
            }
            arr.put(obj)
        }
        getPrefs(context).edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    private fun parsePlatform(code: String): Platform =
        if (code == "DY" || code.contains("DOUYIN")) Platform.DOUYIN else Platform.XIAOHONGSHU

    private fun parseCachedNote(noteObj: JSONObject, parentObj: JSONObject): NoteDetail {
        val notePlatform = parsePlatform(noteObj.optString("platform", parentObj.optString("platform", "XHS")))
        val noteType = if (noteObj.optString("type", "IMAGE") == "VIDEO") NoteType.VIDEO else NoteType.IMAGE
        val videoUrl = noteObj.optString("videoUrl").takeIf { it.isNotBlank() && it != "null" }

        return NoteDetail(
            id = noteObj.optString("id", parentObj.optString("id")),
            title = noteObj.optString("title", parentObj.optString("title")),
            desc = noteObj.optString("desc", parentObj.optString("title")),
            authorName = noteObj.optString("authorName", parentObj.optString("authorName")),
            authorAvatar = noteObj.optString("authorAvatar", parentObj.optString("authorAvatar")),
            platform = notePlatform,
            type = noteType,
            images = jsonArrayToStringList(noteObj.optJSONArray("images")),
            videoUrl = videoUrl,
            shareUrl = noteObj.optString("shareUrl", parentObj.optString("shareUrl")),
            tags = jsonArrayToStringList(noteObj.optJSONArray("tags"))
        )
    }

    private fun serializeNote(n: NoteDetail): JSONObject = JSONObject().apply {
        put("id", n.id)
        put("title", n.title)
        put("desc", n.desc)
        put("authorName", n.authorName)
        put("authorAvatar", n.authorAvatar)
        put("platform", n.platform.code)
        put("type", n.type.name)
        put("shareUrl", n.shareUrl)
        if (n.videoUrl != null) put("videoUrl", n.videoUrl)
        put("images", stringListToJsonArray(n.images))
        put("tags", stringListToJsonArray(n.tags))
    }

    private fun jsonArrayToStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }
    }

    private fun stringListToJsonArray(list: List<String>): JSONArray {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr
    }
}
