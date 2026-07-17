package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AnimatedOrb
import com.example.ui.viewmodel.AssistantState
import com.example.ui.viewmodel.BabyViewModel

@Composable
fun HomeScreen(
    viewModel: BabyViewModel,
    onNavigateToChat: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.assistantState.collectAsState()
    val rmsDb by viewModel.rmsDb.collectAsState()
    val partialText by viewModel.partialSpeechText.collectAsState()
    val isContinuousMode by viewModel.isContinuousMode.collectAsState()
    val isOfflineMode by viewModel.isOfflineMode.collectAsState()
    val backendHealth by viewModel.backendHealth.collectAsState()
    val isInternetAvailable by viewModel.isInternetAvailable.collectAsState()

    val primaryGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F172A), // Slate 900
            Color(0xFF020617)  // Slate 950
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(primaryGradient)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // --- Header Status Area ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "BABY",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = if (isOfflineMode) "Local Private Intelligence" else if (!isInternetAvailable) "Offline Fallback Active" else "Cloud Core Syncing",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }

            // Connection indicator pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isOfflineMode) {
                            if (backendHealth) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f)
                        } else if (!isInternetAvailable) {
                            if (backendHealth) Color(0xFFF59E0B).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f)
                        } else {
                            Color(0xFF3B82F6).copy(alpha = 0.15f)
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (isOfflineMode) {
                            if (backendHealth) Color(0xFF10B981) else Color(0xFFEF4444)
                        } else if (!isInternetAvailable) {
                            if (backendHealth) Color(0xFFF59E0B) else Color(0xFFEF4444)
                        } else {
                            Color(0xFF3B82F6)
                        },
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable { viewModel.checkBackendHealth() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isOfflineMode) {
                                    if (backendHealth) Color(0xFF10B981) else Color(0xFFEF4444)
                                } else if (!isInternetAvailable) {
                                    if (backendHealth) Color(0xFFF59E0B) else Color(0xFFEF4444)
                                } else {
                                    Color(0xFF3B82F6)
                                }
                            )
                    )
                    Text(
                        text = if (isOfflineMode) {
                            if (backendHealth) "Offline Server OK" else "Server Offline"
                        } else if (!isInternetAvailable) {
                            if (backendHealth) "Offline Fallback" else "Connection Alert"
                        } else {
                            "Online Mode"
                        },
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // --- Animated Orb and Mic transcription ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(bottom = 30.dp)
            ) {
                AnimatedOrb(state = state, rmsDb = rmsDb)

                // Central state icon overlays on click
                IconButton(
                    onClick = {
                        if (state == AssistantState.LISTENING) {
                            viewModel.stopListening()
                        } else if (state == AssistantState.SPEAKING) {
                            viewModel.stopSpeaking()
                        } else {
                            viewModel.startListening()
                        }
                    },
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.03f))
                        .testTag("orb_touch_target")
                ) {
                    Icon(
                        imageVector = when (state) {
                            AssistantState.LISTENING -> Icons.Filled.Stop
                            AssistantState.THINKING -> Icons.Filled.HourglassEmpty
                            AssistantState.SPEAKING -> Icons.Filled.VolumeMute
                            AssistantState.IDLE -> Icons.Filled.Mic
                        },
                        contentDescription = "Interact",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // State label text
            Text(
                text = when (state) {
                    AssistantState.IDLE -> "TAP TO SPEAK"
                    AssistantState.LISTENING -> "LISTENING TO YOU..."
                    AssistantState.THINKING -> "THINKING..."
                    AssistantState.SPEAKING -> "SPEAKING RESPONSE..."
                },
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Live Speech Glassmorphic card
            AnimatedVisibility(
                visible = partialText.isNotEmpty() || state == AssistantState.LISTENING,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = partialText.ifEmpty { "Say something, I'm listening..." },
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // --- Bottom Navigation Toolbar & Controls ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Continuous listening toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(30.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Hearing,
                    contentDescription = null,
                    tint = if (isContinuousMode) Color(0xFF10B981) else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Continuous Voice Mode",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = isContinuousMode,
                    onCheckedChange = { viewModel.saveSetting("is_continuous_mode", it.toString()) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF10B981),
                        checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.3f),
                        uncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.scale(0.8f)
                )
            }

            // Quick Toolbar Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateToChat,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.05f))
                        .testTag("toolbar_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Chat,
                        contentDescription = "Chat",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = {
                        if (state == AssistantState.LISTENING) {
                            viewModel.stopListening()
                        } else {
                            viewModel.startListening()
                        }
                    },
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF6366F1), Color(0xFFEC4899))
                            )
                        )
                        .testTag("toolbar_mic_button")
                ) {
                    Icon(
                        imageVector = if (state == AssistantState.LISTENING) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = "Microphone",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = onNavigateToMemory,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.05f))
                        .testTag("toolbar_memory_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Memory,
                        contentDescription = "Memory",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.05f))
                        .testTag("toolbar_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// Extension function to scale the switch cleanly
fun Modifier.scale(scale: Float): Modifier = this.then(
    android.graphics.Matrix().let {
        Modifier
    }
)
