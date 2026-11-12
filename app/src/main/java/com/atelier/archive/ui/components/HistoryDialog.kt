package com.atelier.archive.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.atelier.archive.model.HistoryItem
import com.atelier.archive.model.NoteType
import com.atelier.archive.model.Platform
import com.atelier.archive.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryDialog(
    historyList: List<HistoryItem>,
    onSelect: (HistoryItem) -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(InkPrimary.copy(alpha = 0.75f))
                .padding(horizontal = 16.dp, vertical = 36.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .border(1.dp, ChampagneGold, RoundedCornerShape(4.dp)),
                colors = CardDefaults.cardColors(containerColor = ParchmentBase),
                shape = RoundedCornerShape(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    // Dialog Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = "History",
                                tint = DeepGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "历史解析记录",
                                fontFamily = FontFamily.Serif,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = InkPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "(${historyList.size})",
                                style = EditorialTypography.labelMedium,
                                color = InkMuted
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (historyList.isNotEmpty()) {
                                Text(
                                    text = "清空",
                                    style = EditorialTypography.labelMedium,
                                    color = RustAccent,
                                    modifier = Modifier
                                        .clickable { onClearAll() }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(ParchmentCard)
                                    .border(0.5.dp, HairlineBorder, CircleShape)
                                    .clickable(onClick = onDismiss),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Close",
                                    tint = InkPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(thickness = 0.8.dp, color = ChampagneGold)
                    Spacer(modifier = Modifier.height(14.dp))

                    if (historyList.isEmpty()) {
                        // Empty State
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "❖",
                                    fontSize = 24.sp,
                                    color = ChampagneGold
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "暂无历史解析记录",
                                    style = EditorialTypography.bodyLarge,
                                    color = InkSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "您提取过的无水印图文与视频将自动保存在这里，方便随时回看与重新下载。",
                                    style = EditorialTypography.bodyMedium,
                                    color = InkMuted,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                    } else {
                        // History List
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(historyList, key = { it.id + it.timestamp }) { item ->
                                HistoryCard(
                                    item = item,
                                    onSelect = { onSelect(item) },
                                    onCopyLink = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        cm?.setPrimaryClip(ClipData.newPlainText("URL", item.shareUrl))
                                        Toast.makeText(context, "已复制作品链接", Toast.LENGTH_SHORT).show()
                                    },
                                    onDelete = { onDelete(item.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    item: HistoryItem,
    onSelect: () -> Unit,
    onCopyLink: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val isVideo = item.type == NoteType.VIDEO
    val timeFormatted = remember(item.timestamp) {
        val now = System.currentTimeMillis()
        val diff = now - item.timestamp
        when {
            diff < 60_000L -> "刚刚"
            diff < 3600_000L -> "${diff / 60_000L}分钟前"
            diff < 86400_000L -> "${diff / 3600_000L}小时前"
            else -> SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault()).format(Date(item.timestamp))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ParchmentCard, RoundedCornerShape(3.dp))
            .border(0.6.dp, HairlineBorder, RoundedCornerShape(3.dp))
            .clickable(onClick = onSelect)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(2.dp))
                .border(0.5.dp, DeepHairline, RoundedCornerShape(2.dp))
                .background(InkPrimary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            if (item.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (isVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(InkPrimary.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayCircleOutline,
                        contentDescription = "Video",
                        tint = ParchmentBase,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Badges & Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val platformName = if (item.platform == Platform.XIAOHONGSHU) "小红书" else "抖音"
                    Text(
                        text = platformName,
                        style = EditorialTypography.labelSmall.copy(fontSize = 10.sp),
                        color = DeepGold,
                        modifier = Modifier
                            .background(ParchmentBase, RoundedCornerShape(2.dp))
                            .border(0.5.dp, ChampagneGold.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val typeText = if (isVideo) "高清原视" else "${item.itemCount} 张原图"
                    Text(
                        text = typeText,
                        style = EditorialTypography.labelSmall.copy(fontSize = 10.sp),
                        color = InkMuted
                    )
                }

                Text(
                    text = timeFormatted,
                    style = EditorialTypography.labelSmall.copy(fontSize = 10.sp),
                    color = InkMuted
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Title
            Text(
                text = item.title.ifBlank { "无标题作品" },
                style = EditorialTypography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = InkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Author & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "@${item.authorName}",
                    style = EditorialTypography.labelSmall,
                    color = AntiqueBronze,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Copy link
                    IconButton(
                        onClick = onCopyLink,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy Link",
                            tint = InkMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Delete
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete",
                            tint = InkMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
