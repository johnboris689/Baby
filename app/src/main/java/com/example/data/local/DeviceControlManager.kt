package com.example.data.local

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale

class DeviceControlManager(private val context: Context) {

    private val tag = "DeviceControlManager"

    // --- FLASHLIGHT ---
    fun setFlashlight(enabled: Boolean): String {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        return if (cameraManager != null) {
            try {
                val cameraId = cameraManager.cameraIdList.firstOrNull()
                if (cameraId != null) {
                    cameraManager.setTorchMode(cameraId, enabled)
                    "Flashlight turned ${if (enabled) "on" else "off"} successfully."
                } else {
                    "No flashlight/camera found on this device."
                }
            } catch (e: Exception) {
                Log.e(tag, "Error setting flashlight", e)
                "Failed to control flashlight: ${e.localizedMessage}"
            }
        } else {
            "Flashlight is not supported on this device."
        }
    }

    // --- APP LAUNCHER ---
    fun getInstalledApps(): List<Pair<String, String>> {
        val apps = mutableListOf<Pair<String, String>>()
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        for (info in resolveInfos) {
            val label = info.loadLabel(pm).toString()
            val packageName = info.activityInfo.packageName
            apps.add(label to packageName)
        }
        return apps
    }

    fun launchApp(appName: String): String {
        val pm = context.packageManager
        val apps = getInstalledApps()
        
        // Try exact match first, then case-insensitive, then partial match
        val matchedApp = apps.find { it.first.equals(appName, ignoreCase = true) }
            ?: apps.find { it.first.contains(appName, ignoreCase = true) }
            
        if (matchedApp != null) {
            val intent = pm.getLaunchIntentForPackage(matchedApp.second)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return "Launching ${matchedApp.first}."
            }
        }
        
        // Standard Android apps fallback by name
        val pkgFallback = when (appName.lowercase(Locale.ROOT)) {
            "whatsapp" -> "com.whatsapp"
            "facebook" -> "com.facebook.katana"
            "instagram" -> "com.instagram.android"
            "telegram" -> "org.telegram.messenger"
            "chrome" -> "com.android.chrome"
            "settings" -> "com.android.settings"
            "calculator" -> "com.google.android.calculator"
            "camera" -> "com.android.camera"
            else -> null
        }
        
