package com.example.ui.voice

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.local.db.AppDatabase
import com.example.data.repository.BabyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationListener : NotificationListenerService() {

    private val tag = "NotificationListener"
    private val scope = CoroutineScope(Dispatchers.IO)
    private var repository: BabyRepository? = null

    companion object {
        private var instance: NotificationListener? = null
        private val recentNotifications = mutableMapOf<String, StatusBarNotification>()

        fun isConnected(): Boolean {
            return instance != null
        }

        fun replyToNotification(packageName: String, replyText: String): Boolean {
            val sbn = recentNotifications[packageName] ?: return false
            val wNotification = sbn.notification
            val actions = wNotification.actions ?: return false
            
            for (action in actions) {
                val remoteInputs = action.remoteInputs ?: continue
                for (remoteInput in remoteInputs) {
                    if (remoteInput.resultKey != null) {
                        // We found a direct reply action!
                        try {
                            val intent = Intent()
                            val bundle = Bundle()
                            bundle.putCharSequence(remoteInput.resultKey, replyText)
                            RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
                            
                            action.actionIntent.send(instance, 0, intent)
                            return true
                        } catch (e: Exception) {
                            Log.e("NotificationListener", "Failed to reply via notification action", e)
                        }
                    }
                }
            }
            return false
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val db = AppDatabase.getDatabase(applicationContext)
        repository = BabyRepository(
            db.conversationDao(),
            db.messageDao(),
            db.memoryDao(),
            db.settingDao(),
            db.logDao(),
            db.noteDao(),
            db.taskDao(),
            db.automationRuleDao()
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        
        val packageName = sbn.packageName
        if (packageName == this.packageName) return // Ignore own notifications

        val extras = sbn.notification.extras
        val title = extras?.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (title.isEmpty() && text.isEmpty()) return

        // Save reply capability if it exists
        val actions = sbn.notification.actions
        var canReply = false
        if (actions != null) {
            for (action in actions) {
                if (action.remoteInputs != null) {
                    canReply = true
                    break
                }
            }
        }

        if (canReply) {
            recentNotifications[packageName] = sbn
        }

        // Check if user enabled Notification Announcement
        scope.launch {
            val repo = repository ?: return@launch
            val announcementEnabled = repo.getSetting("notification_access_enabled", "false").toBoolean()
            if (announcementEnabled) {
                val announceText = "New notification from $packageName. $title says: $text"
                repo.addLog("Notification", "Announced: $title - $text")
                
                // Let the service / assistant voice know to speak this
                val speakIntent = Intent("com.example.ACTION_SPEAK_NOTIFICATION").apply {
                    putExtra("text", announceText)
                    setPackage(this@NotificationListener.packageName)
                }
                sendBroadcast(speakIntent)
            }
        }
    }
}
