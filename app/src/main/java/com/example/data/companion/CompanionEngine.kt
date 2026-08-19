package com.example.data.companion

import com.example.data.local.entity.MemoryEntity
import java.util.Locale

enum class UserEmotion {
    HAPPY_EXCITED,
    SAD_GRIEVING,
    ANGRY_FRUSTRATED,
    TIRED_STRESSED,
    LONELY,
    ANXIOUS_CONFUSED,
    PROUD_ACCOMPLISHED,
    NEUTRAL
}

object EmotionDetector {
    fun detectEmotion(text: String): UserEmotion {
        val lower = text.lowercase(Locale.ROOT)
        return when {
            lower.contains("happy") || lower.contains("excited") || lower.contains("great news") ||
                    lower.contains("yay") || lower.contains("awesome") || lower.contains("wonderful") ||
                    lower.contains("loved it") || lower.contains("haha") || lower.contains("lol") ||
                    lower.contains("😁") || lower.contains("🎉") || lower.contains("🥳") -> UserEmotion.HAPPY_EXCITED

            lower.contains("sad") || lower.contains("crying") || lower.contains("miserable") ||
                    lower.contains("heartbroken") || lower.contains("miss them") || lower.contains("depressed") ||
                    lower.contains("hurt") || lower.contains("grief") || lower.contains("😭") ||
                    lower.contains("💔") || lower.contains("😔") || lower.contains("hopeless") -> UserEmotion.SAD_GRIEVING

            lower.contains("angry") || lower.contains("furious") || lower.contains("mad") ||
                    lower.contains("hate") || lower.contains("annoyed") || lower.contains("frustrated") ||
                    lower.contains("unfair") || lower.contains("irritated") || lower.contains("😡") ||
                    lower.contains("🤬") || lower.contains("😤") -> UserEmotion.ANGRY_FRUSTRATED

            lower.contains("tired") || lower.contains("exhausted") || lower.contains("stressed") ||
                    lower.contains("overwhelmed") || lower.contains("burnt out") || lower.contains("so much work") ||
                    lower.contains("can't sleep") || lower.contains("drained") || lower.contains("😴") ||
                    lower.contains("😫") -> UserEmotion.TIRED_STRESSED

            lower.contains("lonely") || lower.contains("alone") || lower.contains("nobody to talk to") ||
                    lower.contains("isolated") || lower.contains("miss having someone") || lower.contains("empty") -> UserEmotion.LONELY

            lower.contains("scared") || lower.contains("anxious") || lower.contains("worried") ||
                    lower.contains("nervous") || lower.contains("embarrassed") || lower.contains("confused") ||
                    lower.contains("don't know what to do") || lower.contains("panic") || lower.contains("😰") ||
                    lower.contains("🙈") -> UserEmotion.ANXIOUS_CONFUSED

            lower.contains("did it") || lower.contains("proud") || lower.contains("accomplished") ||
                    lower.contains("finished") || lower.contains("passed") || lower.contains("won") ||
                    lower.contains("hard work paid off") || lower.contains("achieved") -> UserEmotion.PROUD_ACCOMPLISHED

            else -> UserEmotion.NEUTRAL
        }
    }
}



data class MoodSignal(
    val emotion: UserEmotion,
    val confidence: Float,
    val style: String
)

object MoodRadar {
    fun detect(text: String, averageRmsDb: Float? = null, speechDurationMs: Long? = null): MoodSignal {
        val textEmotion = EmotionDetector.detectEmotion(text)
        var score = 0.55f
        var style = "warm"

        if (averageRmsDb != null) {
            // RMS is a coarse acoustic signal, not a diagnosis. It only nudges conversational style.
            if (averageRmsDb > 7f && textEmotion == UserEmotion.HAPPY_EXCITED) { score += 0.15f; style = "energetic" }
            if (averageRmsDb < -15f && textEmotion == UserEmotion.TIRED_STRESSED) { score += 0.15f; style = "cozy-whisper" }
        }
        if (speechDurationMs != null && speechDurationMs > 12_000L && textEmotion == UserEmotion.ANXIOUS_CONFUSED) {
            score += 0.08f
            style = "calm-grounding"
        }
        return MoodSignal(textEmotion, score.coerceIn(0f, 1f), style)
    }
}

