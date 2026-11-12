package com.atelier.archive.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atelier.archive.ui.theme.*

@Composable
fun ParchmentInputSection(
    inputUrl: String,
    onUrlChange: (String) -> Unit,
    onPasteClicked: () -> Unit,
    onClearClicked: () -> Unit,
    onParseClicked: () -> Unit,
    isParsing: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ParchmentCard)
            .border(0.8.dp, HairlineBorder, RoundedCornerShape(3.dp))
            .padding(16.dp)
    ) {
        // Label with edition stamp & Supported Platform Tags
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "分享链接",
                    style = EditorialTypography.labelMedium,
                    color = InkSecondary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "✦ 小红书 / 抖音",
                    style = EditorialTypography.labelSmall,
                    color = DeepGold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (inputUrl.isNotBlank()) {
                    Text(
                        text = "清空",
                        style = EditorialTypography.labelSmall,
                        color = InkMuted,
                        modifier = Modifier
                            .clickable(onClick = onClearClicked)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .clickable(onClick = onPasteClicked)
                        .background(ParchmentBase, RoundedCornerShape(2.dp))
                        .border(0.5.dp, ChampagneGold, RoundedCornerShape(2.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentPaste,
                        contentDescription = "Paste",
                        tint = DeepGold,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "粘贴",
                        style = EditorialTypography.labelSmall,
                        color = DeepGold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Custom stylized input field
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .background(ParchmentBase)
                .border(0.6.dp, DeepHairline, RoundedCornerShape(2.dp))
                .padding(10.dp)
        ) {
            if (inputUrl.isEmpty()) {
                Text(
                    text = "在此粘贴或输入小红书 / 抖音分享文本（自动识别短链）…",
                    style = EditorialTypography.bodyMedium,
                    color = InkMuted
                )
            }
            BasicTextField(
                value = inputUrl,
                onValueChange = onUrlChange,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 13.sp,
                    color = InkPrimary,
                    lineHeight = 18.sp
                ),
                cursorBrush = SolidColor(InkPrimary),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Primary Action Button
        Button(
            onClick = onParseClicked,
            enabled = inputUrl.isNotBlank() && !isParsing,
            shape = RoundedCornerShape(2.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = InkPrimary,
                contentColor = ParchmentBase,
                disabledContainerColor = HairlineBorder,
                disabledContentColor = InkMuted
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        ) {
            if (isParsing) {
                CircularProgressIndicator(
                    color = ChampagneGold,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "DECIPHERING ARCHIVE · 解析中…",
                    style = EditorialTypography.labelLarge,
                    color = ChampagneGold
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "ARCHIVE · 立即提取无水印原画",
                        style = EditorialTypography.labelLarge,
                        color = ParchmentBase
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "→",
                        fontSize = 14.sp,
                        color = ChampagneGold
                    )
                }
            }
        }
    }
}
