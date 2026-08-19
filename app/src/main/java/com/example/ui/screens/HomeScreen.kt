package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.components.HolographicCore
import com.example.ui.theme.BabyBackground
import com.example.ui.theme.BabyBlue
import com.example.ui.theme.BabyCyan
import com.example.ui.theme.BabyMuted
import com.example.ui.theme.BabyPink
import com.example.ui.theme.BabyText
import com.example.ui.theme.BabyViolet
import com.example.ui.viewmodel.AssistantState
import com.example.ui.viewmodel.BabyViewModel

data class CommandActionItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color,
    val onClick: () -> Unit
)

data class AiCapabilityItem(
    val label: String,
    val status: String,
    val color: Color
)

@Composable
fun HomeScreen(
    viewModel: BabyViewModel,
    onNavigateToChat: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToTranslate: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.assistantState.collectAsState()
    val rmsDb by viewModel.rmsDb.collectAsState()
    val partial by viewModel.partialSpeechText.collectAsState()
    val mood by viewModel.moodSignal.collectAsState()
    val backgroundVoice by viewModel.backgroundServiceEnabled.collectAsState()
    val babyStatus by viewModel.babyStatus.collectAsState()

    val commandActions = listOf(
        CommandActionItem(
            title = "Chat Matrix",
            subtitle = "Direct Dialogue",
            icon = Icons.Filled.ChatBubbleOutline,
            accentColor = BabyCyan,
            onClick = onNavigateToChat
        ),
        CommandActionItem(
            title = "Voice Nexus",
            subtitle = "Live Speech",
            icon = Icons.Filled.GraphicEq,
            accentColor = Color(0xFF3B82F6),
            onClick = {
                viewModel.enterVoiceMode()
                onNavigateToChat()
            }
        ),
        CommandActionItem(
            title = "Neural Translate",
            subtitle = "Multilingual",
            icon = Icons.Filled.Translate,
            accentColor = BabyViolet,
            onClick = onNavigateToTranslate
        ),
        CommandActionItem(
            title = "Memory Drive",
            subtitle = "Infinite Context",
            icon = Icons.Filled.Memory,
            accentColor = BabyPink,
            onClick = onNavigateToMemory
        ),
        CommandActionItem(
            title = "Vision & Files",
            subtitle = "Deep Analysis",
            icon = Icons.Filled.FolderOpen,
            accentColor = Color(0xFF10B981),
            onClick = onNavigateToChat
        ),
        CommandActionItem(
            title = "System Core",
            subtitle = "Brain & Settings",
            icon = Icons.Filled.Settings,
            accentColor = Color(0xFF94A3B8),
            onClick = onNavigateToSettings
        )
    )

    val capabilities = listOf(
        AiCapabilityItem("Conversation", "Active", BabyCyan),
        AiCapabilityItem("Vision", "Active", Color(0xFF10B981)),
        AiCapabilityItem("File Analysis", "Active", BabyBlue),
        AiCapabilityItem("Translation", "Multilingual", BabyViolet),
        AiCapabilityItem("Infinite Memory", "Ready", BabyPink),
        AiCapabilityItem("Voice Nexus", "Live", Color(0xFF3B82F6)),
        AiCapabilityItem("Automation", "Active", Color(0xFFF59E0B))
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF091730),
                        Color(0xFF040814),
                        BabyBackground
                    ),
                    radius = 1200f
                )
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // HUD Top Command Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "BABY",
                            color = BabyText,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "NEXUS",
                            color = BabyCyan,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = 3.sp
                        )
                    }
                    Text(
                        "ADVANCED AI COMMAND CENTER",
                        color = BabyMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }

                // Dynamic Status Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                        .clickable(onClick = onNavigateToSettings)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(babyStatus.indicatorColor)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = babyStatus.statusText,
                        color = if (state != AssistantState.IDLE) babyStatus.indicatorColor else BabyText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Central Holographic AI Core Stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.04f),
                                Color(0xFF091428).copy(alpha = 0.65f),
                                Color.White.copy(alpha = 0.02f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                BabyCyan.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.08f),
                                BabyViolet.copy(alpha = 0.20f)
                            )
                        ),
                        shape = RoundedCornerShape(26.dp)
                    )
                    .padding(vertical = 18.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Holographic AI Core
                    HolographicCore(
                        state = state,
                        rmsDb = rmsDb,
                        isOnline = babyStatus.isOnline,
                        size = 195.dp,
                        onClick = {
                            when (state) {
                                AssistantState.LISTENING -> viewModel.stopListening()
                                AssistantState.SPEAKING -> viewModel.stopSpeaking()
                                else -> viewModel.startListening()
                            }
                        }
                    )

                    Spacer(Modifier.height(12.dp))

                    // Real-time State & Transcript Feedback
                    Text(
                        text = when (state) {
                            AssistantState.LISTENING -> "LISTENING TO SPEECH"
                            AssistantState.THINKING -> "NEURAL PROCESSING"
                            AssistantState.SPEAKING -> "AUDIO SYNTHESIS"
                            AssistantState.IDLE -> if (babyStatus.isOnline) "BABY READY FOR COMMAND" else babyStatus.statusText.uppercase()
                        },
                        color = when (state) {
                            AssistantState.LISTENING -> BabyCyan
                            AssistantState.THINKING -> BabyViolet
                            AssistantState.SPEAKING -> Color(0xFF3B82F6)
                            AssistantState.IDLE -> BabyMuted
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )

                    AnimatedVisibility(
                        visible = partial.isNotBlank(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Text(
                            text = "“$partial”",
                            color = BabyCyan,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp, start = 12.dp, end = 12.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Background Wake Word Voice Row
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(BabyCyan.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = BabyCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Background Wake Word",
                                color = BabyText,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                "Hands-free trigger: “Hey Baby”",
                                color = BabyMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Switch(
                        checked = backgroundVoice,
                        onCheckedChange = viewModel::updateBackgroundServiceState,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF2563EB),
                            uncheckedTrackColor = Color(0xFF1E293B)
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Command Matrix Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "COMMAND MATRIX",
                    color = BabyMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    "6 NODES ONLINE",
                    color = BabyCyan.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(10.dp))

            // Command Actions Grid (2 Columns)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (i in commandActions.indices step 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            CommandActionCard(commandActions[i])
                        }
                        if (i + 1 < commandActions.size) {
                            Box(modifier = Modifier.weight(1f)) {
                                CommandActionCard(commandActions[i + 1])
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // AI Capabilities Strip
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "AI CAPABILITIES",
                            color = BabyCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            "Multimodal Intelligence",
                            color = BabyMuted,
                            fontSize = 10.sp
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        capabilities.take(3).forEach { cap ->
                            CapabilityPill(cap, Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        capabilities.drop(3).take(3).forEach { cap ->
                            CapabilityPill(cap, Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Primary Holographic Voice Action Trigger
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF1D4ED8),
                                Color(0xFF7C3AED),
                                Color(0xFF06B6D4)
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                    .clickable {
                        when (state) {
                            AssistantState.LISTENING -> viewModel.stopListening()
                            AssistantState.SPEAKING -> viewModel.stopSpeaking()
                            else -> viewModel.startListening()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = when (state) {
                            AssistantState.LISTENING -> Icons.Filled.Stop
                            AssistantState.SPEAKING -> Icons.Filled.VolumeOff
                            else -> Icons.Filled.Mic
                        },
                        contentDescription = "Voice Activation",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = when (state) {
                            AssistantState.LISTENING -> "TAP TO STOP LISTENING"
                            AssistantState.SPEAKING -> "TAP TO STOP SPEAKING"
                            else -> "SPEAK WITH BABY"
                        },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Tap to activate speech  •  Say “Hey Baby” anytime",
                color = BabyMuted.copy(alpha = 0.7f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CommandActionCard(item: CommandActionItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.06f),
                        Color(0xFF0D182E).copy(alpha = 0.75f),
                        Color.White.copy(alpha = 0.02f)
                    )
                )
            )
            .border(
                1.dp,
                item.accentColor.copy(alpha = 0.22f),
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = item.onClick)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(item.accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = item.accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = BabyText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.subtitle,
                    color = BabyMuted,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun CapabilityPill(item: AiCapabilityItem, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(0.5.dp, item.color.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
            .padding(vertical = 6.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = item.label,
                color = BabyText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = item.status,
                color = item.color,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