        if (pkgFallback != null) {
            val intent = pm.getLaunchIntentForPackage(pkgFallback)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return "Launching $appName."
            }
        }
        
        return "Application '$appName' is not installed or could not be opened."
    }

    // --- PHONE STORAGE ACCESS ---
    fun searchStorage(query: String): List<File> {
        val results = mutableListOf<File>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            return results
        }
        val rootDir = Environment.getExternalStorageDirectory()
        searchFolder(rootDir, query, results)
        return results
    }

    private fun searchFolder(dir: File, query: String, results: MutableList<File>) {
        if (!dir.exists() || !dir.isDirectory) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (results.size >= 15) return // Limit search size for responsiveness
            if (file.name.contains(query, ignoreCase = true)) {
                results.add(file)
            }
            if (file.isDirectory && !file.name.startsWith(".")) {
                searchFolder(file, query, results)
            }
        }
    }

    fun readTextFile(file: File): String {
        return try {
            if (file.length() > 50000) {
                file.bufferedReader().use { it.readText().take(2000) + "\n... [truncated]" }
            } else {
                file.readText()
            }
        } catch (e: Exception) {
            "Error reading file: ${e.localizedMessage}"
        }
    }

    // --- CAMERA ---
    fun openSystemCamera(video: Boolean = false): String {
        val intent = if (video) {
            Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE)
        } else {
            Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            "Opening camera."
        } else {
            // Fallback: search default camera application package
            val apps = getInstalledApps()
            val cameraApp = apps.find { it.first.lowercase(Locale.ROOT).contains("camera") }
            if (cameraApp != null) {
                launchApp(cameraApp.first)
            } else {
                "No camera application found to open."
            }
        }
    }

    // --- CONTACTS ---
    data class ContactInfo(val name: String, val phoneNumber: String)

    fun searchContacts(query: String): List<ContactInfo> {
        val list = mutableListOf<ContactInfo>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return list
        }
        val resolver = context.contentResolver
        val cursor: Cursor? = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$query%"),
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext() && list.size < 10) {
                if (nameIndex != -1 && numIndex != -1) {
                    val name = it.getString(nameIndex)
                    val phone = it.getString(numIndex)
                    list.add(ContactInfo(name, phone))
                }
            }
        }
        return list
    }

    fun makeCall(phoneNumber: String): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return "Calling requires Call Phone permission."
        }
        try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return "Calling $phoneNumber..."
        } catch (e: Exception) {
            return "Failed to place call: ${e.localizedMessage}"
        }
    }

    // --- SMS ---
    fun sendSMS(phoneNumber: String, message: String): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return "SMS sending requires SMS permissions."
        }
        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            "SMS sent successfully to $phoneNumber."
        } catch (e: Exception) {
            Log.e(tag, "SMS failed", e)
            "Failed to send SMS: ${e.localizedMessage}"
        }
    }

    // --- NAVIGATION ---
    fun openMaps(query: String? = null, lat: Double? = null, lng: Double? = null): String {
        val uri = if (lat != null && lng != null) {
            Uri.parse("geo:$lat,$lng")
        } else if (!query.isNullOrEmpty()) {
            Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        } else {
            Uri.parse("geo:0,0")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "Opening maps."
        } catch (e: Exception) {
            "Maps application not found."
        }
    }

    // --- MUSIC CONTROL ---
    fun controlMusic(action: String): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return "AudioManager not available."
        
        val eventCode = when (action.lowercase(Locale.ROOT)) {
            "play", "resume" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "volume_up" -> {
                audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                return "Volume increased."
            }
            "volume_down" -> {
                audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                return "Volume decreased."
            }
            else -> return "Unknown music command."
        }

        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, eventCode)
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, eventCode)
        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
        return "Sent music command: ${action.uppercase(Locale.ROOT)}."
    }

    // --- BLUETOOTH & WIFI CONTROLS ---
    fun setWifiState(enabled: Boolean): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return if (wifiManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Settings Panel Intent for modern Android
                val intent = Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opening Wi-Fi settings panel."
            } else {
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = enabled
                "Wi-Fi turned ${if (enabled) "on" else "off"}."
            }
        } else {
            "Wi-Fi is not supported."
        }
    }

    fun setBluetoothState(enabled: Boolean): String {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter
            ?: return "Bluetooth is not supported on this device."
            
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return "Bluetooth connection permission not granted."
        }

        return try {
            if (enabled) {
                @Suppress("DEPRECATION")
                bluetoothAdapter.enable()
                "Bluetooth turned on."
            } else {
                @Suppress("DEPRECATION")
                bluetoothAdapter.disable()
                "Bluetooth turned off."
            }
        } catch (e: SecurityException) {
            // Fallback: open Bluetooth Settings
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening Bluetooth settings."
        }
    }

    // --- DO NOT DISTURB (DND) ---
    fun setDNDMode(enabled: Boolean): String {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return "NotificationManager not available."
            
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return "Please grant Do Not Disturb access in the settings screen that opened."
            }
            
            val filter = if (enabled) {
                NotificationManager.INTERRUPTION_FILTER_NONE
            } else {
                NotificationManager.INTERRUPTION_FILTER_ALL
            }
            notificationManager.setInterruptionFilter(filter)
            return "Do Not Disturb turned ${if (enabled) "on" else "off"}."
        }
        return "DND control requires Android M or higher."
    }

    // --- SCREEN BRIGHTNESS ---
    fun setBrightness(brightnessValue: Float): String {
        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return "Please grant permission to modify system settings."
        }
        
        try {
            val systemBrightness = (brightnessValue * 255).toInt().coerceIn(0, 255)
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                systemBrightness
            )
            return "Screen brightness set to ${(brightnessValue * 100).toInt()}%."
        } catch (e: Exception) {
            return "Failed to set screen brightness: ${e.localizedMessage}"
        }
    }

    // --- CLIPBOARD CONTROL ---
    fun copyToClipboard(text: String): String {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return "Clipboard not available."
        val clip = ClipData.newPlainText("BabyAI Response", text)
        clipboard.setPrimaryClip(clip)
        return "Text copied to clipboard."
    }

    fun readClipboard(): String {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return "Clipboard not available."
        val primaryClip = clipboard.primaryClip
        if (primaryClip != null && primaryClip.itemCount > 0) {
            val text = primaryClip.getItemAt(0).text
            if (!text.isNullOrEmpty()) {
                return text.toString()
            }
        }
        return "Clipboard is empty."
    }

    // --- BROWSER / WEB ---
    fun openWebsite(url: String): String {
        val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "Opening website: $formattedUrl."
        } catch (e: Exception) {
            "No web browser application found."
        }
    }

    fun searchWeb(query: String, platform: String = "google"): String {
        val url = when (platform.lowercase(Locale.ROOT)) {
            "youtube" -> "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
            "wikipedia" -> "https://en.wikipedia.org/wiki/Special:Search?search=${Uri.encode(query)}"
            else -> "https://www.google.com/search?q=${Uri.encode(query)}"
        }
        return openWebsite(url)
    }

    // --- SETTINGS INTENTS FALLBACK ---
    fun openSettingsScreen(screenType: String): String {
        val action = when (screenType.lowercase(Locale.ROOT)) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "developer" -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "applications", "app_settings" -> Settings.ACTION_APPLICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening setting screen: $screenType."
        } catch (e: Exception) {
            "Failed to open settings panel: ${e.localizedMessage}"
        }
    }
}
