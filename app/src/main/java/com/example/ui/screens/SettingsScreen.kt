package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.theme.BabyBackground
import com.example.ui.theme.BabyBlue
import com.example.ui.theme.BabyCyan
import com.example.ui.theme.BabyGreen
import com.example.ui.theme.BabyMuted
import com.example.ui.theme.BabyPink
import com.example.ui.theme.BabyText
import com.example.ui.theme.BabyViolet
import com.example.ui.viewmodel.BabyViewModel

@Composable
fun SettingsScreen(
    viewModel: BabyViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val apiKey by viewModel.apiKey.collectAsState()
    val model by viewModel.geminiModel.collectAsState()
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

    var keyDraft by remember(apiKey) { mutableStateOf(apiKey) }
    var customDraft by remember(custom) { mutableStateOf(custom) }

    Column(
        modifier = modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF0B1E3A), Color(0xFF050A15), BabyBackground), radius = 900f)
        ).padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = BabyText) }
            Column(Modifier.weight(1f)) {
                Text("System Settings", color = BabyText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Configure Baby's cognitive core", color = BabyMuted, fontSize = 11.sp)
            }
            Icon(Icons.Filled.Tune, null, tint = BabyCyan)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
            item {
                SettingsCard("AI CORE", Icons.Filled.Speed) {
                    SettingLine("Gemini model", "Fast responses are preferred")
                    GlassInput(keyDraft, { keyDraft = it }, "API key")
                    SavePill("Save API key") { viewModel.saveSetting("api_key", keyDraft.trim()) }
                    Spacer(Modifier.height(8.dp))
                    GlassInput(model, { viewModel.saveSetting("gemini_model", it) }, "Model")
                }
            }
            item {
                SettingsCard("VOICE ENGINE", Icons.Filled.RecordVoiceOver) {
                    SettingSwitch("Continuous conversation", "Listen again after Baby speaks", continuous) { viewModel.saveSetting("is_continuous_mode", it.toString()) }
                    Slider(value = rate, onValueChange = { viewModel.saveSetting("voice_rate", it.toString()) }, valueRange = .65f..1.35f)
                    Text("Speech rate ${"%.2f".format(rate)}x", color = BabyMuted, fontSize = 11.sp)
                    Slider(value = pitch, onValueChange = { viewModel.saveSetting("voice_pitch", it.toString()) }, valueRange = .75f..1.25f)
                    Text("Pitch ${"%.2f".format(pitch)}", color = BabyMuted, fontSize = 11.sp)
                }
            }
            item {
                SettingsCard("BACKGROUND LISTENER", Icons.Filled.Mic) {
                    SettingSwitch("Persistent Voice Service", "Keep the wake-word listener alive", background) { viewModel.updateBackgroundServiceState(it) }
                    SettingSwitch("Offline Wake-Word Detection", "Hey Baby / Hi Baby / Hello Baby", wake) { viewModel.saveDeviceControlSetting("wake_word_enabled", it) }
                    SettingSwitch("Auto-Restart on Boot", "Resume after the phone restarts", boot) { viewModel.saveDeviceControlSetting("boot_on_startup", it) }
                    SettingSwitch("Microphone Access", "Required for voice input", mic) { viewModel.saveDeviceControlSetting("microphone_access_enabled", it) }
                }
            }
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
            item {
                SettingsCard("PRIVACY", Icons.Filled.Security) {
                    Text("The green Android microphone indicator means Baby currently has microphone access. Manual voice input pauses the background listener so only one recognizer owns the microphone at a time.", color = BabyMuted, fontSize = 12.sp, lineHeight = 18.sp)
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
