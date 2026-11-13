package com.atelier.archive.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atelier.archive.ui.theme.*

@Composable
fun EditorialHeader(
    modifier: Modifier = Modifier,
    versionName: String = "V1.0.13",
    onOpenHistory: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ParchmentBase)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Metadata top header line
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EDITION · $versionName",
                style = EditorialTypography.labelSmall,
                color = InkMuted
            )

            // History Button (Enlarged, comfortable touch target)
            Row(
                modifier = Modifier
                    .clickable(onClick = onOpenHistory)
                    .background(ParchmentCard, RoundedCornerShape(2.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = "History",
                    tint = DeepGold,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "历史解析",
                    style = EditorialTypography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = DeepGold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(thickness = 0.6.dp, color = HairlineBorder)
        Spacer(modifier = Modifier.height(14.dp))

        // Atelier Archive French Monogram Subtitle
        Text(
            text = "✦  A T E L I E R   A R C H I V E  ✦",
            fontFamily = FontFamily.Serif,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 4.sp,
            color = ChampagneGold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Elevated Main Masthead: Dual Platform Lossless Extraction
        Text(
            text = "小红书 · 抖音双平台",
            fontFamily = FontFamily.Serif,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 4.sp,
            color = InkPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Subhead
        Text(
            text = "无水印高清原图 · 原画视频 · 实况动图提取",
            fontFamily = FontFamily.Serif,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 1.5.sp,
            color = DeepGold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Classic Double-rule divider
        HorizontalDivider(thickness = 1.dp, color = InkPrimary)
        Spacer(modifier = Modifier.height(2.dp))
        HorizontalDivider(thickness = 0.5.dp, color = ChampagneGold)
    }
}
