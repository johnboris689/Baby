package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.MessageEntity
import com.example.data.model.AttachmentHandler
import com.example.ui.components.GlassCard
import com.example.ui.components.NeonOrb
import com.example.ui.theme.BabyBackground
import com.example.ui.theme.BabyBlue
import com.example.ui.theme.BabyCyan
import com.example.ui.theme.BabyMuted
import com.example.ui.theme.BabyText
import com.example.ui.theme.BabyViolet
import com.example.ui.viewmodel.AssistantState
import com.example.ui.viewmodel.BabyViewModel

@Composable
fun ChatScreen(
    viewModel: BabyViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.activeMessages.collectAsState()
    val state by viewModel.assistantState.collectAsState()
    val partial by viewModel.partialSpeechText.collectAsState()
    val streaming by viewModel.streamingMessageText.collectAsState()
    val attachments by viewModel.pendingAttachments.collectAsState()
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { uri ->
            AttachmentHandler.processUri(context, uri)?.let(viewModel::addAttachment)
        }
    }

    LaunchedEffect(messages.size, streaming) {
        if (messages.isNotEmpty()) listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
    }

    Box(
        modifier = modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF0A1D38), Color(0xFF040914), BabyBackground), radius = 900f)
        )
    ) {
        Column(Modifier.fillMaxSize().imePadding()) {
            GlassCard(modifier = Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = BabyText) }
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Brush.linearGradient(listOf(BabyBlue, BabyViolet))))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Baby", color = BabyText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            when (state) {
                                AssistantState.LISTENING -> "Listening to you"
                                AssistantState.THINKING -> "Thinking • fast mode"
                                AssistantState.SPEAKING -> "Speaking"
                                AssistantState.IDLE -> "Online • Ready"
                            },
                            color = if (state == AssistantState.LISTENING) BabyCyan else BabyMuted,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = { }) { Icon(Icons.Filled.Tune, "Options", tint = BabyMuted) }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(top = 42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            NeonOrb(active = false, modifier = Modifier.size(118.dp))
                            Spacer(Modifier.height(18.dp))
                            Text("Talk to Baby", color = BabyText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text("Say anything, attach a file, or tap the mic.", color = BabyMuted, fontSize = 12.sp)
                        }
                    }
                }
                items(messages, key = { it.id }) { message -> MessageBubble(message) }
                if (streaming != null) {
                    item(key = "streaming") { AssistantBubble(streaming.orEmpty(), live = true) }
                }
            }

            AnimatedVisibility(partial.isNotBlank(), enter = fadeIn()) {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), shape = RoundedCornerShape(18.dp)) {
                    Text(partial, color = BabyCyan, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
                }
            }

            if (attachments.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    attachments.take(3).forEach { att ->
                        GlassCard(modifier = Modifier.height(34.dp)) {
                            Row(Modifier.padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(att.name.take(18), color = BabyText, fontSize = 10.sp)
                                IconButton(onClick = { viewModel.removeAttachment(att.uri) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Filled.Close, "Remove", tint = BabyMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(28.dp)) {
                Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.Bottom) {
                    IconButton(onClick = { picker.launch("*/*") }) {
                        Icon(Icons.Filled.AttachFile, "Attach", tint = BabyCyan)
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 13.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = BabyText, fontSize = 15.sp),
                        singleLine = false,
                        maxLines = 5,
                        decorationBox = { inner ->
                            if (input.isBlank()) Text("Message Baby…", color = BabyMuted, fontSize = 15.sp)
                            inner()
                        }
                    )
                    IconButton(
                        onClick = {
                            if (state == AssistantState.LISTENING) viewModel.cancelListening() else viewModel.startListening()
                        },
                        modifier = Modifier.size(46.dp).clip(CircleShape).background(if (state == AssistantState.LISTENING) BabyPink else BabyBlue)
                    ) {
                        Icon(if (state == AssistantState.LISTENING) Icons.Filled.Stop else Icons.Filled.Mic, "Microphone", tint = Color.White)
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = { val text = input.trim(); if (text.isNotEmpty() || attachments.isNotEmpty()) { viewModel.sendMessage(text); input = "" } },
                        modifier = Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(listOf(BabyBlue, BabyViolet)))
                    ) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageEntity) {
    val user = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(if (user) .84f else .90f),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(if (user) "YOU" else "BABY", color = if (user) BabyViolet else BabyCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(5.dp))
                Text(message.content, color = BabyText, fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
    }
}

@Composable
private fun AssistantBubble(text: String, live: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        GlassCard(modifier = Modifier.fillMaxWidth(.90f), shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (live) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(BabyCyan))
                    Spacer(Modifier.width(8.dp))
                }
                Text(text.ifBlank { "Thinking…" }, color = BabyText, fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
    }
}
