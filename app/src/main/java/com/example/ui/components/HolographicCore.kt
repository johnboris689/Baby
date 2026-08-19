package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.baby.ai.R
import com.example.ui.theme.BabyBlue
import com.example.ui.theme.BabyCyan
import com.example.ui.theme.BabyPink
import com.example.ui.theme.BabyViolet
import com.example.ui.viewmodel.AssistantState
import kotlin.math.cos
import kotlin.math.sin

/**
 * Next-Generation Futuristic Holographic AI Core for Baby Nexus.
 * Integrates the official Baby avatar with orbital HUD rings, particle constellation,
 * and responsive state animations for Listening, Thinking, Speaking, and Idle.
 */
@Composable
fun HolographicCore(
    state: AssistantState,
    rmsDb: Float,
    isOnline: Boolean = true,
    size: Dp = 190.dp,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "holographic_core")

    // Slow orbital rotation
    val orbitRotation1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (state == AssistantState.THINKING) 3000 else 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_1"
    )

    // Counter-orbital rotation
    val orbitRotation2 by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (state == AssistantState.THINKING) 4000 else 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_2"
    )

    // Breathing pulse
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (state != AssistantState.IDLE) 900 else 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    // Wave ring expansion for listening/speaking
    val waveExpansion by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (state == AssistantState.LISTENING) 1100 else 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_expand"
    )

    // Dynamic scale based on microphone audio level (rmsDb)
    val reactiveAudioScale = remember(rmsDb, state) {
        if (state == AssistantState.LISTENING && rmsDb > 0) {
            1.0f + (rmsDb / 20f).coerceIn(0f, 0.35f)
        } else if (state == AssistantState.SPEAKING) {
            1.08f
        } else {
            1.0f
        }
    }

    val primaryColor = when {
        !isOnline -> Color(0xFFF59E0B)
        state == AssistantState.LISTENING -> BabyCyan
        state == AssistantState.THINKING -> BabyViolet
        state == AssistantState.SPEAKING -> Color(0xFF3B82F6)
        else -> BabyCyan
    }

    val secondaryColor = when {
        !isOnline -> Color(0xFFD97706)
        state == AssistantState.LISTENING -> Color(0xFF06B6D4)
        state == AssistantState.THINKING -> BabyPink
        state == AssistantState.SPEAKING -> BabyViolet
        else -> BabyBlue
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(breathScale * reactiveAudioScale)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Holographic Canvas Layer (Orbital HUD Rings, Nodes, Energy Waves)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
            val radius = size.toPx() / 2f

            // Outer Soft Ambient Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = if (state != AssistantState.IDLE) 0.35f else 0.18f),
                        secondaryColor.copy(alpha = if (state != AssistantState.IDLE) 0.15f else 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 0.98f
                ),
                radius = radius * 0.98f,
                center = center
            )

            // Animated Expanding Shockwaves when Active
            if (state == AssistantState.LISTENING || state == AssistantState.SPEAKING) {
                val waveAlpha = (1f - (waveExpansion - 0.75f) / 0.6f).coerceIn(0f, 1f) * 0.45f
                drawCircle(
                    color = primaryColor.copy(alpha = waveAlpha),
                    radius = radius * 0.70f * waveExpansion,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Orbital Ring 1 (Dashed HUD Ring)
            rotate(orbitRotation1, pivot = center) {
                drawCircle(
                    color = primaryColor.copy(alpha = 0.40f),
                    radius = radius * 0.88f,
                    center = center,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f, 6f, 12f), 0f),
                        cap = StrokeCap.Round
                    )
                )

                // HUD Nodes on Orbit 1
                val nodeAngle1 = 0.0
                val nodeAngle2 = Math.PI
                val nodeDist = radius * 0.88f
                drawCircle(
                    color = primaryColor,
                    radius = 3.dp.toPx(),
                    center = Offset(
                        (center.x + nodeDist * cos(nodeAngle1)).toFloat(),
                        (center.y + nodeDist * sin(nodeAngle1)).toFloat()
                    )
                )
                drawCircle(
                    color = secondaryColor,
                    radius = 2.5.dp.toPx(),
                    center = Offset(
                        (center.x + nodeDist * cos(nodeAngle2)).toFloat(),
                        (center.y + nodeDist * sin(nodeAngle2)).toFloat()
                    )
                )
            }

            // Orbital Ring 2 (Fine Tech Outer Ring)
            rotate(orbitRotation2, pivot = center) {
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.30f),
                    radius = radius * 0.76f,
                    center = center,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 20f), 0f)
                    )
                )
            }

            // Inner Quantum Ring (Solid Fine Line with glow)
            drawCircle(
                color = primaryColor.copy(alpha = 0.55f),
                radius = radius * 0.62f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        // Central Avatar Core
        val avatarSize = size * 0.54f
        Box(
            modifier = Modifier
                .size(avatarSize)
                .shadow(
                    elevation = if (state != AssistantState.IDLE) 24.dp else 12.dp,
                    shape = CircleShape,
                    ambientColor = primaryColor,
                    spotColor = primaryColor
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF131D33),
                            Color(0xFF070C18)
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.90f),
                            secondaryColor.copy(alpha = 0.70f),
                            Color.White.copy(alpha = 0.40f)
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.baby_icon),
                contentDescription = "Baby AI Nexus Core",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )

            // Specular Holographic Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.Transparent,
                                primaryColor.copy(alpha = 0.12f)
                            )
                        )
                    )
            )
        }
    }
}
