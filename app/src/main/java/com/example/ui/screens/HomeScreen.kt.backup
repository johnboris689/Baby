package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baby.ai.R
import com.example.ui.components.GlassCard
import com.example.ui.components.NeonOrb
import com.example.ui.theme.BabyBackground
import com.example.ui.theme.BabyBlue
import com.example.ui.theme.BabyCyan
import com.example.ui.theme.BabyGreen
import com.example.ui.theme.BabyMuted
import com.example.ui.theme.BabyPink
import com.example.ui.theme.BabyText
import com.example.ui.theme.BabyViolet
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
    val partial by viewModel.partialSpeechText.collectAsState()
    val mood by viewModel.moodSignal.collectAsState()
    val continuous by viewModel.isContinuousMode.collectAsState()
    val backgroundVoice by viewModel.backgroundServiceEnabled.collectAsState()
    val internet by viewModel.isInternetAvailable.collectAsState()

    val active = state != AssistantState.IDLE
    val status = when (state) {
        AssistantState.LISTENING -> "Listening"
        AssistantState.THINKING -> "Thinking"
        AssistantState.SPEAKING -> "Speaking"
        AssistantState.IDLE -> if (backgroundVoice) "Listening in background" else "Ready"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0B1E3A), Color(0xFF050A15), BabyBackground),
                    radius = 900f
                )
            )
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.baby_icon),
                        contentDescription = "Baby",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                            .border(1.dp, BabyCyan.copy(alpha = .5f), CircleShape)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Baby", color = BabyText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(if (internet) BabyGreen else Color(0xFFF59E0B)))
                            Spacer(Modifier.width(6.dp))
                            Text(if (internet) "Online" else "Offline mode", color = BabyMuted, fontSize = 12.sp)
                        }
                    }
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Filled.Settings, "Settings", tint = BabyText)
                }
            }

            Spacer(Modifier.height(20.dp))

            GlassCard(modifier = Modifier.fillMaxWidth().height(210.dp)) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    NeonOrb(active = active, modifier = Modifier.size(132.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(status.uppercase(), color = BabyText, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    AnimatedVisibility(partial.isNotBlank(), enter = fadeIn()) {
                        Text(partial, color = BabyCyan, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Background voice", color = BabyText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Wake word: Hey Baby", color = BabyMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = backgroundVoice,
                        onCheckedChange = viewModel::updateBackgroundServiceState,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BabyBlue)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassStat("Memory", "∞", BabyViolet, Modifier.weight(1f))
                GlassStat("Mood", mood.emotion.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }, BabyPink, Modifier.weight(1f))
                GlassStat("Voice", if (continuous) "Live" else "Ready", BabyCyan, Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))

            Text("Quick Access", color = BabyMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                item { QuickAction(Icons.Filled.ChatBubbleOutline, "Chat", onNavigateToChat) }
                item { QuickAction(Icons.Filled.FolderOpen, "Files", onNavigateToChat) }
                item { QuickAction(Icons.Filled.Memory, "Memory", onNavigateToMemory) }
                item { QuickAction(Icons.Filled.Settings, "System", onNavigateToSettings) }
            }

            Spacer(Modifier.weight(1f))

            IconButton(
                onClick = {
                    when (state) {
                        AssistantState.LISTENING -> viewModel.stopListening()
                        AssistantState.SPEAKING -> viewModel.stopSpeaking()
                        else -> viewModel.startListening()
                    }
                },
                modifier = Modifier.size(78.dp).clip(CircleShape).background(Brush.linearGradient(listOf(BabyBlue, BabyViolet, BabyPink)))
                    .border(2.dp, Color.White.copy(alpha = .25f), CircleShape)
            ) {
                Icon(
                    when (state) {
                        AssistantState.LISTENING -> Icons.Filled.Stop
                        AssistantState.SPEAKING -> Icons.Filled.VolumeOff
                        else -> Icons.Filled.Mic
                    }, "Voice", tint = Color.White, modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text("Tap to speak  •  Say “Hey Baby”", color = BabyMuted, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GlassStat(title: String, value: String, accent: Color, modifier: Modifier) {
    GlassCard(modifier = modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(title, color = BabyMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    GlassCard(modifier = Modifier.width(82.dp).height(72.dp).clickable(onClick = onClick)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = BabyCyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(5.dp))
            Text(title, color = BabyText, fontSize = 10.sp)
        }
    }
}
