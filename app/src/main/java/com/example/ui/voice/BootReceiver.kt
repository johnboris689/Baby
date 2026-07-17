package com.example.ui.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.local.db.AppDatabase
import com.example.data.repository.BabyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    private val tag = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(tag, "Boot completed received!")
            
            val db = AppDatabase.getDatabase(context.applicationContext)
            val repository = BabyRepository(
                db.conversationDao(),
                db.messageDao(),
                db.memoryDao(),
                db.settingDao(),
                db.logDao(),
                db.noteDao(),
                db.taskDao(),
                db.automationRuleDao()
            )

            CoroutineScope(Dispatchers.IO).launch {
                val bootEnabled = repository.getSetting("boot_on_startup", "false").toBoolean()
                val serviceEnabled = repository.getSetting("background_service", "false").toBoolean()
                
                if (bootEnabled && serviceEnabled) {
                    Log.d(tag, "Boot and service enabled! Starting BabyAssistantService...")
                    repository.addLog("System", "Automatically starting background assistant service on device reboot.")
                    
                    val serviceIntent = Intent(context, BabyAssistantService::class.java)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            }
        }
    }
}
