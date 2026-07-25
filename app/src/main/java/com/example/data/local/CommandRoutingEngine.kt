package com.example.data.local

import java.util.Locale

sealed class RoutingResult {
    data class LocalCommand(val responseText: String) : RoutingResult()
    object SendToGemini : RoutingResult()
}

class CommandRoutingEngine(private val deviceControlManager: DeviceControlManager) {

    fun routeAndExecute(rawInput: String, isChainedSubcommand: Boolean = false): RoutingResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return RoutingResult.SendToGemini

        val lower = trimmed.lowercase(Locale.ROOT)

        // 0. MULTI-ACTION COMMAND CHAINING ("and", "then")
        if (!isChainedSubcommand && (lower.contains(" and ") || lower.contains(" then "))) {
            val delimiter = if (lower.contains(" and ")) " and " else " then "
            val parts = trimmed.split(Regex("(?i)$delimiter")).map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size > 1) {
                val results = mutableListOf<String>()
                var allLocal = true
                for (part in parts) {
                    when (val res = routeAndExecute(part, isChainedSubcommand = true)) {
                        is RoutingResult.LocalCommand -> results.add(res.responseText)
                        is RoutingResult.SendToGemini -> {
                            allLocal = false
                            break
                        }
                    }
                }
                if (allLocal && results.isNotEmpty()) {
                    return RoutingResult.LocalCommand(results.joinToString(" "))
                }
            }
        }

        // 1. FLASHLIGHT COMMANDS
        if (isFlashlightOnCommand(lower)) {
            return RoutingResult.LocalCommand(deviceControlManager.setFlashlight(true))
        }
        if (isFlashlightOffCommand(lower)) {
            return RoutingResult.LocalCommand(deviceControlManager.setFlashlight(false))
        }

        // 2. VOLUME & SOUND / RINGER MODE
        if (lower.contains("silent mode") || lower.contains("ringer silent") || lower == "silent") {
            return RoutingResult.LocalCommand(deviceControlManager.setRingerMode("silent"))
        }
        if (lower.contains("vibrate mode") || lower.contains("ringer vibrate") || lower == "vibrate") {
            return RoutingResult.LocalCommand(deviceControlManager.setRingerMode("vibrate"))
        }
        if (lower.contains("normal sound mode") || lower.contains("ringer normal") || lower.contains("sound mode")) {
            return RoutingResult.LocalCommand(deviceControlManager.setRingerMode("normal"))
        }
        if (lower.contains("increase volume") || lower.contains("volume up") || lower == "louder" || lower.contains("raise volume")) {
            return RoutingResult.LocalCommand(deviceControlManager.controlVolume("increase"))
        }
        if (lower.contains("decrease volume") || lower.contains("reduce volume") || lower.contains("lower volume") || lower.contains("volume down") || lower == "quieter") {
            return RoutingResult.LocalCommand(deviceControlManager.controlVolume("decrease"))
        }
        if (lower.contains("mute phone") || lower.contains("mute volume") || lower == "mute") {
            return RoutingResult.LocalCommand(deviceControlManager.controlVolume("mute"))
        }
        if (lower.contains("unmute phone") || lower.contains("unmute volume") || lower == "unmute") {
            return RoutingResult.LocalCommand(deviceControlManager.controlVolume("unmute"))
        }

        // 3. BRIGHTNESS COMMANDS
        if (lower.contains("maximum brightness") || lower.contains("max brightness") || lower == "full brightness") {
            return RoutingResult.LocalCommand(deviceControlManager.setBrightness(1.0f))
        }
        if (lower.contains("minimum brightness") || lower.contains("min brightness") || lower == "lowest brightness" || lower.contains("dim screen")) {
            return RoutingResult.LocalCommand(deviceControlManager.setBrightness(0.05f))
        }
        if (lower.contains("increase brightness") || lower.contains("brightness up") || lower.contains("higher brightness")) {
            return RoutingResult.LocalCommand(deviceControlManager.adjustBrightnessStep(increase = true))
        }
        if (lower.contains("reduce brightness") || lower.contains("decrease brightness") || lower.contains("lower brightness") || lower.contains("brightness down")) {
            return RoutingResult.LocalCommand(deviceControlManager.adjustBrightnessStep(increase = false))
        }
        if (lower.contains("brightness ") || lower.contains("brightness to ")) {
            val rawNum = lower.substringAfter("brightness").replace("to", "").trim().removeSuffix("%").trim()
            val parsed = rawNum.toFloatOrNull()
            if (parsed != null) {
                val value = (parsed / 100f).coerceIn(0.05f, 1.0f)
                return RoutingResult.LocalCommand(deviceControlManager.setBrightness(value))
            }
        }

        // 4. SCREEN TIMEOUT
        if (lower.contains("screen timeout")) {
            val seconds = lower.filter { it.isDigit() }.toIntOrNull() ?: 30
            return RoutingResult.LocalCommand(deviceControlManager.setScreenTimeout(seconds))
        }

        // 5. BLUETOOTH COMMANDS
        if (lower.contains("enable bluetooth") || lower.contains("turn on bluetooth") || lower.contains("bluetooth on") || lower == "turn bluetooth on" || lower.contains("switch on bluetooth")) {
            return RoutingResult.LocalCommand(deviceControlManager.setBluetoothState(true))
        }
        if (lower.contains("disable bluetooth") || lower.contains("turn off bluetooth") || lower.contains("bluetooth off") || lower == "turn bluetooth off" || lower.contains("switch off bluetooth")) {
            return RoutingResult.LocalCommand(deviceControlManager.setBluetoothState(false))
        }

        // 6. WI-FI COMMANDS
        if (lower.contains("enable wifi") || lower.contains("turn on wifi") || lower.contains("wifi on") || lower == "turn wifi on" || lower.contains("switch on wifi")) {
            return RoutingResult.LocalCommand(deviceControlManager.setWifiState(true))
        }
        if (lower.contains("disable wifi") || lower.contains("turn off wifi") || lower.contains("wifi off") || lower == "turn wifi off" || lower.contains("switch off wifi")) {
            return RoutingResult.LocalCommand(deviceControlManager.setWifiState(false))
        }

        // 7. HOTSPOT & MOBILE DATA & AIRPLANE MODE
        if (lower.contains("hotspot")) {
            return RoutingResult.LocalCommand(deviceControlManager.openHotspotSettings())
        }
        if (lower.contains("mobile data") || lower.contains("cellular data")) {
            return RoutingResult.LocalCommand(deviceControlManager.openSettingsScreen("tethering"))
        }
        if (lower.contains("airplane mode") || lower.contains("flight mode")) {
            return RoutingResult.LocalCommand(deviceControlManager.openSettingsScreen("tethering"))
        }

        // 8. DO NOT DISTURB (DND) COMMANDS
        if (lower.contains("enable do not disturb") || lower.contains("turn dnd on") || lower.contains("dnd on") || lower == "enable dnd" || lower.contains("turn on do not disturb")) {
            return RoutingResult.LocalCommand(deviceControlManager.setDNDMode(true))
        }
        if (lower.contains("disable do not disturb") || lower.contains("turn dnd off") || lower.contains("dnd off") || lower == "disable dnd" || lower.contains("turn off do not disturb")) {
            return RoutingResult.LocalCommand(deviceControlManager.setDNDMode(false))
        }

        // 9. SYSTEM INFO (BATTERY, STORAGE, MEMORY)
        if (lower.contains("battery level") || lower.contains("battery status") || lower.contains("battery info") || lower.contains("how much battery") || lower.contains("battery percentage") || lower == "battery") {
            return RoutingResult.LocalCommand(deviceControlManager.getBatteryInfo())
        }
        if (lower.contains("storage info") || lower.contains("storage status") || lower.contains("free storage") || lower.contains("how much storage") || lower.contains("storage space") || lower == "storage") {
            return RoutingResult.LocalCommand(deviceControlManager.getStorageInfo())
        }
        if (lower.contains("memory info") || lower.contains("ram status") || lower.contains("free ram") || lower.contains("memory usage") || lower.contains("ram info") || lower == "memory" || lower == "ram") {
            return RoutingResult.LocalCommand(deviceControlManager.getMemoryInfo())
        }

        // 10. ALARMS & CALENDAR
        if (lower.contains("open alarm") || lower.contains("show alarm") || lower.contains("set alarm") || lower.contains("open clock")) {
            return RoutingResult.LocalCommand(deviceControlManager.openAlarms())
        }
        if (lower.contains("open calendar") || lower.contains("show calendar")) {
            return RoutingResult.LocalCommand(deviceControlManager.openCalendar())
        }

        // 11. CAMERA, SELFIE, VIDEO, RECORD SOUND
        if (lower.contains("take selfie") || lower == "selfie") {
            return RoutingResult.LocalCommand(deviceControlManager.openCameraMode("selfie"))
        }
        if (lower.contains("record video") || lower == "take video") {
            return RoutingResult.LocalCommand(deviceControlManager.openCameraMode("video"))
        }
        if (lower.contains("open recorder") || lower.contains("start recorder") || lower.contains("voice recorder")) {
            return RoutingResult.LocalCommand(deviceControlManager.openCameraMode("recorder"))
        }
        if (lower == "open camera" || lower == "launch camera" || lower == "start camera" || lower == "camera") {
            return RoutingResult.LocalCommand(deviceControlManager.openCameraMode("normal"))
        }

        // 12. RECENT APPS
        if (lower.contains("open recent apps") || lower == "recent apps") {
            return RoutingResult.LocalCommand(deviceControlManager.openRecentApps())
        }

        // 13. MUSIC / MEDIA CONTROL
        if (lower.contains("play music") || lower.contains("resume music") || lower == "play song") {
            return RoutingResult.LocalCommand(deviceControlManager.controlMusic("play"))
        }
        if (lower.contains("pause music") || lower.contains("stop music") || lower == "pause song") {
            return RoutingResult.LocalCommand(deviceControlManager.controlMusic("pause"))
        }
        if (lower.contains("next song") || lower.contains("next track") || lower == "skip song") {
            return RoutingResult.LocalCommand(deviceControlManager.controlMusic("next"))
        }
        if (lower.contains("previous song") || lower.contains("previous track")) {
            return RoutingResult.LocalCommand(deviceControlManager.controlMusic("previous"))
        }

        // 14. PHONE CALL COMMANDS
        if (lower.startsWith("call ") || lower.startsWith("dial ") || lower.startsWith("place call to ")) {
            val target = trimmed.substringAfter("call").substringAfter("dial").substringAfter("to").trim()
            if (target.isNotEmpty()) {
                return RoutingResult.LocalCommand(deviceControlManager.makeCall(target))
            }
        }

        // 15. SMS COMMANDS
        if (lower.startsWith("send sms to ") || lower.startsWith("text ")) {
            val rest = trimmed.substringAfter("send sms to").substringAfter("text").trim()
            val parts = rest.split(" ", limit = 2)
            if (parts.isNotEmpty()) {
                val contact = parts[0]
                val msg = if (parts.size > 1) parts[1] else ""
                return RoutingResult.LocalCommand(deviceControlManager.sendSMS(contact, msg))
            }
        }

        // 16. NAVIGATION & MAPS COMMANDS
        if (lower.startsWith("navigate to ") || lower.startsWith("directions to ")) {
            val dest = trimmed.substringAfter("navigate to").substringAfter("directions to").trim()
            return RoutingResult.LocalCommand(deviceControlManager.openMaps(dest))
        }

        // 17. WEB & YOUTUBE & WIKIPEDIA SEARCH
        if (lower.startsWith("search youtube for ")) {
            val q = trimmed.substringAfter("search youtube for").trim()
            return RoutingResult.LocalCommand(deviceControlManager.searchWeb(q, "youtube"))
        }
        if (lower.startsWith("search google for ") || lower.startsWith("search for ")) {
            val q = trimmed.substringAfter("search google for").substringAfter("search for").trim()
            return RoutingResult.LocalCommand(deviceControlManager.searchWeb(q, "google"))
        }
        if (lower.startsWith("search wikipedia for ")) {
            val q = trimmed.substringAfter("search wikipedia for").trim()
            return RoutingResult.LocalCommand(deviceControlManager.searchWeb(q, "wikipedia"))
        }
        if (lower.startsWith("open website ")) {
            val site = trimmed.substringAfter("open website").trim()
            return RoutingResult.LocalCommand(deviceControlManager.openWebsite(site))
        }

        // 18. SETTINGS SHORTCUTS
        if (lower == "go to settings" || lower == "open settings" || lower == "settings") {
            return RoutingResult.LocalCommand(deviceControlManager.openSettingsScreen("settings"))
        }
        if (lower.contains("open wifi settings") || lower.contains("wifi settings")) return RoutingResult.LocalCommand(deviceControlManager.openSettingsScreen("wifi"))
        if (lower.contains("open bluetooth settings") || lower.contains("bluetooth settings")) return RoutingResult.LocalCommand(deviceControlManager.openSettingsScreen("bluetooth"))
        if (lower.contains("open battery settings") || lower.contains("battery settings")) return RoutingResult.LocalCommand(deviceControlManager.openSettingsScreen("battery"))
        if (lower.contains("open developer settings") || lower.contains("open developer options") || lower.contains("developer options")) return RoutingResult.LocalCommand(deviceControlManager.openSettingsScreen("developer"))
        if (lower.contains("open accessibility settings") || lower.contains("accessibility settings")) return RoutingResult.LocalCommand(deviceControlManager.openSettingsScreen("accessibility"))
        if (lower.contains("open app settings") || lower.contains("application settings")) return RoutingResult.LocalCommand(deviceControlManager.openSettingsScreen("applications"))
        if (lower.contains("open display settings") || lower.contains("display settings")) return RoutingResult.LocalCommand(deviceControlManager.openSettingsScreen("display"))

        // 19. CLIPBOARD
        if (lower.contains("read clipboard") || lower.contains("what is in my clipboard") || lower == "clipboard") {
            return RoutingResult.LocalCommand("In your clipboard: " + deviceControlManager.readClipboard())
        }

        // 20. APP LAUNCH COMMANDS ("open whatsapp", "launch chrome", "start calculator", "run instagram", etc.)
        val appName = extractAppLaunchName(lower, trimmed)
        if (appName != null && appName.isNotEmpty()) {
            return RoutingResult.LocalCommand(deviceControlManager.launchApp(appName))
        }

        // Default: If no local device command matches, route to Gemini Cloud AI for conversational AI
        return RoutingResult.SendToGemini
    }

    private fun isFlashlightOnCommand(text: String): Boolean {
        return text.contains("turn flashlight on") ||
                text.contains("turn on flashlight") ||
                text.contains("torch on") ||
                text.contains("switch on flashlight") ||
                text.contains("switch on my torch") ||
                text.contains("turn on the flashlight") ||
                text.contains("turn on torch") ||
                text.contains("enable flashlight") ||
                text == "flashlight on" ||
                text == "torch on"
    }

    private fun isFlashlightOffCommand(text: String): Boolean {
        return text.contains("turn flashlight off") ||
                text.contains("turn off flashlight") ||
                text.contains("torch off") ||
                text.contains("switch off flashlight") ||
                text.contains("switch off my torch") ||
                text.contains("turn off the flashlight") ||
                text.contains("turn off torch") ||
                text.contains("disable flashlight") ||
                text == "flashlight off" ||
                text == "torch off"
    }

    private fun extractAppLaunchName(lowerText: String, originalText: String): String? {
        val prefixes = listOf("open ", "launch ", "start ", "run ", "i want ", "go to ")
        for (prefix in prefixes) {
            if (lowerText.startsWith(prefix)) {
                val candidate = originalText.substring(prefix.length).trim()
                // Filter out non-app phrases like "open settings screen" or search queries
                if (candidate.isNotEmpty() && !candidate.contains("for ") && candidate.split(" ").size <= 4) {
                    return candidate
                }
            }
        }
        return null
    }
}
