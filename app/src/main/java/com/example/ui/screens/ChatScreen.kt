package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.baby.ai.R
import com.example.data.local.entity.MessageEntity
import com.example.data.model.Attachment
import com.example.data.model.AttachmentHandler
import com.example.ui.components.BabyAvatar
import com.example.ui.components.GlassCard
import com.example.ui.components.NeonOrb
import com.example.ui.components.RichMarkdownText
import com.example.ui.theme.BabyBackground
import com.example.ui.theme.BabyBlue
import com.example.ui.theme.BabyCyan
import com.example.ui.theme.BabyMuted
import com.example.ui.theme.BabyPink
import com.example.ui.theme.BabyText
import com.example.ui.theme.BabyViolet
import com.example.ui.viewmodel.AssistantState
import com.example.ui.viewmodel.BabyViewModel

private val ChatBarBg = Color(0xFF151821)
private val ChatBarBorder = Color(0xFF2C3242)
private val ElectricBlue = Color(0xFF2563EB)
private val ElectricBlueGlow = Color(0xFF3B82F6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: BabyViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.activeMessages.collectAsState()
    val state by viewModel.assistantState.collectAsState()
    val rmsDb by viewModel.rmsDb.collectAsState()
    val partial by viewModel.partialSpeechText.collectAsState()
    val streaming by viewModel.streamingMessageText.collectAsState()
    val attachments by viewModel.pendingAttachments.collectAsState()
    val isVoiceModeActive by viewModel.isVoiceModeActive.collectAsState()
    val isDictating by viewModel.isDictating.collectAsState()
    val mood by viewModel.moodSignal.collectAsState()
    val babyStatus by viewModel.babyStatus.collectAsState()
    val context = LocalContext.current

    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var showAttachmentSheet by remember { mutableStateOf(false) }

    // Collect dictated text directly into input field
    LaunchedEffect(Unit) {
        viewModel.dictatedText.collect { recognized ->
            if (recognized.isNotBlank()) {
                input = if (input.isBlank()) recognized else "$input $recognized"
            }
        }
    }

    // Permission Launchers
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.startDictation()
        }
    }

    val voiceModeMicPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.enterVoiceMode()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            showAttachmentSheet = true
        }
    }

    // Activity Result Launchers for Attachments
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { uri ->
            AttachmentHandler.processUri(context, uri)?.let(viewModel::addAttachment)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { uri ->
            AttachmentHandler.processUri(context, uri)?.let(viewModel::addAttachment)
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { uri ->
            AttachmentHandler.processUri(context, uri)?.let(viewModel::addAttachment)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            AttachmentHandler.processBitmap(context, bitmap)?.let(viewModel::addAttachment)
        }
    }

    LaunchedEffect(messages.size, streaming) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF0A1D38), Color(0xFF040914), BabyBackground),
                    radius = 900f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Top Bar
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = BabyText)
                    }
                    BabyAvatar(
                        state = state,
                        size = 38.dp,
                        rmsDb = rmsDb
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Baby", color = BabyText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(babyStatus.indicatorColor)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = babyStatus.statusText,
                                color = if (state != AssistantState.IDLE) babyStatus.indicatorColor else BabyMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    IconButton(onClick = { }) {
                        Icon(Icons.Filled.Tune, "Options", tint = BabyMuted)
                    }
                }
            }

            // Message List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 42.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            NeonOrb(active = false, modifier = Modifier.size(118.dp))
                            Spacer(Modifier.height(18.dp))
                            Text("Talk to Baby", color = BabyText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text("Say anything, attach a file, or tap the mic.", color = BabyMuted, fontSize = 12.sp)
                        }
                    }
                }
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }
                if (streaming != null) {
                    item(key = "streaming") {
                        AssistantBubble(streaming.orEmpty(), live = true)
                    }
                }
            }

            // Real-time Dictation / Speech Banner
            AnimatedVisibility(
                visible = isDictating && partial.isNotBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1E2433).copy(alpha = 0.9f))
                        .border(1.dp, BabyCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(BabyCyan)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = partial,
                            color = BabyCyan,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Pending Attachment Previews
            if (attachments.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(attachments) { att ->
                        AttachmentChip(
                            attachment = att,
                            onRemove = { viewModel.removeAttachment(att.uri) }
                        )
                    }
                }
            }

            // Redesigned ChatGPT-Style Compact Pill Chat Input Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(ChatBarBg)
                        .border(1.dp, ChatBarBorder, RoundedCornerShape(32.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Plus / Attachment Button (Far Left)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable { showAttachmentSheet = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add attachments",
                                tint = BabyText,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(Modifier.width(6.dp))

                        // 2. Compact Message Input Field
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp, vertical = 10.dp),
                            textStyle = TextStyle(
                                color = BabyText,
                                fontSize = 15.sp,
                                lineHeight = 20.sp
                            ),
                            singleLine = false,
                            maxLines = 4,
                            decorationBox = { innerTextField ->
                                if (input.isEmpty()) {
                                    Text(
                                        text = "Message Baby...",
                                        color = Color(0xFF8E95A5),
                                        fontSize = 15.sp
                                    )
                                }
                                innerTextField()
                            }
                        )

                        Spacer(Modifier.width(4.dp))

                        // 3. Microphone Button (Speech to Text)
                        val isMicActive = isDictating
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isMicActive) BabyCyan.copy(alpha = 0.25f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    if (isMicActive) {
                                        viewModel.stopDictation()
                                    } else {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            viewModel.startDictation()
                                        } else {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isMicActive) Icons.Filled.Stop else Icons.Filled.Mic,
                                contentDescription = "Dictation microphone",
                                tint = if (isMicActive) BabyCyan else Color(0xFF9EACB9),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(Modifier.width(4.dp))

                        // 4. Blue Voice Mode / Send Button (Far Right)
                        val hasTextToSend = input.trim().isNotEmpty() || attachments.isNotEmpty()

                        if (hasTextToSend) {
                            // Upward Send Arrow when typing
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(ElectricBlueGlow, ElectricBlue)
                                        )
                                    )
                                    .clickable {
                                        val toSend = input.trim()
                                        if (toSend.isNotEmpty() || attachments.isNotEmpty()) {
                                            viewModel.sendMessage(toSend)
                                            input = ""
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowUpward,
                                    contentDescription = "Send message",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            // Prominent Blue Circular Voice Mode Button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .shadow(6.dp, CircleShape, spotColor = ElectricBlue)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(ElectricBlueGlow, ElectricBlue)
                                        )
                                    )
                                    .clickable {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            viewModel.enterVoiceMode()
                                        } else {
                                            voiceModeMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                WaveformVoiceIcon(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // Attachment Action Bottom Sheet
        if (showAttachmentSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showAttachmentSheet = false },
                sheetState = sheetState,
                containerColor = Color(0xFF131722),
                contentColor = BabyText,
                dragHandle = {
                    Box(
                        Modifier
                            .padding(vertical = 10.dp)
                            .size(width = 38.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .navigationBarsPadding()
                ) {
                    Text(
                        text = "Add to conversation",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = BabyText,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AttachmentOptionItem(
                            icon = Icons.Filled.CameraAlt,
                            title = "Camera",
                            color = BabyPink,
                            onClick = {
                                showAttachmentSheet = false
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    cameraLauncher.launch(null)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        )

                        AttachmentOptionItem(
                            icon = Icons.Filled.Image,
                            title = "Photos",
                            color = BabyCyan,
                            onClick = {
                                showAttachmentSheet = false
                                imagePickerLauncher.launch("image/*")
                            }
                        )

                        AttachmentOptionItem(
                            icon = Icons.Filled.PictureAsPdf,
                            title = "PDFs",
                            color = Color(0xFFEF4444),
                            onClick = {
                                showAttachmentSheet = false
                                pdfPickerLauncher.launch("application/pdf")
                            }
                        )

                        AttachmentOptionItem(
                            icon = Icons.Filled.InsertDriveFile,
                            title = "Files",
                            color = BabyViolet,
                            onClick = {
                                showAttachmentSheet = false
                                filePickerLauncher.launch("*/*")
                            }
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                }
            }
        }

        // Immersive Real-Time Voice Conversation Mode Overlay
        AnimatedVisibility(
            visible = isVoiceModeActive,
            enter = fadeIn() + scaleIn(initialScale = 0.95f),
            exit = fadeOut() + scaleOut(targetScale = 0.95f)
        ) {
            VoiceModeScreenOverlay(
                viewModel = viewModel,
                onClose = { viewModel.exitVoiceMode() }
            )
        }
    }
}

/**
 * Waveform voice icon matching the prominent Voice Mode button in ChatGPT.
 */
@Composable
fun WaveformVoiceIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val barHeights = listOf(7.dp, 15.dp, 18.dp, 11.dp)
        barHeights.forEach { height ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 1.dp)
                    .width(2.2.dp)
                    .height(height)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun AttachmentOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f))
                .border(1.dp, color.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            color = BabyText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AttachmentChip(
    attachment: Attachment,
    onRemove: () -> Unit
) {
    val icon = when {
        attachment.isPdf -> Icons.Filled.PictureAsPdf
        attachment.isImage -> Icons.Filled.Image
        else -> Icons.Filled.Description
    }

    val iconColor = when {
        attachment.isPdf -> Color(0xFFEF4444)
        attachment.isImage -> BabyCyan
        else -> BabyViolet
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1C212E))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = attachment.name.take(16),
                color = BabyText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove",
                    tint = BabyMuted,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun VoiceModeScreenOverlay(
    viewModel: BabyViewModel,
    onClose: () -> Unit
) {
    val state by viewModel.assistantState.collectAsState()
    val rmsDb by viewModel.rmsDb.collectAsState()
    val partial by viewModel.partialSpeechText.collectAsState()
    val streaming by viewModel.streamingMessageText.collectAsState()
    val messages by viewModel.activeMessages.collectAsState()
    val babyStatus by viewModel.babyStatus.collectAsState()

    val lastMessage = streaming ?: messages.lastOrNull()?.content.orEmpty()
    val active = state != AssistantState.IDLE

    val statusText = when (state) {
        AssistantState.LISTENING -> "Listening..."
        AssistantState.THINKING -> "Thinking..."
        AssistantState.SPEAKING -> "Speaking..."
        AssistantState.IDLE -> if (babyStatus.isOnline) "Baby Nexus Ready" else babyStatus.statusText
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B14).copy(alpha = 0.97f))
            .navigationBarsPadding()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(ElectricBlue)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Voice Mode",
                        color = BabyText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close Voice Mode",
                        tint = BabyText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Center pulsating Neon Orb for real-time voice
            NeonOrb(active = active, modifier = Modifier.size(170.dp))

            Spacer(Modifier.height(28.dp))

            // Status Indicator
            Text(
                text = statusText,
                color = when (state) {
                    AssistantState.LISTENING -> BabyCyan
                    AssistantState.THINKING -> BabyViolet
                    AssistantState.SPEAKING -> ElectricBlueGlow
                    AssistantState.IDLE -> BabyMuted
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(14.dp))

            // Live speech & subtitle transcript
            val displayText = when {
                partial.isNotBlank() -> partial
                lastMessage.isNotBlank() -> lastMessage.take(160)
                else -> "Speak in any language — Baby is listening."
            }

            Text(
                text = displayText,
                color = if (partial.isNotBlank()) BabyCyan else BabyMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .height(60.dp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.weight(1f))

            // Bottom Voice Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                IconButton(
                    onClick = {
                        if (state == AssistantState.SPEAKING) {
                            viewModel.stopSpeaking()
                        } else if (state == AssistantState.LISTENING) {
                            viewModel.stopListening()
                        } else {
                            viewModel.startListening()
                        }
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = if (state == AssistantState.SPEAKING) Icons.Filled.VolumeOff else if (state == AssistantState.LISTENING) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = "Toggle Mute / Mic",
                        tint = BabyText,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444).copy(alpha = 0.25f))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f), CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "End Voice Mode",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageEntity) {
    val user = message.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (user) 0.84f else 0.92f)
                .clip(
                    if (user) RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                    else RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                )
                .background(
                    if (user) Color(0xFF141D30).copy(alpha = 0.92f)
                    else Color(0xFF0C1424).copy(alpha = 0.88f)
                )
                .border(
                    width = 1.dp,
                    color = if (user) Color.White.copy(alpha = 0.08f) else BabyCyan.copy(alpha = 0.22f),
                    shape = if (user) RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                    else RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!user) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(BabyCyan)
                        )
                    }
                    Text(
                        text = if (user) "YOU" else "BABY",
                        color = if (user) BabyViolet else BabyCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (user) {
                    Text(
                        text = message.content,
                        color = BabyText,
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    )
                } else {
                    RichMarkdownText(
                        content = message.content,
                        textColor = BabyText
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantBubble(text: String, live: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                .background(Color(0xFF0C1424).copy(alpha = 0.88f))
                .border(1.dp, BabyCyan.copy(alpha = 0.25f), RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(BabyCyan)
                    )
                    Text(
                        text = "BABY",
                        color = BabyCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (text.isBlank()) {
                    Text(
                        text = "Thinking…",
                        color = BabyMuted,
                        fontSize = 14.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                } else {
                    RichMarkdownText(
                        content = text,
                        textColor = BabyText
                    )
                }
            }
        }
    }
}