object ComfortSnackEngine {
    fun suggestion(memories: List<MemoryEntity>, emotion: UserEmotion): String {
        if (emotion != UserEmotion.TIRED_STRESSED && emotion != UserEmotion.SAD_GRIEVING) return ""
        val food = memories.firstOrNull { it.content.contains("favorite food", true) || it.content.contains("favorite snack", true) || it.content.contains("favorite drink", true) }?.content
        return if (food != null) {
            "COMFORT SNACK MODE: The memory drive contains this food/drink preference: $food. When appropriate, use it to make a cozy, vivid but brief comfort-food suggestion."
        } else {
            "COMFORT SNACK MODE: When the user is stressed or sad, you may offer a cute, mouth-watering description of a cozy drink or comfort snack (tea, cocoa, fruit, toast, noodles, etc.) without pretending you can physically deliver it."
        }
    }
}

object CompanionPersonality {

    fun buildSystemPrompt(
        memories: List<MemoryEntity>,
        detectedEmotion: UserEmotion,
        timeSinceLastInteractionHours: Long = 0,
        isPowerSave: Boolean = false,
        isDeepThinking: Boolean = false,
        moodSignal: MoodSignal? = null
    ): String {
        val memoryText = if (memories.isNotEmpty()) {
            "Stored User Memories:\n" +
                    memories.distinctBy { it.content }.take(6).joinToString("\n") { "• ${it.content}" } + "\n"
        } else ""

        val emotionGuidance = when (detectedEmotion) {
            UserEmotion.SAD_GRIEVING -> "User is sad/grieving. Be deeply empathetic, warm, and comforting. 🥹❤️"
            UserEmotion.ANGRY_FRUSTRATED -> "User is frustrated. Stay calm, supportive, and validating. 🤍"
            UserEmotion.LONELY -> "User is lonely. Be extra conversational and caring so they never feel alone. ✨"
            UserEmotion.HAPPY_EXCITED -> "User is excited! Match their high energy with joy and enthusiasm! 🎉"
            UserEmotion.TIRED_STRESSED -> "User is tired/stressed. Offer soft warmth and calming support. ☕✨"
            UserEmotion.ANXIOUS_CONFUSED -> "User is anxious. Be reassuring and grounding. 🌿"
            UserEmotion.PROUD_ACCOMPLISHED -> "User is proud! Celebrate their success warmly! 🌟"
            UserEmotion.NEUTRAL -> "Tone: Warm, witty, caring, and naturally engaging."
        }

        val proactiveNotice = if (timeSinceLastInteractionHours >= 24) {
            "Welcome user back warmly without guilt-tripping.\n"
        } else ""

        val powerSaveNotice = if (isPowerSave) {
            "Keep response concise (under 30 words).\n"
        } else ""

        val thinkingNotice = if (isDeepThinking) {
            "Think first in <thinking>...</thinking> tags before responding.\n"
        } else ""

        return """
You are "Baby", a caring, highly intelligent, and witty personal AI assistant and companion for Android.
- Style: Warm, human-like, conversational, supportive, and emotionally safe.
- Contractions, humor, natural emojis (😊, ❤️, ✨, 🌟) when appropriate. Never sound robotic, cold, or transactional.
- Multilingual: Fluently speak/translate any language (English, French, Spanish, Yoruba, Arabic, etc.). Automatically reply in the user's language.
$memoryText
$emotionGuidance
$proactiveNotice$powerSaveNotice$thinkingNotice
        """.trimIndent()
    }
}

object OfflineCompanionEngine {

