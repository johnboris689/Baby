package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.components.GlassCard
import com.example.ui.theme.BabyBackground
import com.example.ui.theme.BabyBlue
import com.example.ui.theme.BabyCyan
import com.example.ui.theme.BabyMuted
import com.example.ui.theme.BabyPink
import com.example.ui.theme.BabyText
import com.example.ui.theme.BabyViolet
import com.example.ui.viewmodel.AssistantState
import com.example.ui.viewmodel.BabyViewModel

private val SUPPORTED_LANGUAGES = listOf(
    "Auto Detect", "English", "Spanish", "French", "German",
    "Japanese", "Chinese (Mandarin)", "Arabic", "Hindi", "Portuguese",
    "Russian", "Korean", "Italian", "Dutch", "Turkish",
    "Vietnamese", "Polish", "Swedish", "Indonesian", "Filipino",
    "Thai", "Greek", "Hebrew", "Czech", "Ukrainian"
)

@Composable
fun TranslationScreen(
    viewModel: BabyViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val state by viewModel.assistantState.collectAsState()
    val isTranslating by viewModel.isTranslating.collectAsState()
    val translationResult by viewModel.translationResult.collectAsState()
    val isDictating by viewModel.isDictating.collectAsState()
    val babyStatus by viewModel.babyStatus.collectAsState()

    var sourceText by remember { mutableStateOf("") }
    var fromLanguage by remember { mutableStateOf("Auto Detect") }
    var toLanguage by remember { mutableStateOf("Spanish") }
    var showFromMenu by remember { mutableStateOf(false) }
    var showToMenu by remember { mutableStateOf(false) }
    var swapRotation by remember { mutableStateOf(0f) }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.startDictation()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.dictatedText.collect { recognized ->
            if (recognized.isNotBlank()) {
                sourceText = if (sourceText.isBlank()) recognized else "$sourceText $recognized"
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0D1B36), Color(0xFF050B18), BabyBackground),
                    radius = 1100f
                )
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // HUD Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = BabyText)
                    }
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(
                            "TRANSLATION ENGINE",
                            color = BabyCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            "Neural Multilingual",
                            color = BabyText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(babyStatus.indicatorColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (babyStatus.isOnline) "Neural Online" else "Offline",
                        color = BabyMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Language Selector Bar
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // From Language Dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .clickable { showFromMenu = true }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = fromLanguage,
                                color = BabyText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Icon(Icons.Filled.ArrowDropDown, null, tint = BabyMuted)
                        }

                        DropdownMenu(
                            expanded = showFromMenu,
                            onDismissRequest = { showFromMenu = false },
                            modifier = Modifier.background(Color(0xFF0F172A))
                        ) {
                            SUPPORTED_LANGUAGES.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang, color = BabyText, fontSize = 13.sp) },
                                    onClick = {
                                        fromLanguage = lang
                                        showFromMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Swap Button
                    IconButton(
                        onClick = {
                            if (fromLanguage != "Auto Detect") {
                                swapRotation += 180f
                                val temp = fromLanguage
                                fromLanguage = toLanguage
                                toLanguage = temp
                                val tempText = sourceText
                                sourceText = translationResult
                                if (tempText.isNotBlank()) {
                                    viewModel.translate(sourceText, fromLanguage, toLanguage)
                                }
                            }
                        },
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BabyCyan.copy(alpha = 0.15f))
                    ) {
                        val animatedRotation by animateFloatAsState(targetValue = swapRotation, label = "swap")
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = "Swap Languages",
                            tint = BabyCyan,
                            modifier = Modifier.rotate(animatedRotation)
                        )
                    }

                    // To Language Dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .clickable { showToMenu = true }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = toLanguage,
                                color = BabyText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Icon(Icons.Filled.ArrowDropDown, null, tint = BabyMuted)
                        }

                        DropdownMenu(
                            expanded = showToMenu,
                            onDismissRequest = { showToMenu = false },
                            modifier = Modifier.background(Color(0xFF0F172A))
                        ) {
                            SUPPORTED_LANGUAGES.filter { it != "Auto Detect" }.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang, color = BabyText, fontSize = 13.sp) },
                                    onClick = {
                                        toLanguage = lang
                                        showToMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Source Input Glass Card
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = fromLanguage.uppercase(),
                            color = BabyCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        if (sourceText.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    sourceText = ""
                                    viewModel.clearTranslation()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Filled.Clear, "Clear", tint = BabyMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    BasicTextField(
                        value = sourceText,
                        onValueChange = { sourceText = it },
                        textStyle = TextStyle(color = BabyText, fontSize = 15.sp, lineHeight = 22.sp),
                        cursorBrush = SolidColor(BabyCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        decorationBox = { innerTextField ->
                            if (sourceText.isEmpty()) {
                                Text(
                                    text = "Enter or dictate text to translate...",
                                    color = BabyMuted,
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${sourceText.length} chars",
                            color = BabyMuted.copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )

                        // Voice Dictation Button
                        IconButton(
                            onClick = {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasPermission) {
                                    if (isDictating) {
                                        viewModel.stopDictation()
                                    } else {
                                        viewModel.startDictation()
                                    }
                                } else {
                                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isDictating) Color(0xFFEF4444).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
                        ) {
                            Icon(
                                imageVector = if (isDictating) Icons.Filled.MicOff else Icons.Filled.Mic,
                                contentDescription = "Dictate",
                                tint = if (isDictating) Color(0xFFEF4444) else BabyCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Action Translate Trigger
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF2563EB),
                                Color(0xFF7C3AED),
                                Color(0xFF06B6D4)
                            )
                        )
                    )
                    .clickable(enabled = sourceText.isNotBlank() && !isTranslating) {
                        viewModel.translate(sourceText, fromLanguage, toLanguage)
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isTranslating) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "TRANSLATING...",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Translate,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "TRANSLATE WITH BABY",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Translated Output Glass Card
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(185.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = toLanguage.uppercase(),
                            color = BabyViolet,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        if (translationResult.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Speak pronunciation
                                IconButton(
                                    onClick = { viewModel.speak(translationResult) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Speak",
                                        tint = BabyCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                                // Copy
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(translationResult))
                                        Toast.makeText(context, "Translation copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = BabyText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (translationResult.isNotEmpty()) {
                            Text(
                                text = translationResult,
                                color = BabyText,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            )
                        } else {
                            Text(
                                text = if (isTranslating) "Synthesizing neural translation..." else "Translated text will appear here with pronunciation audio support.",
                                color = BabyMuted,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
