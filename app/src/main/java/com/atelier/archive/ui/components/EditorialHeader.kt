package com.atelier.archive.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
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
    onOpenSettings: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ParchmentBase)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Metadata top header line
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EDITION · V1.0.3",
                style = EditorialTypography.labelSmall,
                color = InkMuted
            )
            Text(
                text = "RED · DOUYIN DUAL-PLATFORM",
                style = EditorialTypography.labelSmall,
                color = InkMuted
            )
            Text(
                text = "LOSSLESS EXTRACTOR",
                style = EditorialTypography.labelSmall,
                color = InkMuted
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(thickness = 0.6.dp, color = HairlineBorder)
        Spacer(modifier = Modifier.height(14.dp))

        // Main Masthead
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "无印",
                fontFamily = FontFamily.Serif,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                color = InkPrimary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "✦",
                fontSize = 14.sp,
                color = ChampagneGold
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "原图",
                fontFamily = FontFamily.Serif,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                color = InkPrimary
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Subhead
        Text(
            text = "MARKFREE · 小红书 & 抖音双平台无水印原图原视提取",
            style = EditorialTypography.labelMedium,
            color = AntiqueBronze,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Classic Double-rule divider
        HorizontalDivider(thickness = 1.dp, color = InkPrimary)
        Spacer(modifier = Modifier.height(2.dp))
        HorizontalDivider(thickness = 0.5.dp, color = ChampagneGold)
    }
}
