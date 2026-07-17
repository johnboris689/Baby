package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
import com.example.ui.viewmodel.AssistantState
import com.example.ui.viewmodel.BabyViewModel
import com.example.ui.components.RichMarkdownText
import android.content.Intent
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    viewModel: BabyViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val conversations by viewModel.conversations.collectAsState()
    val activeId by viewModel.activeConversationId.collectAsState()
    val messages by viewModel.activeMessages.collectAsState()
    val assistantState by viewModel.assistantState.collectAsState()
    val isOfflineMode by viewModel.isOfflineMode.collectAsState()
    val isInternetAvailable by viewModel.isInternetAvailable.collectAsState()

    var showSidebar by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }

    var conversationSearchQuery by remember { mutableStateOf("") }
    var showRenameDialogId by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size, assistantState) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val primaryBg = Brush.verticalGradient(
        colors = listOf(Color(0xFF0F172A), Color(0xFF020617))
    )

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(primaryBg)
    ) {
        // --- Sidebar Drawer (Conditional on showSidebar) ---
        if (showSidebar) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(280.dp)
                    .background(Color(0xFF0B0F19))
                    .border(width = 1.dp, color = Color.White.copy(alpha = 0.05f))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Conversations",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { viewModel.createNewConversation("Chat ${conversations.size + 1}") }
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = "New Chat", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Search Box
                OutlinedTextField(
                    value = conversationSearchQuery,
                    onValueChange = { conversationSearchQuery = it },
                    placeholder = { Text("Search chats...", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        focusedContainerColor = Color.White.copy(alpha = 0.03f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    leadingIcon = {
                        Icon(imageVector = Icons.Filled.Search, contentDescription = "Search", tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                    }
                )

                // Quick Actions (Import)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showImportDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Input, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import", color = Color.White, fontSize = 11.sp)
                    }
                }

                val filteredConversations = conversations.filter {
                    it.title.contains(conversationSearchQuery, ignoreCase = true)
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredConversations) { conv ->
                        val isActive = conv.id == activeId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isActive) Color(0xFF3B82F6).copy(alpha = 0.15f) else Color.Transparent)
                                .border(
                                    width = 1.dp,
                                    color = if (isActive) Color(0xFF3B82F6) else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    viewModel.selectConversation(conv.id)
                                    showSidebar = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (conv.isPinned) Icons.Filled.PushPin else Icons.Filled.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = if (isActive) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = conv.title,
                                    color = if (isActive) Color.White else Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }

                            // Conversation settings popdown
                            var showOptions by remember { mutableStateOf(false) }
                            Box {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "Options",
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { showOptions = true }
                                )

                                DropdownMenu(
                                    expanded = showOptions,
                                    onDismissRequest = { showOptions = false },
                                    modifier = Modifier.background(Color(0xFF1E293B))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Pin / Unpin", color = Color.White) },
                                        onClick = {
                                            viewModel.pinConversation(conv.id, !conv.isPinned)
                                            showOptions = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Rename", color = Color.White) },
                                        onClick = {
                                            showRenameDialogId = conv.id
                                            renameText = conv.title
                                            showOptions = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Export", color = Color.White) },
                                        onClick = {
                                            val mdText = "Chat: ${conv.title}\n\n" + messages.joinToString("\n\n") { "**${it.role.uppercase()}**:\n${it.content}" }
                                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, mdText)
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "Export chat"))
                                            showOptions = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = Color.Red) },
                                        onClick = {
                                            viewModel.deleteConversation(conv.id)
                                            showOptions = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Main Chat Column ---
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    IconButton(onClick = { showSidebar = !showSidebar }) {
                        Icon(
                            imageVector = if (showSidebar) Icons.Filled.MenuOpen else Icons.Filled.Menu,
                            contentDescription = "Sidebar",
                            tint = Color.White
                        )
                    }

                    Column {
                        val activeTitle = conversations.find { it.id == activeId }?.title ?: "Baby Chat"
                        Text(
                            text = activeTitle,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val dotColor = if (isOfflineMode) {
                                Color(0xFFF59E0B) // Amber for manual local
                            } else if (!isInternetAvailable) {
                                Color(0xFFEF4444) // Red for offline fallback
                            } else {
                                Color(0xFF10B981) // Green for online Gemini
                            }
                            
                            val statusText = if (isOfflineMode) {
                                "Offline (llama.cpp)"
                            } else if (!isInternetAvailable) {
                                "Offline Fallback"
                            } else {
                                "Online (Gemini)"
                            }

                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Text(
                                text = statusText,
                                color = dotColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Quick clear button
                IconButton(
                    onClick = {
                        activeId?.let { viewModel.deleteConversation(it) }
                    }
                ) {
                    Icon(imageVector = Icons.Filled.DeleteSweep, contentDescription = "Clear Chat", tint = Color.White.copy(alpha = 0.6f))
                }
            }

            // Chat Messages list
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                if (messages.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Chat,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.1f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "How can I help you today?",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Send a text message or speak using the mic.",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    val streamingText by viewModel.streamingMessageText.collectAsState()
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(messages) { msg ->
                                MessageBubble(
                                    message = msg,
                                    onCopy = {
                                        clipboardManager.setText(AnnotatedString(msg.content))
                                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    onShare = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, msg.content)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Message"))
                                    },
                                    onEdit = { edited ->
                                        viewModel.editMessageAndRegenerate(msg.id, edited)
                                    },
                                    onRegenerate = {
                                        viewModel.regenerateResponse()
                                    },
                                    onReactionSelected = { reaction ->
                                        viewModel.updateMessageReaction(msg.id, reaction)
                                    }
                                )
                            }

                            if (streamingText != null) {
                                item {
                                    MessageBubble(
                                        message = MessageEntity(
                                            id = 99999L,
                                            role = "assistant",
                                            content = streamingText ?: "",
                                            conversationId = activeId ?: 0L
                                        ),
                                        onCopy = {},
                                        onShare = {},
                                        isStreaming = true
                                    )
                                }
                            }

                            if (assistantState == AssistantState.THINKING) {
                                item {
                                    ThinkingBubble()
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(60.dp))
                            }
                        }

                        // Floating stop or continue button
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (streamingText != null || assistantState == AssistantState.THINKING || assistantState == AssistantState.SPEAKING) {
                                Button(
                                    onClick = { viewModel.interruptGeneration() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Interrupt Baby", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else if (messages.isNotEmpty() && messages.last().role == "assistant") {
                                Button(
                                    onClick = { viewModel.continueGenerating() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Continue generating", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Animated transition between voice recording panel and standard text input field
            AnimatedContent(
                targetState = assistantState,
                transitionSpec = {
                    slideInVertically { height -> height } + fadeIn() togetherWith
                            slideOutVertically { height -> height } + fadeOut()
                },
                label = "input_panel_transition"
            ) { state ->
                if (state == AssistantState.LISTENING) {
                    RecordingInterfacePanel(viewModel = viewModel)
                } else {
                    // Input panel
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A).copy(alpha = 0.6f))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Quick attachment shortcuts
                            IconButton(
                                onClick = {
                                    Toast.makeText(context, "Image attachments coming in next release!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.05f))
                            ) {
                                Icon(imageVector = Icons.Filled.AttachFile, contentDescription = "Attach File", tint = Color.White.copy(alpha = 0.6f))
                            }

                            // Text input field
                            TextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = { Text("Message Baby...", color = Color.White.copy(alpha = 0.4f)) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(24.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                                    .testTag("chat_input_field"),
                                maxLines = 4
                            )

                            // Send or Mic Action button
                            IconButton(
                                onClick = {
                                    if (inputText.trim().isNotEmpty()) {
                                        viewModel.sendMessage(inputText)
                                        inputText = ""
                                    } else {
                                        viewModel.startListening()
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (inputText.trim().isNotEmpty()) {
                                            Brush.linearGradient(colors = listOf(Color(0xFF3B82F6), Color(0xFF6366F1)))
                                        } else {
                                            Brush.linearGradient(colors = listOf(Color(0xFFEC4899), Color(0xFF8B5CF6)))
                                        }
                                    )
                                    .testTag("chat_send_button")
                            ) {
                                Icon(
                                    imageVector = if (inputText.trim().isNotEmpty()) Icons.AutoMirrored.Filled.Send else Icons.Filled.Mic,
                                    contentDescription = "Send",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialogId != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialogId = null },
            title = { Text("Rename Chat", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRenameDialogId?.let { id ->
                            viewModel.renameConversation(id, renameText)
                        }
                        showRenameDialogId = null
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialogId = null }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Conversation", color = Color.White) },
            text = {
                Column {
                    Text("Paste chat content or prompt to initialize a new conversation:", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier.height(120.dp).fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importText.trim().isNotEmpty()) {
                            coroutineScope.launch {
                                viewModel.createNewConversation("Imported Chat")
                                viewModel.sendMessage(importText)
                            }
                        }
                        showImportDialog = false
                        importText = ""
                    }
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}

@Composable
fun MessageBubble(
    message: MessageEntity,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onEdit: (String) -> Unit = {},
    onRegenerate: () -> Unit = {},
    onReactionSelected: (String?) -> Unit = {},
    isStreaming: Boolean = false
) {
    val isUser = message.role == "user"
    var isEditing by remember { mutableStateOf(false) }
    var editedText by remember { mutableStateOf(message.content) }
    var showReactionMenu by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition()
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_blink"
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(colors = listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))))
                    .align(Alignment.Top),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "B", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .background(
                        if (isUser) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.06f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isUser) Color(0xFF2563EB) else Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .padding(12.dp)
            ) {
                Column {
                    if (isEditing) {
                        OutlinedTextField(
                            value = editedText,
                            onValueChange = { editedText = it },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White.copy(alpha = 0.4f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            TextButton(onClick = { isEditing = false }) {
                                Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                            }
                            Button(
                                onClick = {
                                    onEdit(editedText)
                                    isEditing = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                            ) {
                                Text("Save", color = Color.White)
                            }
                        }
                    } else {
                        SelectionContainer {
                            Column {
                                val textWithCursor = if (isStreaming) {
                                    val cursorStr = if (cursorAlpha > 0.5f) "█" else " "
                                    "${message.content}$cursorStr"
                                } else {
                                    message.content
                                }
                                RichMarkdownText(
                                    content = textWithCursor,
                                    textColor = if (message.isError) Color(0xFFF87171) else Color.White
                                )
                            }
                        }
                    }

                    if (message.reaction != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .align(Alignment.Start)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .clickable { onReactionSelected(null) }
                        ) {
                            Text(text = message.reaction ?: "", fontSize = 11.sp)
                        }
                    }
                }
            }

            if (!isEditing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
                ) {
                    Text(
                        text = if (isUser) "You" else "Baby",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 10.sp
                    )

                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy",
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier
                            .size(13.dp)
                            .clickable { onCopy() }
                    )

                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share",
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier
                            .size(13.dp)
                            .clickable { onShare() }
                    )

                    if (isUser) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit",
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier
                                .size(13.dp)
                                .clickable { isEditing = true }
                        )
                    } else if (!isStreaming) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Regenerate",
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier
                                .size(13.dp)
                                .clickable { onRegenerate() }
                        )
                    }

                    // Reaction picker
                    Box {
                        Text(
                            text = "❤️",
                            fontSize = 10.sp,
                            modifier = Modifier
                                .clickable { showReactionMenu = !showReactionMenu }
                                .padding(horizontal = 2.dp)
                        )

                        DropdownMenu(
                            expanded = showReactionMenu,
                            onDismissRequest = { showReactionMenu = false },
                            modifier = Modifier.background(Color(0xFF1E293B))
                        ) {
                            Row(
                                modifier = Modifier.padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("👍", "👎", "❤️", "😂", "😮", "🎉").forEach { reaction ->
                                    Text(
                                        text = reaction,
                                        fontSize = 16.sp,
                                        modifier = Modifier
                                            .clickable {
                                                onReactionSelected(reaction)
                                                showReactionMenu = false
                                            }
                                            .padding(4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3B82F6).copy(alpha = 0.5f))
                    .align(Alignment.Top),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "U", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ThinkingBubble() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(colors = listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)))),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "B", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp,
                    color = Color(0xFFEC4899)
                )
                Text(
                    text = "Baby is formulating a response...",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun RecordingInterfacePanel(
    viewModel: BabyViewModel,
    modifier: Modifier = Modifier
) {
    val rmsDb by viewModel.rmsDb.collectAsState()
    val partialText by viewModel.partialSpeechText.collectAsState()

    // Map RMS from dB to a scale that is visually appealing (e.g. 0 to 1)
    // rmsDb typically ranges from -10 to 10 or so. Let's map it smoothly.
    val dbScale = (rmsDb + 10f).coerceIn(0f, 25f) / 25f

    // Smooth transition for the scaling of elements
    val animatedScale by animateFloatAsState(
        targetValue = 1f + dbScale * 0.4f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 150f),
        label = "waveform_pulse_scale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A))
            .border(width = 1.dp, color = Color(0xFFEC4899).copy(alpha = 0.2f), shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Listening status header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEC4899))
            )
            Text(
                text = "LISTENING",
                color = Color(0xFFEC4899),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }

        // Live transcription area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp, max = 120.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.03f))
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            if (partialText.trim().isNotEmpty()) {
                Text(
                    text = "\"$partialText\"",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            } else {
                Text(
                    text = "Speak now, transcribing in real-time...",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Animated waveform / pulse circle
        Box(
            modifier = Modifier
                .height(80.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Background pulsing circles
            Box(
                modifier = Modifier
                    .size(72.dp * animatedScale)
                    .clip(CircleShape)
                    .background(Color(0xFFEC4899).copy(alpha = 0.1f))
            )
            Box(
                modifier = Modifier
                    .size(54.dp * (1f + dbScale * 0.2f))
                    .clip(CircleShape)
                    .background(Color(0xFF8B5CF6).copy(alpha = 0.15f))
            )

            // Equalizer Bars
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val barHeights = listOf(0.3f, 0.6f, 0.9f, 0.7f, 0.4f, 0.8f, 0.5f, 0.2f, 0.6f, 0.3f)
                barHeights.forEachIndexed { index, baseHeight ->
                    // Modulate height with rmsDb scale and a bit of variation
                    val variableScale = if (index % 2 == 0) dbScale else dbScale * 0.8f
                    val heightFactor = (baseHeight + variableScale).coerceIn(0.1f, 1.2f)
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(36.dp * heightFactor)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFFEC4899), Color(0xFF8B5CF6))
                                )
                            )
                    )
                }
            }
        }

        // Action Buttons Row (Cancel, Done)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel button
            OutlinedButton(
                onClick = { viewModel.cancelListening() },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("voice_cancel_button"),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }

            // Stop / Done button
            Button(
                onClick = { viewModel.stopListening() },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("voice_done_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEC4899),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Finish", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