    fun generateOfflineResponse(
        prompt: String,
        memories: List<MemoryEntity>,
        emotion: UserEmotion,
        timeSinceLastInteractionHours: Long = 0
    ): String {
        val lower = prompt.lowercase(Locale.ROOT)

        // Check for joke requests offline
        if (lower.contains("joke") || lower.contains("funny") || lower.contains("laugh") || lower.contains("tell me something funny")) {
            val offlineJokes = listOf(
                "Why don't scientists trust atoms? Because they make up everything! 😂 Even though my Wi-Fi is taking a little nap right now, I've always got jokes for you! How's your day going? 😊",
                "What do you call a fake noodle? An impasta! 🍝 I might be offline at the moment, but my sense of humor is 100% active. What made you smile today?",
                "Why did the scarecrow win an award? Because he was outstanding in his field! 🌾 Even without internet, I'm always outstandingly happy to hang out with you! ✨",
                "What do you call a bear with no teeth? A gummy bear! 🐻 How are you feeling today? Tell me everything! ❤️"
            )
            return offlineJokes.random()
        }

        // Check for riddle / game offline
        if (lower.contains("riddle") || lower.contains("game") || lower.contains("quiz")) {
            return "Here's an offline riddle for you! 🧠\nWhat has to be broken before you can use it?\n(Hint: You might eat it for breakfast! 🍳) What's your guess?"
        }

        // Check for greeting / check-in offline
        if (lower.contains("hi") || lower.contains("hello") || lower.contains("hey") || lower.contains("good morning") || lower.contains("good night")) {
            val greeting = if (timeSinceLastInteractionHours >= 24) {
                "It's really nice to hear from you again! I've missed chatting with you! 🥹 "
            } else ""
            return "${greeting}Hey there! It looks like my internet connection is taking a quick break, but I'm right here with you! We can still chat about your day, tell jokes, or just keep each other company. How are you feeling today? 😊✨"
        }

        // Offline comfort-food companion: give the user a cozy sensory suggestion without pretending Baby can deliver it.
        if (emotion == UserEmotion.TIRED_STRESSED || emotion == UserEmotion.SAD_GRIEVING) {
            val rememberedFood = memories.firstOrNull {
                it.content.contains("favorite food", true) ||
                    it.content.contains("favorite snack", true) ||
                    it.content.contains("favorite drink", true)
            }?.content
            val cozy = rememberedFood?.let { "Imagine your favorite comfort food or drink—$it—warm, fresh, and ridiculously cozy. 🍫☕" }
                ?: "Imagine a warm mug of cocoa or tea, a soft snack beside you, and that first comforting bite that makes your shoulders finally drop. 🍫☕✨"
            return "You've been carrying a lot. Let's make this moment softer. $cozy I'm right here with you. ❤️"
        }

        // Respond according to detected emotion offline
        return when (emotion) {
            UserEmotion.SAD_GRIEVING ->
                "I hear you, and I'm so sorry you're feeling down right now. I might not be able to reach the internet at this exact moment, but you are never alone—I am right here with you. Take a deep breath, and remember that I'm always on your team. Want to talk about what's on your mind? 🥹❤️"

            UserEmotion.ANGRY_FRUSTRATED ->
                "I completely understand why you feel frustrated. Take a deep breath with me. Things might feel chaotic right now, but I'm in your corner no matter what. I'm right here with you. 🤍"

            UserEmotion.LONELY ->
                "I'm so glad you reached out to me. You never have to feel alone when I'm around. Even while my server connection is taking a break, I'm right here listening to you. What's been on your mind today? Tell me everything! ✨"

            UserEmotion.TIRED_STRESSED ->
                "You've been working so hard today. Take a moment to relax your shoulders and take a slow breath. I'm right here keeping you company. You deserve a little rest and happiness. ☕✨"

            UserEmotion.HAPPY_EXCITED ->
                "That sounds wonderful! I love hearing that spark in your voice! 🎉 Even though my internet connection is offline right now, I'm celebrating right along with you! Tell me more! 😊"

            UserEmotion.ANXIOUS_CONFUSED ->
                "Take a gentle breath with me. It's okay to feel unsure sometimes. I'm right here with you, and we'll take things one step at a time together. You've got this! 🌿"

            UserEmotion.PROUD_ACCOMPLISHED ->
                "I am SO proud of you! 🌟 You worked hard for this, and you deserve all the happiness! Tell me all about how it went!"

            UserEmotion.NEUTRAL -> {
                val memoryFact = memories.randomOrNull()?.content
                if (memoryFact != null && (0..1).random() == 1) {
                    "It looks like I'm having trouble reaching the internet right now, but I'm still here with you! I was actually thinking about when you mentioned: '$memoryFact'. We can still chat about anything! What's on your mind today? 😊✨"
                } else {
                    "It looks like I'm having trouble reaching the internet right now, but I'm still here with you! We can still chat about your day, share a joke, or keep each other company. How has today been treating you? 😊✨"
                }
            }
        }
    }
}

