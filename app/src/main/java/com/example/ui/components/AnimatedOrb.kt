package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.AssistantState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnimatedOrb(
    state: AssistantState,
    rmsDb: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_rotation")

    // Core breathing scale
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_breath"
    )

    // Wave rotations
    val rotation1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_rot_1"
    )

    val rotation2 by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_rot_2"
    )

    // Thinking state rapid pulse
    val thinkingPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thinking_pulse"
    )

    // Speaking state frequency scale
    val speakingFrequency by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "speaking_freq"
    )

    // Map RMS dB to scale multiplier (usually ranges from -2 to 10)
    val voiceScale = remember(rmsDb) {
        val target = if (rmsDb > 0) {
            1.0f + (rmsDb / 15f).coerceIn(0f, 0.8f)
        } else {
            1.0f
        }
        target
    }

    // Dynamic color brush based on state
    val orbColors = remember(state) {
        when (state) {
            AssistantState.IDLE -> listOf(
                Color(0xFF6366F1), // Indigo
                Color(0xFF3B82F6), // Blue
                Color(0xFFEC4899)  // Pink
            )
            AssistantState.LISTENING -> listOf(
                Color(0xFF10B981), // Emerald Teal
                Color(0xFF3B82F6), // Blue
                Color(0xFF06B6D4)  // Cyan
            )
            AssistantState.THINKING -> listOf(
                Color(0xFF8B5CF6), // Purple
                Color(0xFFEC4899), // Pink
                Color(0xFFF59E0B)  // Amber
            )
            AssistantState.SPEAKING -> listOf(
                Color(0xFFEF4444), // Red
                Color(0xFFEC4899), // Pink
                Color(0xFF8B5CF6)  // Violet
            )
        }
    }

    Box(
        modifier = modifier.size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.width * 0.3f

            // 1. Draw outermost breathing ambient glow
            val outerScale = when (state) {
                AssistantState.LISTENING -> voiceScale * 1.4f
                AssistantState.THINKING -> thinkingPulse * 1.3f
                AssistantState.SPEAKING -> 1.3f
                AssistantState.IDLE -> breathingScale * 1.2f
            }
            drawOrbLayer(
                center = center,
                radius = baseRadius * outerScale,
                colors = orbColors,
                alpha = 0.15f,
                rotation = rotation1
            )

            // 2. Draw middle wavy layer
            val middleScale = when (state) {
                AssistantState.LISTENING -> voiceScale * 1.15f
                AssistantState.THINKING -> thinkingPulse * 1.1f
                AssistantState.SPEAKING -> 1.1f + (speakingFrequency / 100f)
                AssistantState.IDLE -> breathingScale * 1.05f
            }
            drawOrbLayer(
                center = center,
                radius = baseRadius * middleScale,
                colors = orbColors.asReversed(),
                alpha = 0.3f,
                rotation = rotation2
            )

            // 3. Draw core layer
            val coreScale = when (state) {
                AssistantState.LISTENING -> voiceScale * 0.95f
                AssistantState.THINKING -> thinkingPulse * 0.95f
                AssistantState.SPEAKING -> 0.95f
                AssistantState.IDLE -> breathingScale * 0.9f
            }

            // Offset the center dynamically in speaking state to create a liquid/voice wave ripple effect
            val coreCenter = if (state == AssistantState.SPEAKING) {
                val rad = Math.toRadians(rotation1.toDouble())
                Offset(
                    center.x + speakingFrequency * cos(rad).toFloat() * 0.6f,
                    center.y + speakingFrequency * sin(rad).toFloat() * 0.6f
                )
            } else {
                center
            }

            drawOrbLayer(
                center = coreCenter,
                radius = baseRadius * coreScale,
                colors = orbColors,
                alpha = 0.75f,
                rotation = rotation1 + 45f
            )

            // 4. Draw highlights for 3D glassmorphic depth
            drawCircle(
                color = Color.White.copy(alpha = 0.15f),
                radius = baseRadius * coreScale * 0.4f,
                center = Offset(coreCenter.x - (baseRadius * 0.25f), coreCenter.y - (baseRadius * 0.25f))
            )
        }
    }
}

private fun DrawScope.drawOrbLayer(
    center: Offset,
    radius: Float,
    colors: List<Color>,
    alpha: Float,
    rotation: Float
) {
    val rad = Math.toRadians(rotation.toDouble())
    val startOffset = Offset(
        center.x + radius * cos(rad).toFloat(),
        center.y + radius * sin(rad).toFloat()
    )
    val endOffset = Offset(
        center.x - radius * cos(rad).toFloat(),
        center.y - radius * sin(rad).toFloat()
    )

    val brush = Brush.linearGradient(
        colors = colors,
        start = startOffset,
        end = endOffset
    )

    drawCircle(
        brush = brush,
        radius = radius,
        center = center,
        alpha = alpha
    )
}
