package com.example.data.local

import java.util.Locale

sealed class RoutingResult {
    data class LocalCommand(val responseText: String) : RoutingResult()
    object SendToGemini : RoutingResult()
}

class CommandRoutingEngine(private val deviceControlManager: DeviceControlManager) {

    fun routeAndExecute(rawInput: String): RoutingResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return RoutingResult.SendToGemini

        val lower = trimmed.lowercase(Locale.ROOT)

        // 1. FLASHLIGHT COMMANDS
        if (isFlashlightOnCommand(lower)) {
            return RoutingResult.LocalCommand(deviceControlManager.setFlashlight(true))
        }
        if (isFlashlightOffCommand(lower)) {
            return RoutingResult.LocalCommand(deviceControlManager.setFlashlight(false))
        }

        // 2. VOLUME COMMANDS
        if (lower.contains("increase volume") || lower.contains("volume up") || lower == "louder") {
            return RoutingResult.LocalCommand(deviceControlManager.controlVolume("increase"))
        }
        if (lower.contains("decrease volume") || lower.contains("reduce volume") || lower.contains("volume down") || lower == "quieter") {
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
        if (lower.contains("minimum brightness") || lower.contains("min brightness") || lower == "lowest brightness") {
            return RoutingResult.LocalCommand(deviceControlManager.setBrightness(0.05f))
        }
        if (lower.contains("increase brightness")) {
            return RoutingResult.LocalCommand(deviceControlManager.adjustBrightnessStep(increase = true))
        }
        if (lower.contains("reduce brightness") || lower.contains("decrease brightness") || lower.contains("lower brightness")) {
            return RoutingResult.LocalCommand(deviceControlManager.adjustBrightnessStep(increase = false))
        }
        if (lower.contains("brightness to ")) {
            val rawNum = lower.substringAfter("brightness to").trim().removeSuffix("%").trim()
            val parsed = rawNum.toFloatOrNull()
            if (parsed != null) {
                val value = (parsed / 100f).coerceIn(0.05f, 1.0f)
                return RoutingResult.LocalCommand(deviceControlManager.setBrightness(value))
            }
        }

        // 4. BLUETOOTH COMMANDS
        if (lower.contains("enable bluetooth") || lower.contains("turn on bluetooth") || lower.contains("bluetooth on") || lower == "turn bluetooth on") {
            return RoutingResult.LocalCommand(deviceControlManager.setBluetoothState(true))
        }
        if (lower.contains("disable bluetooth") || lower.contains("turn off bluetooth") || lower.contains("bluetooth off") || lower == "turn bluetooth off") {
            return RoutingResult.LocalCommand(deviceControlManager.setBluetoothState(false))
        }

        // 5. WI-FI COMMANDS
        if (lower.contains("enable wifi") || lower.contains("turn on wifi") || lower.contains("wifi on") || lower == "turn wifi on") {
            return RoutingResult.LocalCommand(deviceControlManager.setWifiState(true))
        }
        if (lower.contains("disable wifi") || lower.contains("turn off wifi") || lower.contains("wifi off") || lower == "turn wifi off") {
            return RoutingResult.LocalCommand(deviceControlManager.setWifiState(false))
        }

        // 6. HOTSPOT COMMANDS
        if (lower.contains("enable hotspot") || lower.contains("turn on hotspot") || lower.contains("hotspot on") || lower.contains("disable hotspot") || lower.contains("turn off hotspot")) {
            return RoutingResult.LocalCommand(deviceControlManager.openHotspotSettings())
        }

        // 7. DO NOT DISTURB (DND) COMMANDS
        if (lower.contains("enable do not disturb") || lower.contains("turn dnd on") || lower.contains("dnd on") || lower == "enable dnd") {
            return RoutingResult.LocalCommand(deviceControlManager.setDNDMode(true))
        }
        if (lower.contains("disable do not disturb") || lower.contains("turn dnd off") || lower.contains("dnd off") || lower == "disable dnd") {
            return RoutingResult.LocalCommand(deviceControlManager.setDNDMode(false))
        }

        // 8. CAMERA, SELFIE, VIDEO, RECORD SOUND
        if (lower.contains("take selfie") || lower == "selfie") {
            return RoutingResult.LocalCommand(deviceControlManager.openCameraMode("selfie"))
        }
        if (lower.contains("record video") || lower == "take video") {
            return RoutingResult.LocalCommand(deviceControlManager.openCameraMode("video"))
        }
        if (lower.contains("open recorder") || lower.contains("start recorder") || lower.contains("voice recorder")) {
            return RoutingResult.LocalCommand(deviceControlManager.openCameraMode("recorder"))
        }
        if (lower == "open camera" || lower == "launch camera" || lower == "start camera") {
            return RoutingResult.LocalCommand(deviceControlManager.openCameraMode("normal"))
        }

        // 9. RECENT APPS
        if (lower.contains("open recent apps") || lower == "recent apps") {
            return RoutingResult.LocalCommand(deviceControlManager.openRecentApps())
        }

        // 10. PHONE CALL COMMANDS
        if (lower.startsWith("call ") || lower.startsWith("dial ") || lower.startsWith("place call to ")) {
            val target = trimmed.substringAfter("call").substringAfter("dial").substringAfter("to").trim()
            if (target.isNotEmpty()) {
                return RoutingResult.LocalCommand(deviceControlManager.makeCall(target))
            }
        }

        // 11. SMS COMMANDS
        if (lower.startsWith("send sms to ") || lower.startsWith("text ")) {
            val rest = trimmed.substringAfter("send sms to").substringAfter("text").trim()
            val parts = rest.split(" ", limit = 2)
            if (parts.isNotEmpty()) {
                val contact = parts[0]
                val msg = if (parts.size > 1) parts[1] else ""
                return RoutingResult.LocalCommand(deviceControlManager.sendSMS(contact, msg))
            }
        }

        // 12. NAVIGATION & MAPS COMMANDS
        if (lower.startsWith("navigate to ") || lower.startsWith("directions to ")) {
            val dest = trimmed.substringAfter("navigate to").substringAfter("directions to").trim()
            return RoutingResult.LocalCommand(deviceControlManager.openMaps(dest))
        }

        // 13. WEB & YOUTUBE SEARCH
        if (lower.startsWith("search youtube for ")) {
            val q = trimmed.substringAfter("search youtube for").trim()
            return RoutingResult.LocalCommand(deviceControlManager.searchWeb(q, "youtube"))
        }
        if (lower.startsWith("search google for ")) {
            val q = trimmed.substringAfter("search google for").trim()
            return RoutingResult.LocalCommand(deviceControlManager.searchWeb(q, "google"))
        }

        // 14. SETTINGS SHORTCUTS
        if (lower == "go to settings" || lower == "open settings") {
            return RoutingResult.LocalCommand(deviceControlManager.openSettingsScreen("settings"))
        }
        if (lower.contains("open wifi settings")) return RoutingResult.LocalCommand(deviceControlManager.openSettingsScreen("wifi"))
        if (lower.contains("open bluetooth settings")) return RoutingResult.LocalCommand(deviceControlManager.openSettingsScreen("bluetooth"))
        if (lower.contains("open battery settings")) return RoutingResult.LocalCommand(deviceControlManager.openSettingsScreen("battery"))
        if (lower.contains("open developer settings") || lower.contains("open developer options")) return RoutingResult.LocalCommand(deviceControlManager.openSettingsScreen("developer"))
        if (lower.contains("open accessibility settings")) return RoutingResult.LocalCommand(deviceControlManager.openSettingsScreen("accessibility"))

        // 15. CLIPBOARD
        if (lower.contains("read clipboard") || lower.contains("what is in my clipboard")) {
            return RoutingResult.LocalCommand("In your clipboard: " + deviceControlManager.readClipboard())
        }

        // 16. APP LAUNCH COMMANDS ("open whatsapp", "launch chrome", "i want whatsapp", "start camera")
        val appName = extractAppLaunchName(lower, trimmed)
        if (appName != null && appName.isNotEmpty()) {
            return RoutingResult.LocalCommand(deviceControlManager.launchApp(appName))
        }

        // Default: If no device command matches, route to Gemini Cloud AI
        return RoutingResult.SendToGemini
    }

    private fun isFlashlightOnCommand(text: String): Boolean {
        return text.contains("turn flashlight on") ||
                text.contains("turn on flashlight") ||
                text.contains("torch on") ||
                text.contains("switch on my torch") ||
                text.contains("turn on the flashlight") ||
                text == "flashlight on" ||
                text == "torch on"
    }

    private fun isFlashlightOffCommand(text: String): Boolean {
        return text.contains("turn flashlight off") ||
                text.contains("turn off flashlight") ||
                text.contains("torch off") ||
                text.contains("switch off my torch") ||
                text.contains("turn off the flashlight") ||
                text == "flashlight off" ||
                text == "torch off"
    }

    private fun extractAppLaunchName(lowerText: String, originalText: String): String? {
        val prefixes = listOf("open ", "launch ", "start ", "i want ", "go to ")
        for (prefix in prefixes) {
            if (lowerText.startsWith(prefix)) {
                val candidate = originalText.substring(prefix.length).trim()
                // Filter out non-app phrases like "open settings screen" or general chat
                if (candidate.isNotEmpty() && !candidate.contains("for ") && candidate.split(" ").size <= 4) {
                    return candidate
                }
            }
        }
        return null
    }
}
