package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.BabyViewModel

@Composable
fun SettingsScreen(
    viewModel: BabyViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOfflineMode by viewModel.isOfflineMode.collectAsState()
    val isAutoSwitchEnabled by viewModel.isAutoSwitchEnabled.collectAsState()
    val provider by viewModel.selectedProvider.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val backendUrl by viewModel.backendUrl.collectAsState()
    val llamaUrl by viewModel.llamaUrl.collectAsState()
    val voicePitch by viewModel.voicePitch.collectAsState()
    val voiceRate by viewModel.voiceRate.collectAsState()
    val voiceStyle by viewModel.voiceStyle.collectAsState()
    val selectedVoiceName by viewModel.selectedVoiceName.collectAsState()
    val availableVoices by viewModel.availableVoices.collectAsState()
    val isContinuousMode by viewModel.isContinuousMode.collectAsState()
    val silenceThreshold by viewModel.silenceThreshold.collectAsState()
    val backendHealth by viewModel.backendHealth.collectAsState()
    val logs by viewModel.logs.collectAsState()

    val batteryLevel by viewModel.batteryLevel.collectAsState()
    val isCharging by viewModel.isCharging.collectAsState()
    val isPowerSaveActive by viewModel.isPowerSaveActive.collectAsState()
    val forcePowerSave by viewModel.forcePowerSave.collectAsState()
    val autoPowerSave by viewModel.autoPowerSave.collectAsState()

    val context = LocalContext.current
    var showApiKey by remember { mutableStateOf(false) }

    val primaryBg = Brush.verticalGradient(
        colors = listOf(Color(0xFF0F172A), Color(0xFF020617))
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(primaryBg)
            .padding(16.dp)
    ) {
        // --- Header ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Column {
                Text(
                    text = "System Settings",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Configure Baby's cognitive core",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- AI Core Mode Section ---
            item {
                SettingsSectionCard(title = "AI Engine Core") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-Switch AI Engine",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Automatically switches to local Llama.cpp when offline, and Gemini Cloud API when online",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = isAutoSwitchEnabled,
                                onCheckedChange = { viewModel.saveSetting("is_auto_switch_enabled", it.toString()) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF3B82F6),
                                    checkedTrackColor = Color(0xFF3B82F6).copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.testTag("auto_switch_switch")
                            )
                        }

                        Divider(color = Color.White.copy(alpha = 0.05f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Manual Offline-First Mode",
                                    color = if (isAutoSwitchEnabled) Color.White.copy(alpha = 0.4f) else Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isOfflineMode) "Uses local llama.cpp server" else "Uses cloud-based Gemini Core API",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = isOfflineMode,
                                enabled = !isAutoSwitchEnabled,
                                onCheckedChange = { viewModel.saveSetting("is_offline_mode", it.toString()) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF10B981),
                                    checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.testTag("offline_mode_switch")
                            )
                        }
                    }
                }
            }

            // --- Online API Provider Settings ---
            if (!isOfflineMode) {
                item {
                    SettingsSectionCard(title = "Online Provider Config") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("API Provider", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("gemini", "openai", "openrouter").forEach { prov ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (provider == prov) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.05f))
                                            .clickable { viewModel.saveSetting("selected_provider", prov) }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = prov.uppercase(),
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text("API Key", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)

                            TextField(
                                value = apiKey,
                                onValueChange = { viewModel.saveSetting("api_key", it) },
                                placeholder = { Text("Enter private key...") },
                                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showApiKey = !showApiKey }) {
                                        Icon(
                                            imageVector = if (showApiKey) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.4f)
                                        )
                                    }
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("api_key_field")
                            )
                        }
                    }
                }
            } else {
                // --- Offline llama.cpp Server Settings ---
                item {
                    SettingsSectionCard(title = "Local Server Configuration") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Connection test
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (backendHealth) Color(0xFF10B981).copy(alpha = 0.08f) else Color(0xFFEF4444).copy(alpha = 0.08f))
                                    .border(1.dp, if (backendHealth) Color(0xFF10B981) else Color(0xFFEF4444), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (backendHealth) "Local server connected successfully" else "Could not connect to local server",
                                    color = if (backendHealth) Color(0xFF34D399) else Color(0xFFF87171),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Button(
                                    onClick = { viewModel.checkBackendHealth() },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (backendHealth) Color(0xFF10B981) else Color(0xFFEF4444)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Test", fontSize = 11.sp, color = Color.White)
                                }
                            }

                            Text("Flask API URL", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                            TextField(
                                value = backendUrl,
                                onValueChange = { viewModel.saveSetting("backend_url", it) },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text("Direct llama.cpp Port URL", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                            TextField(
                                value = llamaUrl,
                                onValueChange = { viewModel.saveSetting("llama_url", it) },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // --- Voice Customization Section ---
            item {
                SettingsSectionCard(title = "Voice Speech Synthesis") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Style Preset Selector
                        val styleOptions = listOf(
                            "Default (Balanced)" to "default",
                            "Cute Baby (High-pitched)" to "baby",
                            "Deep & Wise" to "deep",
                            "Playful & Lively" to "playful",
                            "Calm & Meditative" to "calm",
                            "Custom Pitch/Speed" to "custom"
                        )
                        val currentStyleName = styleOptions.firstOrNull { it.second == voiceStyle }?.first ?: "Custom"
                        
                        SettingsDropdown(
                            label = "Speech Style Preset",
                            selectedValue = currentStyleName,
                            options = styleOptions,
                            onSelected = { viewModel.saveSetting("voice_style", it) }
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // System TTS Voice Selector
                        val voiceOptions = remember(availableVoices) {
                            val list = mutableListOf("Device Default Voice" to "")
                            availableVoices.forEach { name ->
                                val cleanName = name
                                    .replace("en-", "")
                                    .replace("us-", "US ")
                                    .replace("gb-", "UK ")
                                    .replace("-local", "")
                                    .uppercase()
                                list.add("$cleanName ($name)" to name)
                            }
                            list
                        }
                        val currentVoiceDisplayName = voiceOptions.firstOrNull { it.second == selectedVoiceName }?.first ?: "Device Default Voice"

                        SettingsDropdown(
                            label = "System TTS Voice",
                            selectedValue = currentVoiceDisplayName,
                            options = voiceOptions,
                            onSelected = { viewModel.saveSetting("selected_voice_name", it) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Speech Rate (Speed)", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                                Text(text = String.format("%.2fx", voiceRate), color = Color(0xFF3B82F6), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = voiceRate,
                                onValueChange = { viewModel.saveSetting("voice_rate", it.toString()) },
                                valueRange = 0.5f..2.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF3B82F6),
                                    activeTrackColor = Color(0xFF3B82F6)
                                )
                            )
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Speech Pitch", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                                Text(text = String.format("%.2f", voicePitch), color = Color(0xFF3B82F6), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = voicePitch,
                                onValueChange = { viewModel.saveSetting("voice_pitch", it.toString()) },
                                valueRange = 0.5f..2.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF3B82F6),
                                    activeTrackColor = Color(0xFF3B82F6)
                                )
                            )
                        }

                        // Test speech button
                        Button(
                            onClick = { viewModel.speak("Hello! I am Baby. My voice parameters are set correctly.") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Audio Synthesis", color = Color.White)
                        }
                    }
                }
            }

            // --- Voice Input & Silence Detection Section ---
            item {
                SettingsSectionCard(title = "Voice Input & Silence Detection") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Silence Amplitude Threshold", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = "Minimum audio energy level to keep the microphone active",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    text = String.format("%.1f dB", silenceThreshold),
                                    color = Color(0xFF10B981),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = silenceThreshold,
                                onValueChange = { viewModel.saveSetting("silence_amplitude_threshold", it.toString()) },
                                valueRange = 0.0f..10.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF10B981),
                                    activeTrackColor = Color(0xFF10B981),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                                ),
                                modifier = Modifier.testTag("silence_threshold_slider")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("0.0 dB (Ultra-Sensitive)", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                                Text("10.0 dB (Strict/Quick Auto-Stop)", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                            }
                        }

                        Divider(color = Color.White.copy(alpha = 0.05f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Continuous Mode", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Automatically reopen microphone after speaking response",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = isContinuousMode,
                                onCheckedChange = { viewModel.saveSetting("is_continuous_mode", it.toString()) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF10B981),
                                    checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.testTag("continuous_mode_switch")
                            )
                        }
                    }
                }
            }

            // --- Battery & Power-Save Mode Section ---
            item {
                SettingsSectionCard(title = "Battery & Power-Save Mode") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Battery level and status visual
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Battery Status", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (isPowerSaveActive) "Power-Save active: Optimizations enabled" else "Normal operational mode",
                                    color = if (isPowerSaveActive) Color(0xFFF59E0B) else Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(36.dp)
                                        .height(20.dp)
                                        .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                        .padding(2.dp)
                                ) {
                                    val progress = (batteryLevel / 100f).coerceIn(0f, 1f)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(progress)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(
                                                if (isPowerSaveActive) Color(0xFFF59E0B)
                                                else if (batteryLevel < 20) Color(0xFFEF4444)
                                                else Color(0xFF10B981)
                                            )
                                    )
                                }
                                Text(
                                    text = "$batteryLevel%${if (isCharging) " ⚡" else ""}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Divider(color = Color.White.copy(alpha = 0.05f))

                        // Force Power-Save Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Force Power-Save Mode", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Force offline optimizations and low complexity requests immediately",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = forcePowerSave,
                                onCheckedChange = { viewModel.saveSetting("force_power_save", it.toString()) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFF59E0B),
                                    checkedTrackColor = Color(0xFFF59E0B).copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.testTag("force_power_save_switch")
                            )
                        }

                        Divider(color = Color.White.copy(alpha = 0.05f))

                        // Auto Power-Save Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto Power-Save (Low Battery)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Enable automatically when battery is low (< 20%) and discharging",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = autoPowerSave,
                                onCheckedChange = { viewModel.saveSetting("auto_power_save", it.toString()) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF10B981),
                                    checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.testTag("auto_power_save_switch")
                            )
                        }

                        if (isPowerSaveActive) {
                            Divider(color = Color.White.copy(alpha = 0.05f))
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF59E0B).copy(alpha = 0.08f))
                                    .padding(10.dp)
                            ) {
                                Text("Active Optimizations:", color = Color(0xFFF59E0B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("• Health check monitoring interval increased to 120s (saves standby wakeups)", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                                Text("• AI context simplified to 3 turns and max 1 local memory (reduces request size)", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                                Text("• Restricting Gemini outputs to 150 max tokens (reduces data and processing time)", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                                Text("• Voice audio cache clearing interval extended to 15 mins", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // --- Voice Assistant & Device Controls Section ---
            item {
                val backgroundService by viewModel.backgroundServiceEnabled.collectAsState()
                val bootOnStartup by viewModel.bootOnStartupEnabled.collectAsState()
                val wakeWordEnabled by viewModel.wakeWordEnabled.collectAsState()
                val customWakePhrase by viewModel.customWakePhrase.collectAsState()

                var tempCustomWake by remember(customWakePhrase) { mutableStateOf(customWakePhrase) }

                SettingsSectionCard(title = "Assistant Background Core") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Persistent Voice Service", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Keep Baby active in background with a persistent notification",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = backgroundService,
                                onCheckedChange = { viewModel.updateBackgroundServiceState(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF3B82F6),
                                    checkedTrackColor = Color(0xFF3B82F6).copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.testTag("bg_service_switch")
                            )
                        }

                        Divider(color = Color.White.copy(alpha = 0.05f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Restart on Boot", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Automatically launch voice service when device reboots",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = bootOnStartup,
                                onCheckedChange = { viewModel.saveDeviceControlSetting("boot_on_startup", it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF3B82F6),
                                    checkedTrackColor = Color(0xFF3B82F6).copy(alpha = 0.3f)
                                )
                            )
                        }

                        Divider(color = Color.White.copy(alpha = 0.05f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Offline Wake-Word Detection", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Continuous listening for 'Hey Baby', 'Hi Baby' to activate",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = wakeWordEnabled,
                                onCheckedChange = { viewModel.saveDeviceControlSetting("wake_word_enabled", it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF3B82F6),
                                    checkedTrackColor = Color(0xFF3B82F6).copy(alpha = 0.3f)
                                )
                            )
                        }

                        if (wakeWordEnabled) {
                            Divider(color = Color.White.copy(alpha = 0.05f))
                            
                            // Custom Wake Word Input
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Custom Activation Phrase", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextField(
                                        value = tempCustomWake,
                                        onValueChange = { tempCustomWake = it },
                                        placeholder = { Text("e.g. computer, assistant", fontSize = 13.sp) },
                                        colors = TextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                            unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = {
                                            viewModel.saveCustomWakePhrase(tempCustomWake)
                                            Toast.makeText(context, "Saved wake phrase!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                                    ) {
                                        Text("Save", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- Device Controls Permissions & Safety Section ---
            item {
                val flashlightEnabled by viewModel.flashlightControlEnabled.collectAsState()
                val appLaunchingEnabled by viewModel.appLaunchingEnabled.collectAsState()
                val notificationAccessEnabled by viewModel.notificationAccessEnabled.collectAsState()
                val storageAccessEnabled by viewModel.storageAccessEnabled.collectAsState()
                val microphoneAccessEnabled by viewModel.microphoneAccessEnabled.collectAsState()
                val cameraAccessEnabled by viewModel.cameraAccessEnabled.collectAsState()
                val contactAccessEnabled by viewModel.contactAccessEnabled.collectAsState()
                val smsAccessEnabled by viewModel.smsAccessEnabled.collectAsState()

                SettingsSectionCard(title = "Device Controls & Permissions") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "Toggle permissions and control features which Baby has access to:",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        // Microphone Access Switch
                        PermissionToggleRow(
                            title = "Microphone Access",
                            description = "Required for always-listening and voice input features",
                            checked = microphoneAccessEnabled,
                            onCheckedChange = { viewModel.saveDeviceControlSetting("microphone_access_enabled", it) }
                        )

                        Divider(color = Color.White.copy(alpha = 0.05f))

                        // Flashlight Control Switch
                        PermissionToggleRow(
                            title = "Flashlight & Torch Control",
                            description = "Allows Baby to toggle system flashlight by voice command",
                            checked = flashlightEnabled,
                            onCheckedChange = { viewModel.saveDeviceControlSetting("flashlight_control_enabled", it) }
                        )

                        Divider(color = Color.White.copy(alpha = 0.05f))

                        // App Launching Switch
                        PermissionToggleRow(
                            title = "App Launching",
                            description = "Allows opening other installed apps on command",
                            checked = appLaunchingEnabled,
                            onCheckedChange = { viewModel.saveDeviceControlSetting("app_launching_enabled", it) }
                        )

                        Divider(color = Color.White.copy(alpha = 0.05f))

                        // Notification Access Switch (Needs system intent)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Read & Announce Notifications", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Requires System Notification Listener Permission. Click 'Configure' to open settings.",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Switch(
                                    checked = notificationAccessEnabled,
                                    onCheckedChange = { viewModel.saveDeviceControlSetting("notification_access_enabled", it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF10B981),
                                        checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.3f)
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Configure",
                                    color = Color(0xFF3B82F6),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        try {
                                            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not open Notification settings", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }

                        Divider(color = Color.White.copy(alpha = 0.05f))

                        // Storage Access Switch
                        PermissionToggleRow(
                            title = "Storage & Local Media",
                            description = "Required for reading local notes and offline files",
                            checked = storageAccessEnabled,
                            onCheckedChange = { viewModel.saveDeviceControlSetting("storage_access_enabled", it) }
                        )

                        Divider(color = Color.White.copy(alpha = 0.05f))

                        // Camera Access Switch
                        PermissionToggleRow(
                            title = "Camera Actions",
                            description = "Allows taking photos and starting video recordings via voice",
                            checked = cameraAccessEnabled,
                            onCheckedChange = { viewModel.saveDeviceControlSetting("camera_access_enabled", it) }
                        )

                        Divider(color = Color.White.copy(alpha = 0.05f))

                        // Contact & Calling Switch
                        PermissionToggleRow(
                            title = "Contacts & Calling",
                            description = "Allows looking up contacts and placing calls by name",
                            checked = contactAccessEnabled,
                            onCheckedChange = { viewModel.saveDeviceControlSetting("contact_access_enabled", it) }
                        )

                        Divider(color = Color.White.copy(alpha = 0.05f))

                        // SMS sending switch
                        PermissionToggleRow(
                            title = "Send SMS Messages",
                            description = "Allows composing and sending text messages by voice",
                            checked = smsAccessEnabled,
                            onCheckedChange = { viewModel.saveDeviceControlSetting("sms_access_enabled", it) }
                        )
                    }
                }
            }

            // --- Developer logs list ---

            item {
                SettingsSectionCard(title = "System Developer Logs") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Cognitive Events", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)

                            IconButton(onClick = { viewModel.clearLogs() }) {
                                Icon(imageVector = Icons.Filled.Delete, contentDescription = "Clear logs", tint = Color.Red.copy(alpha = 0.8f))
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0B0F19))
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            if (logs.isEmpty()) {
                                Text(
                                    text = "No logs yet. Try interacting with Baby.",
                                    color = Color.White.copy(alpha = 0.3f),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(logs) { log ->
                                        Text(
                                            text = "[${log.tag}] ${log.message}",
                                            color = if (log.tag == "AI_Error") Color(0xFFF87171) else Color(0xFF34D399),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // About block
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Baby AI Assistant • v1.0.0", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                    Text("Pristine, private and modern.", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 4.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.03f))
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
fun <T> SettingsDropdown(
    label: String,
    selectedValue: String,
    options: List<Pair<String, T>>,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = selectedValue, color = Color.White, fontSize = 14.sp)
                Icon(
                    imageVector = if (expanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .background(Color(0xFF1E293B))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            ) {
                options.forEach { (displayName, value) ->
                    DropdownMenuItem(
                        text = { Text(displayName, color = Color.White, fontSize = 14.sp) },
                        onClick = {
                            onSelected(value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF10B981),
                checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.3f)
            )
        )
    }
}

