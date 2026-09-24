package chat.simplex.app.views

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@Composable
fun VoiceSpeedChip(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val speeds = listOf(1.0f, 1.5f, 2.0f)
    val isBoosted = currentSpeed > 1.0f

    // Динамические токены Monet для Android 12+ с безопасным фоллбэком
    val chipBg = remember(isBoosted, context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isBoosted) {
                Color(ContextCompat.getColor(context, android.R.color.system_accent1_200))
            } else {
                Color(ContextCompat.getColor(context, android.R.color.system_neutral1_800)).copy(alpha = 0.6f)
            }
        } else {
            if (isBoosted) Color(0xFFD0BCFF) else Color(0x33FFFFFF)
        }
    }

    val chipTextColor = remember(isBoosted, context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isBoosted) {
                Color(ContextCompat.getColor(context, android.R.color.system_accent1_900))
            } else {
                Color(ContextCompat.getColor(context, android.R.color.system_neutral1_100))
            }
        } else {
            if (isBoosted) Color.Black else Color.White
        }
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(chipBg, CircleShape)
            .clickable {
                val nextIndex = (speeds.indexOf(currentSpeed) + 1) % speeds.size
                onSpeedChange(speeds[nextIndex])
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = currentSpeed,
            transitionSpec = {
                (slideInVertically(spring()) { it } + fadeIn())
                    .togetherWith(slideOutVertically(spring()) { -it } + fadeOut())
            },
            label = "VoiceSpeedAnimation"
        ) { speed ->
            val label = if (speed % 1.0f == 0.0f) "${speed.toInt()}x" else "${speed}x"
            BasicText(
                text = label,
                style = TextStyle(
                    color = chipTextColor,
                    fontSize = 11.sp,
                    fontWeight = if (isBoosted) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}
