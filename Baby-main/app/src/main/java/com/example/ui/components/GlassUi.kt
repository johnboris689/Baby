package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BabyBlue
import com.example.ui.theme.BabyCyan
import com.example.ui.theme.BabyGlass
import com.example.ui.theme.BabyPink
import com.example.ui.theme.BabyViolet

private val GlassBorder = Color.White.copy(alpha = 0.10f)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.075f),
                        BabyGlass.copy(alpha = 0.70f),
                        Color.White.copy(alpha = 0.025f)
                    )
                )
            )
            .border(1.dp, GlassBorder, shape),
        content = content
    )
}

@Composable
fun NeonOrb(
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "baby-orb")
    val pulse by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = if (active) 1.12f else 1.03f,
        animationSpec = infiniteRepeatable(tween(if (active) 900 else 1800), RepeatMode.Reverse),
        label = "orb-pulse"
    )
    Box(
        modifier = modifier
            .size(150.dp)
            .shadow(if (active) 28.dp else 18.dp, CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.95f),
                        BabyCyan.copy(alpha = 0.85f),
                        BabyBlue.copy(alpha = 0.60f),
                        BabyViolet.copy(alpha = 0.24f),
                        Color.Transparent
                    ),
                    radius = 115f * pulse
                ),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(86.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(BabyBlue, BabyViolet, BabyPink))
                )
                .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape)
        )
    }
}
