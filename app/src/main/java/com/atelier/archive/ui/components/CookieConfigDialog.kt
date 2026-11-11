package com.atelier.archive.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.atelier.archive.ui.theme.*

@Composable
fun CookieConfigDialog(
    initialCookie: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var cookieText by remember { mutableStateOf(initialCookie) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ParchmentCard, RoundedCornerShape(3.dp))
                .border(0.8.dp, ChampagneGold, RoundedCornerShape(3.dp))
                .padding(20.dp)
        ) {
            Text(
                text = "SECURITY DISCIPLINE / 凭证配置",
                style = EditorialTypography.labelLarge,
                color = InkPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "若遇到平台人机验证，可填入小红书网页端登录 Cookie (例如包含 a1, web_session 等参数) 增强解析成功率。",
                style = EditorialTypography.bodyMedium,
                color = InkSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(ParchmentBase)
                    .border(0.6.dp, DeepHairline, RoundedCornerShape(2.dp))
                    .padding(8.dp)
            ) {
                if (cookieText.isEmpty()) {
                    Text(
                        text = "Paste cookie here: a1=...; web_session=...",
                        style = EditorialTypography.bodyMedium,
                        color = InkMuted
                    )
                }
                BasicTextField(
                    value = cookieText,
                    onValueChange = { cookieText = it },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = InkPrimary,
                        lineHeight = 16.sp
                    ),
                    cursorBrush = SolidColor(InkPrimary),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Text(
                        text = "CANCEL",
                        style = EditorialTypography.labelSmall,
                        color = InkMuted
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(cookieText.trim())
                        onDismiss()
                    },
                    shape = RoundedCornerShape(2.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InkPrimary,
                        contentColor = ParchmentBase
                    )
                ) {
                    Text(
                        text = "SAVE CONFIG",
                        style = EditorialTypography.labelSmall,
                        color = ParchmentBase
                    )
                }
            }
        }
    }
}
