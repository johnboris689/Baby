package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.baby.ai.R
import com.example.ui.theme.BabyCyan
import com.example.ui.theme.BabyViolet
import com.example.ui.viewmodel.AssistantState

@Composable
fun BabyAvatar(
    state: AssistantState = AssistantState.IDLE,
    size: Dp = 38.dp,
    rmsDb: Float = 0f,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "avatar-transition")

    val pulseScale by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = when (state) {
            AssistantState.LISTENING -> (1.06f + (rmsDb.coerceIn(0f, 10f) * 0.015f))
            AssistantState.THINKING -> 1.05f
            AssistantState.SPEAKING -> 1.08f
            AssistantState.IDLE -> 1.0f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.LISTENING -> 800
                    AssistantState.THINKING -> 1200
                    AssistantState.SPEAKING -> 600
                    AssistantState.IDLE -> 2000
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatar-pulse"
    )

    val rotationAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "avatar-rotate"
    )

    val auraBrush = when (state) {
        AssistantState.LISTENING -> Brush.sweepGradient(
            listOf(
                BabyCyan.copy(alpha = 0.8f),
                Color(0xFF38BDF8).copy(alpha = 0.4f),
                BabyCyan.copy(alpha = 0.8f)
            )
        )
        AssistantState.THINKING -> Brush.sweepGradient(
            listOf(
                BabyViolet.copy(alpha = 0.8f),
                BabyCyan.copy(alpha = 0.6f),
                BabyViolet.copy(alpha = 0.8f)
            )
        )
        AssistantState.SPEAKING -> Brush.sweepGradient(
            listOf(
                Color(0xFF60A5FA).copy(alpha = 0.8f),
                BabyCyan.copy(alpha = 0.7f),
                Color(0xFF60A5FA).copy(alpha = 0.8f)
            )
        )
        AssistantState.IDLE -> Brush.linearGradient(
            listOf(
                BabyCyan.copy(alpha = 0.35f),
                Color(0xFF1E293B).copy(alpha = 0.5f)
            )
        )
    }

    Box(
        modifier = modifier
            .size(size + 6.dp)
            .scale(if (state != AssistantState.IDLE) pulseScale else 1f),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow aura when active
        if (state != AssistantState.IDLE) {
            Box(
                modifier = Modifier
                    .size(size + 4.dp)
                    .clip(CircleShape)
                    .then(
                        if (state == AssistantState.THINKING) Modifier.rotate(rotationAngle)
                        else Modifier
                    )
                    .background(auraBrush)
            )
        }

        // Inner Avatar Image
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF070B14))
                .border(
                    width = if (state != AssistantState.IDLE) 1.5.dp else 1.dp,
                    brush = auraBrush,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.baby_icon),
                contentDescription = "Baby Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
            )
        }
    }
}
