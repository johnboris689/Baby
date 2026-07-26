package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

class PackageCacheManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("baby_package_cache", Context.MODE_PRIVATE)

    companion object {
        val WELL_KNOWN_PACKAGES = mapOf(
            "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "wa" to listOf("com.whatsapp"),
            "instagram" to listOf("com.instagram.android"),
            "ig" to listOf("com.instagram.android"),
            "tiktok" to listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
            "tk" to listOf("com.zhiliaoapp.musically"),
            "youtube" to listOf("com.google.android.youtube"),
            "yt" to listOf("com.google.android.youtube"),
            "facebook" to listOf("com.facebook.katana"),
            "fb" to listOf("com.facebook.katana"),
            "messenger" to listOf("com.facebook.orca"),
            "telegram" to listOf("org.telegram.messenger"),
            "tg" to listOf("org.telegram.messenger"),
            "spotify" to listOf("com.spotify.music"),
            "chrome" to listOf("com.android.chrome"),
            "browser" to listOf("com.android.chrome", "org.mozilla.firefox"),
            "gmail" to listOf("com.google.android.gm"),
            "email" to listOf("com.google.android.gm"),
            "maps" to listOf("com.google.android.apps.maps"),
            "google maps" to listOf("com.google.android.apps.maps"),
            "play store" to listOf("com.android.vending"),
            "google play" to listOf("com.android.vending"),
            "store" to listOf("com.android.vending"),
            "twitter" to listOf("com.twitter.android"),
            "x" to listOf("com.twitter.android"),
            "snapchat" to listOf("com.snapchat.android"),
            "netflix" to listOf("com.netflix.mediaclient"),
            "calculator" to listOf("com.google.android.calculator", "com.sec.android.app.popupcalculator", "com.android.calculator2"),
            "calc" to listOf("com.google.android.calculator", "com.sec.android.app.popupcalculator"),
            "camera" to listOf("com.google.android.GoogleCamera", "com.sec.android.app.camera", "com.android.camera2", "com.android.camera"),
            "gallery" to listOf("com.google.android.apps.photos", "com.sec.android.gallery3d"),
            "photos" to listOf("com.google.android.apps.photos"),
            "clock" to listOf("com.google.android.deskclock", "com.sec.android.app.clockpackage"),
            "alarm" to listOf("com.google.android.deskclock", "com.sec.android.app.clockpackage"),
            "contacts" to listOf("com.google.android.contacts", "com.samsung.android.app.contacts"),
            "phone" to listOf("com.google.android.dialer", "com.samsung.android.dialer", "com.android.dialer"),
            "dialer" to listOf("com.google.android.dialer", "com.samsung.android.dialer"),
            "files" to listOf("com.google.android.documentsui", "com.sec.android.app.myfiles"),
            "file manager" to listOf("com.google.android.documentsui", "com.sec.android.app.myfiles"),
            "settings" to listOf("com.android.settings"),
            "messages" to listOf("com.google.android.apps.messaging", "com.samsung.android.messaging"),
            "sms" to listOf("com.google.android.apps.messaging", "com.samsung.android.messaging")
        )
    }

    fun getCachedPackage(query: String): String? {
        val cleanKey = query.trim().lowercase(Locale.ROOT)
        // 1. Check user-learned/saved cache
        val saved = prefs.getString(cleanKey, null)
        if (!saved.isNullOrEmpty()) return saved

        // 2. Check built-in well-known mappings
        val list = WELL_KNOWN_PACKAGES[cleanKey]
        return list?.firstOrNull()
    }

    fun saveLearnedPackage(query: String, packageName: String) {
        val cleanKey = query.trim().lowercase(Locale.ROOT)
        if (cleanKey.isNotEmpty() && packageName.isNotEmpty()) {
            prefs.edit().putString(cleanKey, packageName).apply()
        }
    }
}
