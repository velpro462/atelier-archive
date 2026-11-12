package com.atelier.archive.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atelier.archive.data.HistoryManager
import com.atelier.archive.model.DownloadState
import com.atelier.archive.model.HistoryItem
import com.atelier.archive.model.NoteType
import com.atelier.archive.model.ParseState
import com.atelier.archive.network.ArchiveDispatcher
import com.atelier.archive.network.MediaDownloader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _inputText = MutableStateFlow("")
    val inputText = _inputText.asStateFlow()

    private val _parseState = MutableStateFlow<ParseState>(ParseState.Idle)
    val parseState = _parseState.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState = _downloadState.asStateFlow()

    private val _customCookie = MutableStateFlow("")
    val customCookie = _customCookie.asStateFlow()

    private val _detectedClipboardUrl = MutableStateFlow<String?>(null)
    val detectedClipboardUrl = _detectedClipboardUrl.asStateFlow()

    private val _inspectMedia = MutableStateFlow<Pair<String, Boolean>?>(null)
    val inspectMedia = _inspectMedia.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen = _isSettingsOpen.asStateFlow()

    private val _isHistoryOpen = MutableStateFlow(false)
    val isHistoryOpen = _isHistoryOpen.asStateFlow()

    private val _historyList = MutableStateFlow<List<HistoryItem>>(emptyList())
    val historyList = _historyList.asStateFlow()

    fun updateInput(text: String) {
        _inputText.value = text
    }

    fun clearInput() {
        _inputText.value = ""
        _parseState.value = ParseState.Idle
        _downloadState.value = DownloadState.Idle
    }

    fun onClipboardDetected(url: String) {
        if (url != _inputText.value) {
            _detectedClipboardUrl.value = url
        }
    }

    fun dismissClipboard() {
        _detectedClipboardUrl.value = null
    }

    fun openSettings() {
        _isSettingsOpen.value = true
    }

    fun closeSettings() {
        _isSettingsOpen.value = false
    }

    fun openHistory() {
        _historyList.value = HistoryManager.getHistory(getApplication())
        _isHistoryOpen.value = true
    }

    fun closeHistory() {
        _isHistoryOpen.value = false
    }

    fun deleteHistoryItem(id: String) {
        HistoryManager.deleteRecord(getApplication(), id)
        _historyList.value = HistoryManager.getHistory(getApplication())
    }

    fun clearAllHistory() {
        HistoryManager.clearHistory(getApplication())
        _historyList.value = emptyList()
    }

    fun selectHistoryItem(item: HistoryItem) {
        _isHistoryOpen.value = false
        updateInput(item.shareUrl)
        parseUrl(item.shareUrl)
    }

    fun saveCookie(cookie: String) {
        _customCookie.value = cookie
    }

    fun openInspect(url: String, isVideo: Boolean) {
        _inspectMedia.value = Pair(url, isVideo)
    }

    fun closeInspect() {
        _inspectMedia.value = null
    }

    fun parseUrl(overrideText: String? = null) {
        val target = overrideText ?: _inputText.value
        if (target.isBlank()) return

        _parseState.value = ParseState.Parsing
        _downloadState.value = DownloadState.Idle
        _detectedClipboardUrl.value = null

        viewModelScope.launch {
            val result = ArchiveDispatcher.parse(target, _customCookie.value, getApplication())
            result.onSuccess { note ->
                _parseState.value = ParseState.Success(note)
                HistoryManager.addRecord(getApplication(), note)
            }.onFailure { error ->
                _parseState.value = ParseState.Error(
                    error.localizedMessage ?: "FAILED TO EXTRACT NOTE FROM ARCHIVE"
                )
            }
        }
    }

    fun downloadSingleImage(context: Context, pageIndex: Int) {
        val state = _parseState.value
        if (state !is ParseState.Success) return

        val note = state.note
        if (pageIndex !in note.images.indices) return

        _downloadState.value = DownloadState.Progress(
            current = 0,
            total = 1,
            bytesDownloaded = 0L,
            totalBytes = 0L,
            percent = 0f
        )

        viewModelScope.launch {
            val result = MediaDownloader.downloadSingleImage(
                context = context,
                noteId = note.id,
                platform = note.platform,
                url = note.images[pageIndex],
                pageIndex = pageIndex,
                onProgress = { bytesRead, totalBytes, percent ->
                    _downloadState.value = DownloadState.Progress(
                        current = 0,
                        total = 1,
                        bytesDownloaded = bytesRead,
                        totalBytes = totalBytes,
                        percent = percent
                    )
                }
            )
            result.onSuccess {
                _downloadState.value = DownloadState.Success(1)
            }.onFailure {
                _downloadState.value = DownloadState.Error(it.localizedMessage ?: "DOWNLOAD FAILED")
            }
        }
    }

    fun downloadCurrentMedia(context: Context) {
        val state = _parseState.value
        if (state !is ParseState.Success) return

        val note = state.note
        val isVideo = note.type == NoteType.VIDEO

        _downloadState.value = DownloadState.Progress(
            current = 0,
            total = if (isVideo) 1 else note.images.size,
            bytesDownloaded = 0L,
            totalBytes = 0L,
            percent = 0f
        )

        viewModelScope.launch {
            if (isVideo) {
                val videoUrl = note.videoUrl
                if (videoUrl.isNullOrBlank()) {
                    _downloadState.value = DownloadState.Error("NO VALID VIDEO URL")
                    return@launch
                }
                val result = MediaDownloader.downloadVideo(
                    context = context,
                    noteId = note.id,
                    platform = note.platform,
                    url = videoUrl,
                    onProgress = { bytesRead, totalBytes, percent ->
                        _downloadState.value = DownloadState.Progress(
                            current = 0,
                            total = 1,
                            bytesDownloaded = bytesRead,
                            totalBytes = totalBytes,
                            percent = percent
                        )
                    }
                )
                result.onSuccess {
                    _downloadState.value = DownloadState.Success(1)
                }.onFailure {
                    _downloadState.value = DownloadState.Error(it.localizedMessage ?: "DOWNLOAD FAILED")
                }
            } else {
                val result = MediaDownloader.downloadImages(
                    context = context,
                    noteId = note.id,
                    platform = note.platform,
                    urls = note.images,
                    onProgress = { cur, tot, pct ->
                        _downloadState.value = DownloadState.Progress(
                            current = cur,
                            total = tot,
                            bytesDownloaded = 0L,
                            totalBytes = 0L,
                            percent = pct
                        )
                    }
                )
                result.onSuccess { count ->
                    _downloadState.value = DownloadState.Success(count)
                }.onFailure {
                    _downloadState.value = DownloadState.Error(it.localizedMessage ?: "DOWNLOAD FAILED")
                }
            }
        }
    }
}
