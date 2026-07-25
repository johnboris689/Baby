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
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.provider.ContactsContract
import android.provider.MediaStore
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
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            if (cameraManager != null) {
                val cameraId = cameraManager.cameraIdList.firstOrNull()
                if (cameraId != null) {
                    cameraManager.setTorchMode(cameraId, enabled)
                    "Flashlight turned ${if (enabled) "on" else "off"}."
                } else {
                    "No flashlight hardware found on this device."
                }
            } else {
                "Flashlight is not supported on this device."
            }
        } catch (e: Exception) {
            Log.e(tag, "Error setting flashlight", e)
            "Could not turn flashlight ${if (enabled) "on" else "off"}."
        }
    }

    // --- APP LAUNCHER ---
    fun getInstalledApps(): List<Pair<String, String>> {
        val apps = mutableListOf<Pair<String, String>>()
        try {
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
        } catch (e: Exception) {
            Log.e(tag, "Error querying installed apps", e)
        }
        return apps
    }

    fun launchApp(appNameQuery: String): String {
        try {
            val pm = context.packageManager
            val cleanQuery = appNameQuery.lowercase(Locale.ROOT).trim()
            val apps = getInstalledApps()

            // 1. Try exact match first
            var matchedApp = apps.find { it.first.lowercase(Locale.ROOT) == cleanQuery }
            
            // 2. Try partial match
            if (matchedApp == null) {
                matchedApp = apps.find { it.first.lowercase(Locale.ROOT).contains(cleanQuery) }
            }

            // 3. Known package aliases fallback
            val pkgFallback = when (cleanQuery) {
                "whatsapp" -> "com.whatsapp"
                "facebook" -> "com.facebook.katana"
                "instagram" -> "com.instagram.android"
                "telegram" -> "org.telegram.messenger"
                "chrome" -> "com.android.chrome"
                "settings" -> "com.android.settings"
                "calculator", "calc" -> "com.google.android.calculator"
                "camera" -> "com.android.camera"
                "gallery", "photos" -> "com.google.android.apps.photos"
                "youtube" -> "com.google.android.youtube"
                "gmail" -> "com.google.android.gm"
                "maps", "google maps" -> "com.google.android.apps.maps"
                "play store", "store" -> "com.android.vending"
                "file manager", "files" -> "com.google.android.documentsui"
                "contacts" -> "com.google.android.contacts"
                "recorder", "sound recorder" -> "com.google.android.soundrecorder"
                else -> null
            }

            if (matchedApp != null) {
                val intent = pm.getLaunchIntentForPackage(matchedApp.second)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return "Opening ${matchedApp.first}."
                }
            }

            if (pkgFallback != null) {
                val intent = pm.getLaunchIntentForPackage(pkgFallback)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return "Opening $appNameQuery."
                }
            }

            // Capitalize for clean message
            val capitalizedName = appNameQuery.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            return "$capitalizedName is not installed."
        } catch (e: Exception) {
            Log.e(tag, "Error launching app: $appNameQuery", e)
            return "I couldn't open that app."
        }
    }

    // --- VOLUME & SOUND ---
    fun controlVolume(action: String): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return "Audio system not available."

        return try {
            when (action.lowercase(Locale.ROOT)) {
                "increase", "up", "louder" -> {
                    audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    "Volume increased."
                }
                "decrease", "down", "quieter" -> {
                    audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    "Volume decreased."
                }
                "mute" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true)
                    }
                    "Phone muted."
                }
                "unmute" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false)
                    }
                    "Phone unmuted."
                }
                else -> "Volume updated."
            }
        } catch (e: Exception) {
            Log.e(tag, "Error controlling volume", e)
            "Failed to change volume."
        }
    }

    // --- BRIGHTNESS ---
    fun setBrightness(brightnessValue: Float): String {
        if (!Settings.System.canWrite(context)) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(tag, "Write settings permission intent failed", e)
            }
            return "Permission is required to modify screen brightness."
        }

        return try {
            val systemBrightness = (brightnessValue * 255).toInt().coerceIn(0, 255)
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                systemBrightness
            )
            "Screen brightness set to ${(brightnessValue * 100).toInt()}%."
        } catch (e: Exception) {
            Log.e(tag, "Failed to set brightness", e)
            "Could not set screen brightness."
        }
    }

    fun adjustBrightnessStep(increase: Boolean): String {
        if (!Settings.System.canWrite(context)) {
            return setBrightness(0.5f)
        }
        return try {
            val current = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            val currentFloat = current / 255f
            val target = if (increase) (currentFloat + 0.25f).coerceAtMost(1.0f) else (currentFloat - 0.25f).coerceAtLeast(0.05f)
            setBrightness(target)
        } catch (e: Exception) {
            setBrightness(if (increase) 0.8f else 0.2f)
        }
    }

    // --- BLUETOOTH & WIFI ---
    fun setBluetoothState(enabled: Boolean): String {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter
            ?: return "Bluetooth is not supported on this device."

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return "Permission is required to perform that action."
        }

        return try {
            if (enabled) {
                @Suppress("DEPRECATION")
                bluetoothAdapter.enable()
                "Bluetooth enabled."
            } else {
                @Suppress("DEPRECATION")
                bluetoothAdapter.disable()
                "Bluetooth disabled."
            }
        } catch (e: SecurityException) {
            try {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opening Bluetooth settings."
            } catch (ex: Exception) {
                "Permission is required to control Bluetooth."
            }
        } catch (e: Exception) {
            "Could not change Bluetooth state."
        }
    }

    fun setWifiState(enabled: Boolean): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return "Wi-Fi is not supported."

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val intent = Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opening Wi-Fi settings panel."
            } else {
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = enabled
                "Wi-Fi ${if (enabled) "enabled" else "disabled"}."
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to toggle Wi-Fi", e)
            "Could not change Wi-Fi state."
        }
    }

    fun openHotspotSettings(): String {
        return try {
            val intent = Intent().apply {
                action = Settings.ACTION_WIRELESS_SETTINGS
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening Hotspot settings."
        } catch (e: Exception) {
            openSettingsScreen("tethering")
        }
    }

    // --- DO NOT DISTURB ---
    fun setDNDMode(enabled: Boolean): String {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return "Notification policy unavailable."

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!notificationManager.isNotificationPolicyAccessGranted) {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return "Please grant Do Not Disturb permission in Settings."
                }

                val filter = if (enabled) {
                    NotificationManager.INTERRUPTION_FILTER_NONE
                } else {
                    NotificationManager.INTERRUPTION_FILTER_ALL
                }
                notificationManager.setInterruptionFilter(filter)
                "Do Not Disturb ${if (enabled) "enabled" else "disabled"}."
            } else {
                "DND requires Android M or higher."
            }
        } catch (e: Exception) {
            Log.e(tag, "Error setting DND", e)
            "Could not change Do Not Disturb mode."
        }
    }

    // --- CONTACTS & CALLS ---
    data class ContactInfo(val name: String, val phoneNumber: String)

    fun searchContacts(query: String): List<ContactInfo> {
        val list = mutableListOf<ContactInfo>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return list
        }
        try {
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
                        val name = it.getString(nameIndex) ?: ""
                        val phone = it.getString(numIndex) ?: ""
                        if (name.isNotEmpty() && phone.isNotEmpty()) {
                            list.add(ContactInfo(name, phone))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error searching contacts", e)
        }
        return list
    }

    fun makeCall(queryOrPhone: String): String {
        try {
            var phoneNumber = queryOrPhone.filter { it.isDigit() || it == '+' }
            var contactName = queryOrPhone

            if (phoneNumber.length < 3) {
                val contacts = searchContacts(queryOrPhone)
                if (contacts.isNotEmpty()) {
                    phoneNumber = contacts[0].phoneNumber
                    contactName = contacts[0].name
                }
            }

            if (phoneNumber.isEmpty()) {
                return "Contact '$queryOrPhone' was not found."
            }

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return "Calling $contactName."
            } else {
                // Fallback to dialer
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return "Opening dialer for $contactName."
            }
        } catch (e: Exception) {
            Log.e(tag, "Error placing call", e)
            return "I couldn't place that call."
        }
    }

    // --- SMS ---
    fun sendSMS(queryOrPhone: String, message: String): String {
        try {
            var phoneNumber = queryOrPhone.filter { it.isDigit() || it == '+' }
            var contactName = queryOrPhone

            if (phoneNumber.length < 3) {
                val contacts = searchContacts(queryOrPhone)
                if (contacts.isNotEmpty()) {
                    phoneNumber = contacts[0].phoneNumber
                    contactName = contacts[0].name
                }
            }

            if (phoneNumber.isEmpty()) {
                return "Contact '$queryOrPhone' was not found."
            }

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                return "SMS sent to $contactName."
            } else {
                // Fallback to default SMS App
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phoneNumber")).apply {
                    putExtra("sms_body", message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return "Opening Messaging for $contactName."
            }
        } catch (e: Exception) {
            Log.e(tag, "Error sending SMS", e)
            return "I couldn't send that SMS."
        }
    }

    // --- CAMERA / SELFIE / RECORD ---
    fun openCameraMode(mode: String): String {
        return try {
            when (mode.lowercase(Locale.ROOT)) {
                "selfie" -> {
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        putExtra("android.intent.extras.CAMERA_FACING", 1)
                        putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
                        putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                        "Opening selfie camera."
                    } else {
                        launchApp("camera")
                    }
                }
                "video" -> {
                    val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                        "Opening video recorder."
                    } else {
                        launchApp("camera")
                    }
                }
                "recorder" -> {
                    launchApp("recorder")
                }
                else -> {
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                        "Opening camera."
                    } else {
                        launchApp("camera")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error opening camera mode: $mode", e)
            "I couldn't open the camera."
        }
    }

    // --- RECENT APPS ---
    fun openRecentApps(): String {
        return try {
            openSettingsScreen("accessibility")
        } catch (e: Exception) {
            "Could not open recent apps."
        }
    }

    // --- NAVIGATION & MAPS ---
    fun openMaps(query: String? = null): String {
        return try {
            val uri = if (!query.isNullOrEmpty()) {
                Uri.parse("geo:0,0?q=${Uri.encode(query)}")
            } else {
                Uri.parse("geo:0,0")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            if (!query.isNullOrEmpty()) "Navigating to $query." else "Opening Maps."
        } catch (e: Exception) {
            "Maps application not available."
        }
    }

    // --- MUSIC CONTROL ---
    fun controlMusic(action: String): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return "Audio system not available."

        return try {
            val eventCode = when (action.lowercase(Locale.ROOT)) {
                "play", "resume" -> KeyEvent.KEYCODE_MEDIA_PLAY
                "pause", "stop" -> KeyEvent.KEYCODE_MEDIA_PAUSE
                "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                else -> return "Unknown music command."
            }

            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, eventCode)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, eventCode)
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)
            "Music ${action.lowercase(Locale.ROOT)}."
        } catch (e: Exception) {
            Log.e(tag, "Error controlling music", e)
            "Could not control music playback."
        }
    }

    // --- BROWSER / WEB ---
    fun searchWeb(query: String, platform: String = "google"): String {
        return try {
            val url = when (platform.lowercase(Locale.ROOT)) {
                "youtube" -> "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
                "wikipedia" -> "https://en.wikipedia.org/wiki/Special:Search?search=${Uri.encode(query)}"
                else -> "https://www.google.com/search?q=${Uri.encode(query)}"
            }
            openWebsite(url)
        } catch (e: Exception) {
            "Could not perform web search."
        }
    }

    fun openWebsite(url: String): String {
        return try {
            val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening $formattedUrl."
        } catch (e: Exception) {
            "No web browser found."
        }
    }

    // --- SETTINGS INTENTS ---
    fun openSettingsScreen(screenType: String): String {
        val action = when (screenType.lowercase(Locale.ROOT)) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "developer" -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "applications", "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "tethering", "hotspot" -> Settings.ACTION_WIRELESS_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening settings."
        } catch (e: Exception) {
            "Failed to open settings."
        }
    }

    // --- CLIPBOARD CONTROL ---
    fun copyToClipboard(text: String): String {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return "Clipboard unavailable."
            val clip = ClipData.newPlainText("BabyAI Response", text)
            clipboard.setPrimaryClip(clip)
            "Copied to clipboard."
        } catch (e: Exception) {
            "Failed to copy to clipboard."
        }
    }

    fun readClipboard(): String {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return "Clipboard unavailable."
            val primaryClip = clipboard.primaryClip
            if (primaryClip != null && primaryClip.itemCount > 0) {
                val text = primaryClip.getItemAt(0).text
                if (!text.isNullOrEmpty()) {
                    return text.toString()
                }
            }
            "Clipboard is empty."
        } catch (e: Exception) {
            "Clipboard unavailable."
        }
    }
}