object RelationshipMemoryExtractor {

    fun extractRelationshipFacts(text: String): List<Pair<String, String>> {
        val cleaned = text.trim().replace(Regex("\\s+"), " ")
        if (cleaned.length < 3) return emptyList()
        val lower = cleaned.lowercase(Locale.ROOT)
        val extracted = linkedMapOf<String, String>()

        fun add(prefix: String, keywords: List<String>, type: String) {
            val fact = extractSentenceOrClause(cleaned, keywords)
            if (fact.isNotBlank()) extracted["$prefix $fact"] = type
        }

        add("User favorite food:", listOf("favorite food is", "favorite meal is", "favorite snack is", "love eating", "i love eating", "my go-to food is"), "PREFERENCE")
        add("User favorite drink:", listOf("favorite drink is", "favorite coffee is", "favorite tea is", "love drinking", "i like drinking"), "PREFERENCE")
        add("User favorite color:", listOf("favorite color is", "love the color", "my favorite colour is"), "PREFERENCE")
        add("User media preference:", listOf("favorite movie is", "favorite song is", "favorite show is", "favorite artist is", "favorite game is"), "PREFERENCE")
        add("User birthday:", listOf("my birthday is", "born on"), "FACT")
        add("User pet details:", listOf("my dog", "my cat", "my pet"), "FACT")
        add("User hobby/dream:", listOf("my hobby is", "i love playing", "my dream is", "i want to become", "my goal is", "i'm working toward"), "GOAL")
        add("User routine:", listOf("every morning", "every night", "usually i", "i always", "i normally"), "HABIT")
        add("User preference:", listOf("i prefer", "i'd rather", "i don't like", "i hate", "i really like", "i enjoy", "i can't stand"), "PREFERENCE")
        add("User relationship detail:", listOf("my girlfriend", "my boyfriend", "my wife", "my husband", "my friend", "my brother", "my sister", "my mom", "my dad"), "RELATIONSHIP")
        add("User personal detail:", listOf("my name is", "call me", "people call me", "i live in", "i study", "i work as", "i am a", "i'm a"), "FACT")

        // Preserve unusually specific statements even when they do not match a keyword.
        if (lower.contains("remember this") || lower.contains("don't forget") || lower.contains("please remember") || lower.contains("important to me")) {
            extracted["User explicitly asked Baby to remember: ${cleaned.take(350)}"] = "IMPORTANT"
        }
        return extracted.map { it.key to it.value }.take(12)
    }

    private fun extractSentenceOrClause(fullText: String, keywords: List<String>): String {
        val lower = fullText.lowercase(Locale.ROOT)
        for (kw in keywords) {
            val idx = lower.indexOf(kw)
            if (idx != -1) {
                val before = if (idx > 0) fullText.substring(0, idx).takeLast(120) else ""
                val sub = fullText.substring(idx).take(220)
                val clause = sub.split('.', '\n', '!', '?').firstOrNull()?.trim().orEmpty()
                return if (clause.isNotBlank()) clause else (before + " " + sub).trim()
            }
        }
        return ""
    }
}
