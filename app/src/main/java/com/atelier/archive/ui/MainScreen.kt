package com.atelier.archive.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atelier.archive.model.NoteType
import com.atelier.archive.model.ParseState
import com.atelier.archive.ui.components.*
import com.atelier.archive.ui.theme.*

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val inputText by viewModel.inputText.collectAsState()
    val parseState by viewModel.parseState.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val detectedClipboardUrl by viewModel.detectedClipboardUrl.collectAsState()
    val inspectMedia by viewModel.inspectMedia.collectAsState()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsState()
    val customCookie by viewModel.customCookie.collectAsState()

    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ParchmentBase)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 40.dp)
        ) {
            // Masthead
            EditorialHeader(
                onOpenSettings = { viewModel.openSettings() }
            )

            // Utility Bar (Settings & Monogram)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "COLLECTION N° 2026 · DUAL PLATFORM",
                    style = EditorialTypography.labelSmall,
                    color = InkMuted
                )

                Row(
                    modifier = Modifier
                        .clickable {
                            android.widget.Toast.makeText(context, "提示：普通情况不用配置这个东西，直接粘贴链接提取即可", android.widget.Toast.LENGTH_LONG).show()
                            viewModel.openSettings()
                        }
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = "Settings",
                        tint = DeepGold,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "凭证配置",
                        style = EditorialTypography.labelSmall,
                        color = DeepGold
                    )
                }
            }

            // Clipboard Detection Banner
            if (detectedClipboardUrl != null) {
                ClipboardNoticeBanner(
                    visible = true,
                    detectedUrl = detectedClipboardUrl ?: "",
                    onParseNow = {
                        viewModel.updateInput(detectedClipboardUrl ?: "")
                        viewModel.parseUrl(detectedClipboardUrl)
                    },
                    onDismiss = { viewModel.dismissClipboard() },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Input Monolith
            ParchmentInputSection(
                inputUrl = inputText,
                onUrlChange = { viewModel.updateInput(it) },
                onPasteClicked = {
                    val clip = clipboardManager.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val pasteText = clip.getItemAt(0).text?.toString() ?: ""
                        viewModel.updateInput(pasteText)
                    }
                },
                onClearClicked = { viewModel.clearInput() },
                onParseClicked = { viewModel.parseUrl() },
                isParsing = parseState is ParseState.Parsing,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Parsing Result / Error / Empty States
            when (val state = parseState) {
                is ParseState.Idle -> {
                    // Refined Empty State Callout
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "❖",
                            fontSize = 20.sp,
                            color = ChampagneGold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "PRESERVE ARTISTRY · 无水印原画双域收藏",
                            style = EditorialTypography.labelLarge,
                            color = InkSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "现已无缝兼容 小红书 & 抖音 双平台内容。\n支持从客户端点击「分享」直达，或复制链接后一键提取无水印图集与原画视频。",
                            style = EditorialTypography.bodyMedium,
                            color = InkMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is ParseState.Parsing -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .background(ParchmentCard)
                            .border(0.8.dp, HairlineBorder, RoundedCornerShape(2.dp))
                            .padding(30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = ChampagneGold,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "DECIPHERING & RETRIEVING LOSSLESS ASSETS…",
                                style = EditorialTypography.labelMedium,
                                color = DeepGold
                            )
                        }
                    }
                }

                is ParseState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .background(ParchmentCard)
                            .border(0.8.dp, WaxSealBurgundy.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                            .padding(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "✕",
                                fontSize = 14.sp,
                                color = WaxSealBurgundy
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DECODING ERROR / 解析受阻",
                                style = EditorialTypography.labelLarge,
                                color = WaxSealBurgundy
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            style = EditorialTypography.bodyMedium,
                            color = InkSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "建议：检查分享链接是否完整（支持小红书与抖音分享口令），若作品受限可在右上角 PREFERENCES 中配置 Cookie 后重试。",
                            style = EditorialTypography.labelSmall,
                            color = InkMuted
                        )
                    }
                }

                is ParseState.Success -> {
                    MagazineNoteCard(
                        note = state.note,
                        downloadState = downloadState,
                        onDownloadClicked = { viewModel.downloadCurrentMedia(context) },
                        onDownloadCurrentClicked = { pageIndex ->
                            viewModel.downloadSingleImage(context, pageIndex)
                        },
                        onImageClicked = { index ->
                            if (state.note.type == NoteType.VIDEO) {
                                val videoUrl = state.note.videoUrl
                                if (!videoUrl.isNullOrBlank()) {
                                    viewModel.openInspect(videoUrl, true)
                                } else if (state.note.images.isNotEmpty()) {
                                    viewModel.openInspect(state.note.images[0], false)
                                }
                            } else {
                                if (state.note.images.isNotEmpty() && index in state.note.images.indices) {
                                    viewModel.openInspect(state.note.images[index], false)
                                }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Footer Credits
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HorizontalDivider(thickness = 0.5.dp, color = HairlineBorder)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "无印原图 · 小红书 & 抖音双平台无水印原图原视提取",
                    style = EditorialTypography.labelSmall,
                    color = InkMuted,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "小红书与抖音图文及视频著作权归属于原作者，请遵照相关知识产权规范合规使用",
                    style = EditorialTypography.labelSmall.copy(fontSize = 8.sp),
                    color = InkMuted.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Full Screen Media Preview Dialog
        inspectMedia?.let { (url, isVideo) ->
            MediaInspectDialog(
                mediaUrl = url,
                isVideo = isVideo,
                onDismiss = { viewModel.closeInspect() }
            )
        }

        // Cookie Settings Dialog
        if (isSettingsOpen) {
            CookieConfigDialog(
                initialCookie = customCookie,
                onSave = { viewModel.saveCookie(it) },
                onDismiss = { viewModel.closeSettings() }
            )
        }
    }
}
