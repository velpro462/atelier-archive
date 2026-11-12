package com.atelier.archive.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atelier.archive.model.DownloadState
import com.atelier.archive.model.NoteType
import com.atelier.archive.model.ParseState
import com.atelier.archive.network.ArchiveDispatcher
import com.atelier.archive.network.MediaDownloader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

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
            val result = ArchiveDispatcher.parse(target, _customCookie.value)
            result.onSuccess { note ->
                _parseState.value = ParseState.Success(note)
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

        _downloadState.value = DownloadState.Progress(0, 1)

        viewModelScope.launch {
            val result = MediaDownloader.downloadSingleImage(
                context = context,
                noteId = note.id,
                platform = note.platform,
                url = note.images[pageIndex],
                pageIndex = pageIndex,
                onProgress = { cur, tot ->
                    _downloadState.value = DownloadState.Progress(cur, tot)
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
        _downloadState.value = DownloadState.Progress(0, if (note.type == NoteType.VIDEO) 1 else note.images.size)

        viewModelScope.launch {
            if (note.type == NoteType.VIDEO) {
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
                    onProgress = { cur, tot ->
                        _downloadState.value = DownloadState.Progress(cur, tot)
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
                    onProgress = { cur, tot ->
                        _downloadState.value = DownloadState.Progress(cur, tot)
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
