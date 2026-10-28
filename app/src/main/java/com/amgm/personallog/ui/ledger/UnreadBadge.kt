package com.amgm.personallog.ui.ledger

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.amgm.personallog.ui.design.AppTheme
import com.amgm.personallog.ui.design.Space

/**
 * 「未読」の小さな印。琥珀の丸薬の外側から、輪が広がりながら薄くなって消えるリップルを繰り返す。
 * 未読の支払いの行・一覧の1枚に付ける。色は琥珀(accent)だけを使う。
 */
@Composable
fun UnreadBadge(modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val transition = rememberInfiniteTransition(label = "unread")
    // 0 → 1 を繰り返す。0 で丸薬と同じ大きさ、1 で大きく広がって透明になる
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "unread-ripple",
    )
    val shape = RoundedCornerShape(50)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // 輪(丸薬の後ろ。丸薬の外側にだけ見える)
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val scale = 1f + 0.6f * phase
                    scaleX = scale
                    scaleY = scale
                    alpha = 0.6f * (1f - phase)
                }
                .border(width = 1.dp, color = c.accent, shape = shape),
        )
        Box(
            modifier = Modifier
                .clip(shape)
                .background(c.accent)
                .padding(horizontal = Space.s, vertical = 3.dp),
        ) {
            Text("未読", style = AppTheme.type.label, color = c.onAccent)
        }
    }
}
