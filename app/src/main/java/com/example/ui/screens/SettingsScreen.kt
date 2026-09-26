package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.*
import com.example.ui.components.GlassCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.BabyViewModel

@Composable
fun SettingsScreen(
    viewModel: BabyViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val modelStatus by viewModel.modelStatus.collectAsState()
    val rate by viewModel.voiceRate.collectAsState()
    val pitch by viewModel.voicePitch.collectAsState()
    val continuous by viewModel.isContinuousMode.collectAsState()
    val background by viewModel.backgroundServiceEnabled.collectAsState()
    val wake by viewModel.wakeWordEnabled.collectAsState()
    val mic by viewModel.microphoneAccessEnabled.collectAsState()
    val boot by viewModel.bootOnStartupEnabled.collectAsState()
    val hey by viewModel.wakePhraseHeyBaby.collectAsState()
    val hi by viewModel.wakePhraseHiBaby.collectAsState()
    val hello by viewModel.wakePhraseHelloBaby.collectAsState()
    val baby by viewModel.wakePhraseBaby.collectAsState()
    val custom by viewModel.customWakePhrase.collectAsState()

    var customDraft by remember(custom) { mutableStateOf(custom) }

    val hardware = viewModel.hardwareProfile

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(listOf(Color(0xFF0B1E3A), Color(0xFF050A15), BabyBackground), radius = 900f)
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = BabyText) }
            Column(Modifier.weight(1f)) {
                Text("System Settings", color = BabyText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Configure Baby's on-device cognitive core", color = BabyMuted, fontSize = 11.sp)
            }
            Icon(Icons.Filled.Tune, null, tint = BabyCyan)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
            // 1. Central On-Device AI Engine & Model Management
            item {
                SettingsCard("LOCAL ON-DEVICE AI ENGINE", Icons.Filled.Memory) {
                    Text(
                        "Baby runs her own autonomous AI engine directly on your phone hardware. Zero remote API keys, cloud subscriptions, or tracking.",
                        color = BabyMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    // Hardware profile readout
                    GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Device RAM: ${hardware.formattedRam}", color = BabyCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text("CPU: ${hardware.cpuCores} Cores", color = BabyCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Profile: ${hardware.recommendedTier.displayName}", color = BabyText, fontSize = 11.sp)
                                Text("Storage Free: ${hardware.formattedStorage}", color = BabyText, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Hardware compatibility advisory
                    val currentCompatibility = ModelCatalog.getCompatibility(modelStatus.currentModel, hardware)
                    if (currentCompatibility == DeviceCompatibility.COMPATIBLE_HIGH_MEMORY) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x22F59E0B), RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0x66F59E0B), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Warning, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Hardware Advisory: High Memory Load", color = Color(0xFFF59E0B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${modelStatus.currentModel.name} requires ~${modelStatus.currentModel.minRamMb} MB RAM. Your device has ${hardware.formattedRam}. Running this model may cause memory pressure. We recommend Baby Compact for smooth performance.",
                                color = BabyText,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Button(
                                onClick = { viewModel.selectModel(ModelCatalog.QWEN_0_5B) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                modifier = Modifier.fillMaxWidth().height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Switch to Baby Compact (Recommended)", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    } else if (currentCompatibility == DeviceCompatibility.INSUFFICIENT_RAM) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x22EF4444), RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0x66EF4444), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Error, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Exceeds Device Hardware Capacity", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${modelStatus.currentModel.name} requires a minimum of ${modelStatus.currentModel.minRamMb} MB RAM, which exceeds this phone's total RAM (${hardware.totalRamMb} MB).",
                                color = BabyText,
                                fontSize = 10.sp
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    // Current Model Status Header
                    SettingLine(
                        if (modelStatus.state == ModelInstallationState.READY) "Active Model" else "Selected Model",
                        modelStatus.currentModel.name
                    )
                    Text(
                        "Parameters: ${modelStatus.currentModel.parameterCount} • Size: ${modelStatus.currentModel.sizeFormatted} • Quantization: ${modelStatus.currentModel.quantization}",
                        color = BabyMuted,
                        fontSize = 10.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    when (modelStatus.state) {
                        ModelInstallationState.READY -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BabyGreen.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                    .border(1.dp, BabyGreen.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.CheckCircle, null, tint = BabyGreen, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Model Installed, Loaded & Verified", color = BabyGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(modelStatus.verificationDetails ?: "Active 100% on-device neural inference", color = BabyText, fontSize = 10.sp)
                                    }
                                }
                            }

                            val testOutput = viewModel.selfTestResult.value
                            if (testOutput != null) {
                                Spacer(Modifier.height(8.dp))
                                GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                                    Column(Modifier.padding(8.dp)) {
                                        Text("Self-Test Output:", color = BabyCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(4.dp))
                                        Text(testOutput, color = BabyText, fontSize = 10.sp, lineHeight = 14.sp)
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.runModelSelfTest() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BabyCyan)
                                ) {
                                    Text("Test Token Gen", fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.reloadLocalModel() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BabyCyan)
                                ) {
                                    Text("Re-verify", fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.deleteModel() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                                ) {
                                    Text("Free Storage", fontSize = 11.sp)
                                }
                            }
                        }
                        ModelInstallationState.DOWNLOADING -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BabyBlue.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Downloading Neural Model...", color = BabyCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("${(modelStatus.downloadProgress * 100).toInt()}% (${modelStatus.downloadSpeed})", color = BabyText, fontSize = 11.sp)
                                }
                                Spacer(Modifier.height(4.dp))
                                val dlMb = modelStatus.downloadedBytes / (1024 * 1024)
                                val totMb = modelStatus.totalBytes / (1024 * 1024)
                                Text("$dlMb MB / $totMb MB downloaded", color = BabyMuted, fontSize = 10.sp)
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { modelStatus.downloadProgress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = BabyCyan,
                                    trackColor = Color(0xFF1E293B)
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.cancelModelDownload() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                                ) {
                                    Text("Pause / Cancel Download", color = BabyText, fontSize = 11.sp)
                                }
                            }
                        }
                        ModelInstallationState.VERIFYING -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BabyCyan.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = BabyCyan, strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("Verifying GGUF Header & Executing Self-Test...", color = BabyCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("Inspecting binary structure and validating tokens", color = BabyMuted, fontSize = 10.sp)
                                }
                            }
                        }
                        ModelInstallationState.ERROR -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0x22EF4444), RoundedCornerShape(10.dp))
                                    .border(1.dp, Color(0x66EF4444), RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.ErrorOutline, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Download / Installation Failure", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    modelStatus.errorMessage ?: "Unknown error occurred during download.",
                                    color = BabyText,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { viewModel.downloadModel() },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = BabyBlue)
                                    ) {
                                        Text("Retry Download", color = Color.White, fontSize = 11.sp)
                                    }
                                    if (modelStatus.currentModel.id != ModelCatalog.QWEN_0_5B.id) {
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.selectModel(ModelCatalog.QWEN_0_5B)
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BabyCyan)
                                        ) {
                                            Text("Try Baby Compact", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                        ModelInstallationState.NOT_INSTALLED -> {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.WarningAmber, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text("No Local Model Installed", color = BabyText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Install model weights below to activate on-device reasoning.", color = BabyMuted, fontSize = 10.sp)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.downloadModel() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = BabyBlue)
                                ) {
                                    Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Download & Install ${modelStatus.currentModel.name} (${modelStatus.currentModel.sizeFormatted})", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Model Selection Catalog
                    Text("Select Local Model", color = BabyText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    ModelCatalog.allModels.forEach { itemModel ->
                        val isSelected = (itemModel.id == modelStatus.currentModel.id)
                        val compatibility = ModelCatalog.getCompatibility(itemModel, hardware)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .background(
                                    if (isSelected) BabyBlue.copy(alpha = 0.2f) else Color(0x10FFFFFF),
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) BabyCyan else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.selectModel(itemModel) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.selectModel(itemModel) },
                                colors = RadioButtonDefaults.colors(selectedColor = BabyCyan)
                            )
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(itemModel.name, color = BabyText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    when (compatibility) {
                                        DeviceCompatibility.RECOMMENDED -> {
                                            Text(
                                                "⭐ RECOMMENDED",
                                                color = BabyGreen,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .background(BabyGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                        DeviceCompatibility.COMPATIBLE_HIGH_MEMORY -> {
                                            Text(
                                                "⚠️ HIGH RAM LOAD",
                                                color = Color(0xFFF59E0B),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .background(Color(0x22F59E0B), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                        DeviceCompatibility.INSUFFICIENT_RAM -> {
                                            Text(
                                                "⛔ 5GB+ RAM REQ",
                                                color = Color(0xFFEF4444),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .background(Color(0x22EF4444), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text("${itemModel.sizeFormatted} • Min RAM: ${itemModel.minRamMb} MB • ${itemModel.parameterCount}", color = BabyMuted, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            // 2. Voice Engine Settings
            item {
                SettingsCard("VOICE ENGINE", Icons.Filled.RecordVoiceOver) {
                    SettingSwitch("Continuous conversation", "Listen again after Baby speaks", continuous) { viewModel.saveSetting("is_continuous_mode", it.toString()) }
                    Slider(value = rate, onValueChange = { viewModel.saveSetting("voice_rate", it.toString()) }, valueRange = .65f..1.35f)
                    Text("Speech rate ${"%.2f".format(rate)}x", color = BabyMuted, fontSize = 11.sp)
                    Slider(value = pitch, onValueChange = { viewModel.saveSetting("voice_pitch", it.toString()) }, valueRange = .75f..1.25f)
                    Text("Pitch ${"%.2f".format(pitch)}", color = BabyMuted, fontSize = 11.sp)
                }
            }

            // 3. Background Listener Settings
            item {
                SettingsCard("BACKGROUND LISTENER", Icons.Filled.Mic) {
                    SettingSwitch("Persistent Voice Service", "Keep the wake-word listener alive", background) { viewModel.updateBackgroundServiceState(it) }
                    SettingSwitch("Offline Wake-Word Detection", "Hey Baby / Hi Baby / Hello Baby", wake) { viewModel.saveDeviceControlSetting("wake_word_enabled", it) }
                    SettingSwitch("Auto-Restart on Boot", "Resume after the phone restarts", boot) { viewModel.saveDeviceControlSetting("boot_on_startup", it) }
                    SettingSwitch("Microphone Access", "Required for voice input", mic) { viewModel.saveDeviceControlSetting("microphone_access_enabled", it) }
                }
            }

            // 4. Wake Phrases
            item {
                SettingsCard("WAKE PHRASES", Icons.Filled.CheckCircle) {
                    SettingSwitch("Hey Baby", "Recommended primary phrase", hey) { viewModel.saveDeviceControlSetting("wake_phrase_hey_baby", it) }
                    SettingSwitch("Hi Baby", "Alternative phrase", hi) { viewModel.saveDeviceControlSetting("wake_phrase_hi_baby", it) }
                    SettingSwitch("Hello Baby", "Alternative phrase", hello) { viewModel.saveDeviceControlSetting("wake_phrase_hello_baby", it) }
                    SettingSwitch("Baby", "Short wake phrase", baby) { viewModel.saveDeviceControlSetting("wake_phrase_baby", it) }
                    Spacer(Modifier.height(8.dp))
                    GlassInput(customDraft, { customDraft = it }, "Optional custom phrase")
                    SavePill("Save custom phrase") { viewModel.saveCustomWakePhrase(customDraft.trim()) }
                    Text("Custom phrase is optional. You do NOT need it for Hey Baby.", color = BabyMuted, fontSize = 10.sp)
                }
            }

            // 5. Centralized Safety & Privacy Policies (JB Restrictions)
            item {
                SettingsCard("SAFETY & PRIVACY (JB RESTRICTIONS)", Icons.Filled.Security) {
                    Text(
                        "Baby enforces JB Restrictions: a centralized on-device safety and privacy policy. All moderation, device controls, and inference run locally on this phone without telemetry.",
                        color = BabyMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = BabyCyan, modifier = Modifier.padding(end = 9.dp))
                Text(title, color = BabyCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingLine(title: String, subtitle: String) {
    Text(title, color = BabyText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    Text(subtitle, color = BabyMuted, fontSize = 10.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = BabyText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = BabyMuted, fontSize = 10.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BabyBlue))
    }
}

@Composable
private fun GlassInput(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = BabyText, fontSize = 13.sp),
            decorationBox = { inner -> if (value.isBlank()) Text(placeholder, color = BabyMuted, fontSize = 13.sp); inner() }
        )
    }
}

@Composable
private fun SavePill(label: String, onClick: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth().padding(top = 7.dp), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(11.dp), horizontalArrangement = Arrangement.Center) {
            Text(label, color = BabyCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
