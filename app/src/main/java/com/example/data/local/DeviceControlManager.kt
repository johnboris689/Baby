package com.example.data.local

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale

data class AppInfo(val label: String, val packageName: String)

class DeviceControlManager(private val context: Context) {

    private val tag = "DeviceControlManager"

    companion object {
        val COMMON_ALIASES = mapOf(
            "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "google play" to listOf("com.android.vending"),
            "play store" to listOf("com.android.vending"),
            "store" to listOf("com.android.vending"),
            "chrome" to listOf("com.android.chrome"),
            "browser" to listOf("com.android.chrome", "org.mozilla.firefox"),
            "youtube" to listOf("com.google.android.youtube"),
            "settings" to listOf("com.android.settings"),
            "calculator" to listOf("com.google.android.calculator", "com.sec.android.app.popupcalculator", "com.android.calculator2"),
            "calc" to listOf("com.google.android.calculator", "com.sec.android.app.popupcalculator", "com.android.calculator2"),
            "phone" to listOf("com.google.android.dialer", "com.samsung.android.dialer", "com.android.dialer"),
            "dialer" to listOf("com.google.android.dialer", "com.samsung.android.dialer", "com.android.dialer"),
            "gallery" to listOf("com.google.android.apps.photos", "com.sec.android.gallery3d"),
            "photos" to listOf("com.google.android.apps.photos"),
            "camera" to listOf("com.google.android.GoogleCamera", "com.sec.android.app.camera", "com.android.camera2", "com.android.camera"),
            "contacts" to listOf("com.google.android.contacts", "com.samsung.android.app.contacts"),
            "files" to listOf("com.google.android.documentsui", "com.sec.android.app.myfiles"),
            "file manager" to listOf("com.google.android.documentsui", "com.sec.android.app.myfiles"),
            "messages" to listOf("com.google.android.apps.messaging", "com.samsung.android.messaging"),
            "sms" to listOf("com.google.android.apps.messaging", "com.samsung.android.messaging"),
            "gmail" to listOf("com.google.android.gm"),
            "email" to listOf("com.google.android.gm"),
            "clock" to listOf("com.google.android.deskclock", "com.sec.android.app.clockpackage"),
            "alarm" to listOf("com.google.android.deskclock", "com.sec.android.app.clockpackage"),
            "music" to listOf("com.google.android.apps.youtube.music", "com.spotify.music"),
            "spotify" to listOf("com.spotify.music"),
            "telegram" to listOf("org.telegram.messenger"),
            "facebook" to listOf("com.facebook.katana"),
            "instagram" to listOf("com.instagram.android"),
            "x" to listOf("com.twitter.android"),
            "twitter" to listOf("com.twitter.android"),
            "tiktok" to listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
            "maps" to listOf("com.google.android.apps.maps"),
            "recorder" to listOf("com.google.android.soundrecorder")
        )
    }

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
            "Could not turn flashlight ${if (enabled) "on" else "off"}: ${e.localizedMessage}"
        }
    }

    // --- APP LAUNCHER ---
    fun getInstalledApps(): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
        try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            for (info in resolveInfos) {
                val label = info.loadLabel(pm).toString().trim()
                val packageName = info.activityInfo.packageName
                if (label.isNotEmpty()) {
                    apps.add(AppInfo(label, packageName))
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error querying installed apps", e)
        }
        return apps
    }

    fun launchApp(appNameQuery: String): String {
        val cleanQuery = appNameQuery.lowercase(Locale.ROOT).trim()
        if (cleanQuery.isEmpty()) return "Please specify an application to open."

        val pm = context.packageManager
        val installedApps = getInstalledApps()

        // 1. Check direct aliases first
        val aliasPackages = COMMON_ALIASES[cleanQuery]
        if (aliasPackages != null) {
            for (pkg in aliasPackages) {
                try {
                    val intent = pm.getLaunchIntentForPackage(pkg)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        val label = installedApps.find { it.packageName == pkg }?.label ?: appNameQuery
                        return "Opening $label."
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to launch alias package $pkg: ${e.message}", e)
                    return "Failed to open $appNameQuery: ${e.localizedMessage}"
                }
            }
        }

        // 2. Exact label match
        var matchedApp = installedApps.find { it.label.lowercase(Locale.ROOT) == cleanQuery }

        // 3. Label startsWith or contains
        if (matchedApp == null) {
            matchedApp = installedApps.find { it.label.lowercase(Locale.ROOT).startsWith(cleanQuery) }
        }
        if (matchedApp == null) {
            matchedApp = installedApps.find { it.label.lowercase(Locale.ROOT).contains(cleanQuery) }
        }

        // 4. Fuzzy match score > 0.55
        if (matchedApp == null) {
            var bestScore = 0.0
            var bestApp: AppInfo? = null
            for (app in installedApps) {
                val score = similarityScore(cleanQuery, app.label)
                if (score > bestScore) {
                    bestScore = score
                    bestApp = app
                }
            }
            if (bestScore > 0.55 && bestApp != null) {
                matchedApp = bestApp
            }
        }

        // 5. Package name match
        if (matchedApp == null) {
            matchedApp = installedApps.find { it.packageName.lowercase(Locale.ROOT).contains(cleanQuery) }
        }

        if (matchedApp != null) {
            try {
                val intent = pm.getLaunchIntentForPackage(matchedApp.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return "Opening ${matchedApp.label}."
                } else {
                    return "Could not launch ${matchedApp.label} (no launch intent found)."
                }
            } catch (e: Exception) {
                Log.e(tag, "Exception starting activity for ${matchedApp.label}", e)
                return "Failed to open ${matchedApp.label}: ${e.localizedMessage}"
            }
        }

        val capitalizedName = appNameQuery.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        return "$capitalizedName is not installed on this device."
    }

    private fun similarityScore(s1: String, s2: String): Double {
        val str1 = s1.lowercase(Locale.ROOT).trim()
        val str2 = s2.lowercase(Locale.ROOT).trim()
        if (str1 == str2) return 1.0
        if (str1.contains(str2) || str2.contains(str1)) return 0.8
        val distance = levenshteinDistance(str1, str2)
        val maxLength = maxOf(str1.length, str2.length)
        return if (maxLength == 0) 1.0 else 1.0 - (distance.toDouble() / maxLength.toDouble())
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
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
            "Failed to change volume: ${e.localizedMessage}"
        }
    }

    // --- RINGER MODE ---
    fun setRingerMode(mode: String): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return "Audio manager unavailable."
        return try {
            when (mode.lowercase(Locale.ROOT)) {
                "silent" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        if (notificationManager?.isNotificationPolicyAccessGranted == false) {
                            openSettingsScreen("dnd")
                            return "Grant Do Not Disturb permission in Settings to enable silent mode."
                        }
                    }
                    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                    "Ringer set to Silent mode."
                }
                "vibrate" -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    "Ringer set to Vibrate mode."
                }
                "normal", "sound" -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    "Ringer set to Normal sound mode."
                }
                else -> "Ringer mode updated."
            }
        } catch (e: Exception) {
            Log.e(tag, "Error setting ringer mode", e)
            "Could not set ringer mode: ${e.localizedMessage}"
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
            "Could not set screen brightness: ${e.localizedMessage}"
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

    // --- SCREEN TIMEOUT ---
    fun setScreenTimeout(seconds: Int): String {
        if (!Settings.System.canWrite(context)) {
            openSettingsScreen("display")
            return "Permission required to change screen timeout."
        }
        return try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, seconds * 1000)
            "Screen timeout set to $seconds seconds."
        } catch (e: Exception) {
            "Could not set screen timeout: ${e.localizedMessage}"
        }
    }

    // --- BLUETOOTH & WIFI ---
    fun setBluetoothState(enabled: Boolean): String {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter
            ?: return "Bluetooth is not supported on this device."

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            openSettingsScreen("bluetooth")
            return "Permission or manual action is required to toggle Bluetooth."
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
            openSettingsScreen("bluetooth")
            "Opening Bluetooth settings."
        } catch (e: Exception) {
            "Could not change Bluetooth state: ${e.localizedMessage}"
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
            "Could not change Wi-Fi state: ${e.localizedMessage}"
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
            "Could not change Do Not Disturb mode: ${e.localizedMessage}"
        }
    }

    // --- SYSTEM INFO (BATTERY, STORAGE, MEMORY) ---
    fun getBatteryInfo(): String {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val chargingStr = if (isCharging) " (charging)" else ""
            if (level >= 0) {
                "Battery level is $level%$chargingStr."
            } else {
                "Battery status unavailable."
            }
        } catch (e: Exception) {
            "Could not read battery info: ${e.localizedMessage}"
        }
    }

    fun getStorageInfo(): String {
        return try {
            val path = Environment.getDataDirectory().path
            val stat = StatFs(path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalGB = (totalBlocks * blockSize) / (1024f * 1024f * 1024f)
            val freeGB = (availableBlocks * blockSize) / (1024f * 1024f * 1024f)

            "Storage: ${"%.1f".format(freeGB)} GB free of ${"%.1f".format(totalGB)} GB total."
        } catch (e: Exception) {
            "Could not read storage info: ${e.localizedMessage}"
        }
    }

    fun getMemoryInfo(): String {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return "Memory info unavailable."
            val mi = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(mi)
            val availMB = mi.availMem / (1024 * 1024)
            val totalMB = mi.totalMem / (1024 * 1024)
            "Memory (RAM): ${availMB} MB free of ${totalMB} MB total."
        } catch (e: Exception) {
            "Could not read memory info: ${e.localizedMessage}"
        }
    }

    // --- ALARMS & CALENDAR ---
    fun openAlarms(): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                "Opening Alarms."
            } else {
                launchApp("clock")
            }
        } catch (e: Exception) {
            launchApp("clock")
        }
    }

    fun openCalendar(): String {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALENDAR)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening Calendar."
        } catch (e: Exception) {
            openWebsite("https://calendar.google.com")
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
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return "Opening dialer for $contactName."
            }
        } catch (e: Exception) {
            Log.e(tag, "Error placing call", e)
            return "I couldn't place that call: ${e.localizedMessage}"
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
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phoneNumber")).apply {
                    putExtra("sms_body", message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return "Opening Messaging for $contactName."
            }
        } catch (e: Exception) {
            Log.e(tag, "Error sending SMS", e)
            return "I couldn't send that SMS: ${e.localizedMessage}"
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
            "I couldn't open the camera: ${e.localizedMessage}"
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
            "Could not control music playback: ${e.localizedMessage}"
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
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "dnd" -> Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening settings."
        } catch (e: Exception) {
            "Failed to open settings: ${e.localizedMessage}"
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
