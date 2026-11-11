@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.atelier.archive.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.atelier.archive.model.DownloadState
import com.atelier.archive.model.NoteDetail
import com.atelier.archive.model.NoteType
import com.atelier.archive.ui.theme.*

@Composable
fun MagazineNoteCard(
    note: NoteDetail,
    downloadState: DownloadState,
    onDownloadClicked: () -> Unit,
    onImageClicked: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDescExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ParchmentCard)
            .border(0.8.dp, HairlineBorder, RoundedCornerShape(3.dp))
            .padding(18.dp)
    ) {
        // Top author signature & issue tag
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.authorAvatar.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(note.authorAvatar)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Author Avatar",
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .border(1.dp, ChampagneGold, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Column {
                    Text(
                        text = note.authorName.uppercase(),
                        style = EditorialTypography.labelLarge,
                        color = InkPrimary
                    )
                    Text(
                        text = note.platform.displayName,
                        style = EditorialTypography.labelSmall,
                        color = DeepGold
                    )
                }
            }

            // Note Type Badge
            Box(
                modifier = Modifier
                    .background(ParchmentBase, RoundedCornerShape(2.dp))
                    .border(0.5.dp, DeepHairline, RoundedCornerShape(2.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (note.type == NoteType.VIDEO) "MOTION FILM · 视频" else "PHOTO FOLIO · 图集",
                    style = EditorialTypography.labelSmall,
                    color = AntiqueBronze
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(thickness = 0.5.dp, color = HairlineBorder)
        Spacer(modifier = Modifier.height(14.dp))

        // Note Title
        Text(
            text = note.title,
            style = EditorialTypography.titleLarge,
            color = InkPrimary
        )

        // Note Description
        if (note.desc.isNotBlank() && note.desc != note.title) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = note.desc,
                style = EditorialTypography.bodyMedium,
                color = InkSecondary,
                maxLines = if (isDescExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { isDescExpanded = !isDescExpanded }
            )
            if (note.desc.length > 80) {
                Text(
                    text = if (isDescExpanded) "COLLAPSE · 折叠" else "READ MORE · 阅读全文",
                    style = EditorialTypography.labelSmall,
                    color = DeepGold,
                    modifier = Modifier
                        .clickable { isDescExpanded = !isDescExpanded }
                        .padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Media Showcase Section
        if (note.type == NoteType.VIDEO) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SlateDark)
                    .border(0.8.dp, DeepHairline, RoundedCornerShape(2.dp))
                    .clickable { onImageClicked(0) }
            ) {
                if (note.images.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(note.images[0])
                            .crossfade(true)
                            .build(),
                        contentDescription = "Video Poster",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // Play Icon Overlay
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(InkPrimary.copy(alpha = 0.75f))
                        .border(1.dp, ChampagneGold, CircleShape)
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Video",
                        tint = ParchmentBase,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Video Stamp
                Text(
                    text = "${note.platform.code} · HD 1080P MASTER STREAM",
                    style = EditorialTypography.labelSmall,
                    color = ParchmentBase,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(InkPrimary.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        } else {
            // Image Folio Pager
            if (note.images.isNotEmpty()) {
                val pagerState = rememberPagerState(pageCount = { note.images.size })

                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(ParchmentBase)
                            .border(0.8.dp, HairlineBorder, RoundedCornerShape(2.dp))
                    ) {
                        val referer = if (note.platform.code == "XHS") "https://www.xiaohongshu.com/" else "https://www.douyin.com/"
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(note.images[page])
                                    .addHeader("Referer", referer)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Image $page",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { onImageClicked(page) }
                            )
                        }

                        // Edition Page Stamp
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .background(InkPrimary.copy(alpha = 0.85f), RoundedCornerShape(2.dp))
                                .border(0.5.dp, ChampagneGold, RoundedCornerShape(2.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = String.format("%02d / %02d", pagerState.currentPage + 1, note.images.size),
                                style = EditorialTypography.labelSmall,
                                color = ChampagneGold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "TOUCH TO ENLARGE · 轻触画页全屏检视无水印高清原图",
                        style = EditorialTypography.labelSmall,
                        color = InkMuted,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        HorizontalDivider(thickness = 0.5.dp, color = HairlineBorder)
        Spacer(modifier = Modifier.height(16.dp))

        // Download Action Section
        when (downloadState) {
            is DownloadState.Idle -> {
                Button(
                    onClick = onDownloadClicked,
                    shape = RoundedCornerShape(2.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChampagneGold,
                        contentColor = InkPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        modifier = Modifier.size(16.dp),
                        tint = InkPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (note.type == NoteType.VIDEO)
                            "COLLECT VIDEO · 臻藏无水印视频至相册"
                        else
                            "COLLECT ALL (${note.images.size}) · 臻藏全部高清原图",
                        style = EditorialTypography.labelLarge,
                        color = InkPrimary
                    )
                }
            }

            is DownloadState.Progress -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ParchmentBase)
                        .border(0.5.dp, ChampagneGold, RoundedCornerShape(2.dp))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        progress = {
                            if (downloadState.total > 0)
                                downloadState.current.toFloat() / downloadState.total
                            else 0f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = ChampagneGold,
                        trackColor = HairlineBorder
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ARCHIVING TO STORAGE · 正在转存 ${downloadState.current} / ${downloadState.total}",
                        style = EditorialTypography.labelSmall,
                        color = DeepGold
                    )
                }
            }

            is DownloadState.Success -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HeritageGreen.copy(alpha = 0.08f), RoundedCornerShape(2.dp))
                        .border(0.8.dp, HeritageGreen, RoundedCornerShape(2.dp))
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Success",
                        tint = HeritageGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CONSERVED IN SYSTEM GALLERY · 已保存至系统相册",
                        style = EditorialTypography.labelLarge,
                        color = HeritageGreen
                    )
                }
            }

            is DownloadState.Error -> {
                Text(
                    text = "DOWNLOAD ERROR: ${downloadState.message}",
                    style = EditorialTypography.labelSmall,
                    color = WaxSealBurgundy,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
