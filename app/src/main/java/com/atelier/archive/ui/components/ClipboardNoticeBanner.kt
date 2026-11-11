package com.atelier.archive.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atelier.archive.ui.theme.*

@Composable
fun ClipboardNoticeBanner(
    visible: Boolean,
    detectedUrl: String,
    onParseNow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(InkPrimary, RoundedCornerShape(2.dp))
                .border(0.8.dp, ChampagneGold, RoundedCornerShape(2.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CLIPBOARD DETECTED · 发现小红书 / 抖音作品链接",
                    style = EditorialTypography.labelSmall,
                    color = ChampagneGold
                )
                Text(
                    text = detectedUrl,
                    style = EditorialTypography.bodyMedium.copy(fontSize = 11.sp),
                    color = ParchmentBase.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "DISMISS",
                    style = EditorialTypography.labelSmall,
                    color = ParchmentBase.copy(alpha = 0.6f),
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(4.dp)
                )

                Box(
                    modifier = Modifier
                        .background(ChampagneGold, RoundedCornerShape(2.dp))
                        .clickable(onClick = onParseNow)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "EXTRACT",
                        style = EditorialTypography.labelSmall,
                        color = InkPrimary
                    )
                }
            }
        }
    }
}
