package com.atelier.archive.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.atelier.archive.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@kotlin.OptIn(UnstableApi::class)
@Composable
fun MediaInspectDialog(
    isVideo: Boolean,
    videoUrl: String?,
    images: List<String>,
    initialIndex: Int = 0,
    platform: String = "",
    onSaveSingleImage: (Int) -> Unit = {},
    onSaveAllImages: () -> Unit = {},
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(InkPrimary.copy(alpha = 0.96f))
        ) {
            if (isVideo && !videoUrl.isNullOrBlank()) {
                VideoPreviewPlayer(
                    videoUrl = videoUrl,
                    onDismiss = onDismiss
                )
            } else if (images.isNotEmpty()) {
                ImagePreviewPager(
                    images = images,
                    initialIndex = initialIndex,
                    platform = platform,
                    onSaveSingleImage = onSaveSingleImage,
                    onSaveAllImages = onSaveAllImages,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@kotlin.OptIn(UnstableApi::class)
@Composable
private fun VideoPreviewPlayer(
    videoUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPos by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Ticker to track position
    LaunchedEffect(exoPlayer, isPlaying) {
        while (isActive) {
            currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (duration == 0L && exoPlayer.duration > 0) {
                duration = exoPlayer.duration
            }
            delay(250)
        }
    }

    // Auto-hide controls
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Video View with NO native controls
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Custom Haute Couture Buffering Indicator
        if (isBuffering) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(InkPrimary.copy(alpha = 0.6f))
                    .border(1.dp, ChampagneGold, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = ChampagneGold,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Custom Overlay Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            ) {
                // Top Header Line
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 32.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "MASTER FILM · 原画母带预览",
                            fontFamily = FontFamily.Serif,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = ChampagneGold
                        )
                        Text(
                            text = "100% 纯净无水印母带",
                            style = EditorialTypography.labelSmall.copy(fontSize = 10.sp),
                            color = ParchmentBase.copy(alpha = 0.8f)
                        )
                    }

                    // Close Button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(ParchmentBase.copy(alpha = 0.15f))
                            .border(0.8.dp, ChampagneGold, CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ParchmentBase,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Center Play/Pause Emblem
                if (!isBuffering) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(InkPrimary.copy(alpha = 0.65f))
                            .border(1.2.dp, ChampagneGold, CircleShape)
                            .clickable {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = ChampagneGold,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Bottom Custom Progress Bar & Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 32.dp)
                ) {
                    // Time format
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentPos),
                            fontFamily = FontFamily.Serif,
                            fontSize = 12.sp,
                            color = ChampagneGold
                        )
                        Text(
                            text = formatTime(duration),
                            fontFamily = FontFamily.Serif,
                            fontSize = 12.sp,
                            color = ParchmentBase.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Custom Slider matching Haute Couture Theme
                    val progress = if (duration > 0) (currentPos.toFloat() / duration).coerceIn(0f, 1f) else 0f
                    Slider(
                        value = progress,
                        onValueChange = { newProgress ->
                            val targetMs = (newProgress * duration).toLong()
                            exoPlayer.seekTo(targetMs)
                            currentPos = targetMs
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = ChampagneGold,
                            activeTrackColor = ChampagneGold,
                            inactiveTrackColor = ParchmentBase.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ImagePreviewPager(
    images: List<String>,
    initialIndex: Int,
    platform: String,
    onSaveSingleImage: (Int) -> Unit,
    onSaveAllImages: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val safeInitial = initialIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = safeInitial,
        pageCount = { images.size }
    )

    val referer = if (platform.contains("XHS", ignoreCase = true)) {
        "https://www.xiaohongshu.com/"
    } else {
        "https://www.douyin.com/"
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Horizontal Swiping Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 88.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(images[pageIndex])
                        .addHeader("Referer", referer)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Image ${pageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(3.dp))
                        .border(0.6.dp, ChampagneGold.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                )
            }
        }

        // Left Navigation Chevron (if multiple images and not on first page)
        if (images.size > 1 && pagerState.currentPage > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(InkPrimary.copy(alpha = 0.75f))
                    .border(1.dp, ChampagneGold.copy(alpha = 0.8f), CircleShape)
                    .clickable {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChevronLeft,
                    contentDescription = "Previous Image",
                    tint = ChampagneGold,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Right Navigation Chevron (if multiple images and not on last page)
        if (images.size > 1 && pagerState.currentPage < images.size - 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(InkPrimary.copy(alpha = 0.75f))
                    .border(1.dp, ChampagneGold.copy(alpha = 0.8f), CircleShape)
                    .clickable {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = "Next Image",
                    tint = ChampagneGold,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Top Header Line
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 32.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "LOSSLESS RETRIEVAL · 原图画廊",
                    fontFamily = FontFamily.Serif,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = ChampagneGold
                )
                Text(
                    text = if (images.size > 1)
                        "可左右滑动切换画页 (第 ${pagerState.currentPage + 1} / ${images.size} 张)"
                    else
                        "无水印无损画质预览",
                    style = EditorialTypography.labelSmall.copy(fontSize = 10.sp),
                    color = ParchmentBase.copy(alpha = 0.8f)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Page indicator badge
                if (images.size > 1) {
                    Box(
                        modifier = Modifier
                            .background(InkPrimary.copy(alpha = 0.85f), RoundedCornerShape(2.dp))
                            .border(0.6.dp, ChampagneGold, RoundedCornerShape(2.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = String.format("%02d / %02d", pagerState.currentPage + 1, images.size),
                            fontFamily = FontFamily.Serif,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ChampagneGold
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }

                // Close Button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(ParchmentBase.copy(alpha = 0.15f))
                        .border(0.8.dp, ChampagneGold, CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = ParchmentBase,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Bottom Action Bar: Save Current & Save All
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(InkPrimary.copy(alpha = 0.85f))
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Save Current Image Button
            Button(
                onClick = { onSaveSingleImage(pagerState.currentPage) },
                shape = RoundedCornerShape(2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ChampagneGold,
                    contentColor = InkPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Save Current",
                    modifier = Modifier.size(15.dp),
                    tint = InkPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (images.size > 1) "保存第 ${pagerState.currentPage + 1} 张原图" else "保存无水印原图",
                    style = EditorialTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = InkPrimary
                )
            }

            // Save All Images Button (if note has multiple images)
            if (images.size > 1) {
                OutlinedButton(
                    onClick = onSaveAllImages,
                    shape = RoundedCornerShape(2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ChampagneGold),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ChampagneGold
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Save All",
                        modifier = Modifier.size(15.dp),
                        tint = ChampagneGold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "保存全部 (${images.size} 张)",
                        style = EditorialTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = ChampagneGold
                    )
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
