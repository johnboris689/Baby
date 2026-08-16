package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.local.db.AppDatabase
import com.example.data.repository.BabyRepository
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MemoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.BabyViewModel

enum class Screen {
    Home,
    Chat,
    Memory,
    Settings
}

class MainActivity : ComponentActivity() {

    private val viewModel: BabyViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = AppDatabase.getDatabase(applicationContext)
                val repo = BabyRepository(
                    db.conversationDao(),
                    db.messageDao(),
                    db.memoryDao(),
                    db.settingDao(),
                    db.logDao(),
                    db.noteDao(),
                    db.taskDao(),
                    db.automationRuleDao()
                )
                @Suppress("UNCHECKED_CAST")
                return BabyViewModel(application, repo) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                var hasAudioPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    hasAudioPermission = isGranted
                }

                LaunchedEffect(Unit) {
                    if (!hasAudioPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                var currentScreen by remember { mutableStateOf(Screen.Home) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (currentScreen) {
                        Screen.Home -> HomeScreen(
                            viewModel = viewModel,
                            onNavigateToChat = { currentScreen = Screen.Chat },
                            onNavigateToMemory = { currentScreen = Screen.Memory },
                            onNavigateToSettings = { currentScreen = Screen.Settings },
                            modifier = Modifier.padding(innerPadding)
                        )
                        Screen.Chat -> ChatScreen(
                            viewModel = viewModel,
                            onNavigateBack = { currentScreen = Screen.Home },
                            modifier = Modifier.padding(innerPadding)
                        )
                        Screen.Memory -> MemoryScreen(
                            viewModel = viewModel,
                            onNavigateBack = { currentScreen = Screen.Home },
                            modifier = Modifier.padding(innerPadding)
                        )
                        Screen.Settings -> SettingsScreen(
                            viewModel = viewModel,
                            onNavigateBack = { currentScreen = Screen.Home },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
