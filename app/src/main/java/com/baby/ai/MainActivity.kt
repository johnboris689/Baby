package com.baby.ai

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private data class Message(val role: String, val text: String)

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var chat: LinearLayout
    private lateinit var input: EditText
    private lateinit var send: ImageButton
    private lateinit var mic: ImageButton
    private lateinit var status: TextView
    private lateinit var tts: TextToSpeech
    private var speech: SpeechRecognizer? = null
    private var busy = false
    private val messages = mutableListOf<Message>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs by lazy { getSharedPreferences("baby", MODE_PRIVATE) }

    private val bg = Color.rgb(9, 11, 16)
    private val panel = Color.rgb(17, 21, 30)
    private val panel2 = Color.rgb(23, 28, 38)
    private val white = Color.rgb(244, 247, 251)
    private val muted = Color.rgb(126, 135, 152)
    private val accent = Color.rgb(79, 140, 255)
    private val green = Color.rgb(67, 209, 122)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        tts = TextToSpeech(this, this)
        buildUi()
        addAssistant("Good morning, sir. I’m Baby. Your personal AI operator is online. Tell me what you need handled.")
        requestAudioPermission()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        root.addView(header(), LinearLayout.LayoutParams(-1, dp(78)))

        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), dp(12)) }
        body.addView(chips(), LinearLayout.LayoutParams(-1, dp(54)))

        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(bg) }
        chat = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), dp(8), dp(6), dp(16)) }
        scroll.addView(chat)
        body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        body.addView(composer(), LinearLayout.LayoutParams(-1, dp(72)))
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun header(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(12), dp(18), 0) }
        val icon = TextView(this).apply { text = "✦"; textSize = 25f; gravity = Gravity.CENTER; setTextColor(accent); setBackgroundColor(panel2) }
        box.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        val name = TextView(this).apply { text = "Baby"; textSize = 19f; setTextColor(white); setTypeface(typeface, Typeface.BOLD) }
        val sub = TextView(this).apply { text = "Personal AI operator"; textSize = 12f; setTextColor(muted) }
        status = TextView(this).apply { text = "Online · Ready"; textSize = 11f; setTextColor(green) }
        titles.addView(name); titles.addView(sub); titles.addView(status)
        box.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        val settings = Button(this).apply { text = "⚙"; textSize = 18f; setTextColor(muted); background = null; setOnClickListener { settingsDialog() } }
        box.addView(settings, LinearLayout.LayoutParams(dp(52), dp(52)))
        return box
    }

    private fun chips(): View {
        val h = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        listOf("Revenue", "Urgent mail", "Growth", "Routines", "Context").forEach { label ->
            val b = Button(this).apply {
                text = label; textSize = 11f; setTextColor(muted); background = null
                setOnClickListener { sendMessage("Show me my $label") }
            }
            row.addView(b, LinearLayout.LayoutParams(-2, dp(46)).apply { marginEnd = dp(4) })
        }
        h.addView(row); return h
    }

    private fun composer(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(6), dp(8), dp(6)); setBackgroundColor(panel) }
        input = EditText(this).apply { hint = "Tell Baby what to handle…"; setHintTextColor(Color.rgb(75, 83, 98)); setTextColor(white); textSize = 14f; background = null; maxLines = 4; setPadding(dp(10), 0, dp(6), 0) }
        row.addView(input, LinearLayout.LayoutParams(0, -1, 1f))
        mic = ImageButton(this).apply { contentDescription = "Voice input"; setImageResource(android.R.drawable.ic_btn_speak_now); setColorFilter(muted); setBackgroundColor(Color.TRANSPARENT); setOnClickListener { toggleSpeech() } }
        row.addView(mic, LinearLayout.LayoutParams(dp(48), dp(56)))
        send = ImageButton(this).apply { contentDescription = "Send"; setImageResource(android.R.drawable.ic_menu_send); setColorFilter(Color.WHITE); setBackgroundColor(accent); setOnClickListener { sendMessage(input.text.toString()) } }
        row.addView(send, LinearLayout.LayoutParams(dp(48), dp(56)))
        return row
    }

    private fun addAssistant(text: String) = addMessage(Message("assistant", text), true)
    private fun addUser(text: String) = addMessage(Message("user", text), false)

    private fun addMessage(message: Message, speak: Boolean) {
        messages.add(message)
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = if (message.role == "user") Gravity.END else Gravity.START; setPadding(dp(4), dp(6), dp(4), dp(6)) }
        val bubble = TextView(this).apply {
            text = message.text; textSize = 14f; setTextColor(if (message.role == "user") Color.WHITE else white); setPadding(dp(16), dp(12), dp(16), dp(12)); setLineSpacing(0f, 1.15f)
            setBackgroundColor(if (message.role == "user") accent else panel2)
        }
        val width = (resources.displayMetrics.widthPixels * 0.82).toInt()
        wrap.addView(bubble, LinearLayout.LayoutParams(width, -2))
        chat.addView(wrap)
        chat.post { (chat.parent as? ScrollView)?.fullScroll(View.FOCUS_DOWN) }
        if (speak && message.role == "assistant") speak(message.text)
    }

    private fun sendMessage(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || busy) return
        input.setText("")
        addUser(text)
        busy = true; send.isEnabled = false; status.text = "Baby is thinking…"
        scope.launch {
            val answer = withContext(Dispatchers.IO) { askBaby(text) }
            busy = false; send.isEnabled = true; status.text = "Online · Ready"
            addAssistant(answer)
        }
    }

    private fun askBaby(text: String): String {
        val key = prefs.getString("anthropic_key", "")?.trim().orEmpty()
        if (key.isEmpty()) return demoAnswer(text)
        return try {
            val endpoint = prefs.getString("anthropic_url", "https://api.anthropic.com/v1/messages")!!
            val model = prefs.getString("model", "claude-sonnet-4-5-20250929")!!
            val payload = JSONObject().apply {
                put("model", model); put("max_tokens", 1200)
                put("system", "You are Baby, a calm capable personal AI operator. Speak in a polished British assistant tone. Be concise and never claim you completed an external action unless you actually did it.")
                put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", text)))
            }
            val conn = URL(endpoint).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"; conn.doOutput = true; conn.connectTimeout = 20000; conn.readTimeout = 60000
            conn.setRequestProperty("content-type", "application/json")
            conn.setRequestProperty("x-api-key", key)
            conn.setRequestProperty("anthropic-version", "2023-06-01")
            conn.setRequestProperty("anthropic-dangerous-direct-browser-access", "true")
            conn.outputStream.use { it.write(payload.toString().toByteArray()) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val body = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            if (conn.responseCode !in 200..299) return "I couldn't reach Claude. ${conn.responseCode}: ${body.take(300)}"
            val json = JSONObject(body); val content = json.optJSONArray("content") ?: return "Claude returned no response."
            buildString { for (i in 0 until content.length()) { val b = content.getJSONObject(i); if (b.optString("type") == "text") append(b.optString("text")) } }.ifBlank { "I received an empty response." }
        } catch (e: Exception) { "The AI service is unavailable right now. ${e.message ?: "Please check your connection and API settings."}" }
    }

    private fun demoAnswer(text: String): String {
        val t = text.lowercase(Locale.ROOT)
        return when {
            "revenue" in t -> "Your local Baby dashboard is in demo mode. Connect Claude and your RevenueCat credentials in Settings to retrieve live revenue and subscription metrics."
            "mail" in t || "email" in t -> "I can handle customer-mail workflows once a Gmail connection is configured. In demo mode I will not pretend I accessed your mailbox."
            "campaign" in t || "growth" in t -> "I can prepare a paused growth campaign. Connect your Meta credentials before requesting a live campaign operation."
            "routine" in t -> "You can create recurring routines from the Routines action. The Android build keeps routine definitions locally on the device."
            else -> "I’m Baby. I’m running locally in demo mode because no Claude API key is configured. Open Settings and add your Anthropic API key to enable live AI conversations."
        }
    }

    private fun settingsDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(4), dp(24), 0) }
        val key = EditText(this).apply { hint = "Anthropic API key"; inputType = 0x00000081; setText(prefs.getString("anthropic_key", "")) }
        val model = EditText(this).apply { hint = "Model ID"; setText(prefs.getString("model", "claude-sonnet-4-5-20250929")) }
        val url = EditText(this).apply { hint = "Anthropic API URL"; setText(prefs.getString("anthropic_url", "https://api.anthropic.com/v1/messages")) }
        box.addView(key); box.addView(model); box.addView(url)
        AlertDialog.Builder(this).setTitle("Baby settings").setView(box).setMessage("Your key is stored locally on this device. Leave it blank for demo mode.")
            .setNegativeButton("Clear") { _, _ -> prefs.edit().clear().apply() }
            .setPositiveButton("Save") { _, _ -> prefs.edit().putString("anthropic_key", key.text.toString()).putString("model", model.text.toString()).putString("anthropic_url", url.text.toString()).apply(); status.text = "Online · Ready" }
            .show()
    }

    private fun toggleSpeech() {
        if (speech != null) { speech?.stopListening(); speech?.destroy(); speech = null; mic.setColorFilter(muted); return }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { Toast.makeText(this, "Speech recognition is unavailable on this device.", Toast.LENGTH_SHORT).show(); return }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this); speech = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { mic.setColorFilter(Color.RED) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { mic.setColorFilter(muted) }
            override fun onError(error: Int) { mic.setColorFilter(muted); speech?.destroy(); speech = null }
            override fun onResults(results: Bundle?) { val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); speech?.destroy(); speech = null; mic.setColorFilter(muted); if (!list.isNullOrEmpty()) sendMessage(list[0]) }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB"); putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) }
        recognizer.startListening(intent)
    }

    private fun requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
    }

    override fun onInit(statusCode: Int) { if (statusCode == TextToSpeech.SUCCESS) { tts.language = Locale.UK; tts.setSpeechRate(0.96f); tts.setPitch(0.92f) } }
    private fun speak(text: String) {
        if (::tts.isInitialized) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "baby-${System.currentTimeMillis()}")
        }
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onDestroy() { scope.cancel(); speech?.destroy(); if (::tts.isInitialized) tts.shutdown(); super.onDestroy() }
}
