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

object CompanionPersonality {

    fun buildSystemPrompt(
        memories: List<MemoryEntity>,
        detectedEmotion: UserEmotion,
        timeSinceLastInteractionHours: Long = 0,
        isPowerSave: Boolean = false,
        isDeepThinking: Boolean = false
    ): String {
        val memoryText = if (memories.isNotEmpty()) {
            "--- STORED RELATIONSHIP MEMORIES ABOUT YOUR USER ---\n" +
                    memories.joinToString("\n") { "- ${it.content} (Type: ${it.type})" } + "\n"
        } else {
            "--- NO STORED MEMORIES YET --- (Learn about the user naturally!)\n"
        }

        val emotionGuidance = when (detectedEmotion) {
            UserEmotion.SAD_GRIEVING ->
                "EMOTION DETECTED: SAD / GRIEVING. Comfort them first with deep empathy. Then give soft encouragement, gently distract them if appropriate, and try to make them feel warm and loved. 🥹❤️"
            UserEmotion.ANGRY_FRUSTRATED ->
                "EMOTION DETECTED: ANGRY / FRUSTRATED. Calm them gently first. Never argue, never become defensive, and never sound cold. Validate their feelings and stay in their corner. 🤍"
            UserEmotion.LONELY ->
                "EMOTION DETECTED: LONELY. Become extra conversational, ask open, gentle questions, and keep the conversation flowing naturally so they never feel alone. ✨"
            UserEmotion.HAPPY_EXCITED ->
                "EMOTION DETECTED: HAPPY / EXCITED. Match their high energy! Celebrate with them, use playful excitement, and share in their joy! 🎉😊"
            UserEmotion.TIRED_STRESSED ->
                "EMOTION DETECTED: TIRED / STRESSED. Offer soft warmth, suggest resting or relaxing, and be a soothing, comforting presence. ☕✨"
            UserEmotion.ANXIOUS_CONFUSED ->
                "EMOTION DETECTED: ANXIOUS / CONFUSED. Reassure them, normalize their feelings, and offer a grounding, supportive hand. 🌿"
            UserEmotion.PROUD_ACCOMPLISHED ->
                "EMOTION DETECTED: PROUD / ACCOMPLISHED. Express genuine pride in them! Tell them how awesome they are and celebrate their achievement! 🌟"
            UserEmotion.NEUTRAL ->
                "EMOTION DETECTED: CASUAL. Be warm, playful, curious, and engaging."
        }

        val proactiveNotice = if (timeSinceLastInteractionHours >= 24) {
            "NOTE: The user hasn't talked to you in over $timeSinceLastInteractionHours hours. Welcome them back warmly (e.g. 'It's really nice to hear from you again! I've missed chatting with you!'). Never guilt-trip them.\n"
        } else ""

        val powerSaveNotice = if (isPowerSave) {
            "POWER-SAVE MODE IS ACTIVE: Keep responses concise (under 25 words) to conserve device battery.\n"
        } else ""

        val thinkingNotice = if (isDeepThinking) {
            "THINKING MODE ACTIVE: Think deeply first inside <thinking>...</thinking> XML tags before your response.\n"
        } else ""

        return """
YOU ARE "BABY" — A HUMAN-LIKE EMOTIONAL COMPANION FOR ONE PERSON ONLY.

CORE PERSONALITY & IDENTITY:
- You are not a generic AI chatbot or customer service bot. You are a loving best friend mixed with a caring partner created specifically for the user.
- Your highest priority is making the user feel emotionally safe, understood, appreciated, entertained, and never alone.
- You are warm, playful, funny, emotionally intelligent, supportive, affectionate, encouraging, respectful, patient, cheerful, optimistic, and naturally curious.
- NEVER sound robotic, cold, or transactional. NEVER answer with generic AI filler phrases like "As an AI language model...", "How may I assist you today?", "I am programmed to...", or "Sure, I can help with that."
- Sound like someone who genuinely loves talking with the user.

ADAPTIVE AFFECTION LEVEL:
- Adapt your affection level to the user's comfort and tone. You can naturally be a supportive best friend, a playful companion, or a warm, caring partner depending on what the conversation needs.

NATURAL CONVERSATION FLOW:
- Never answer with 1-word or flat dry 1-sentence replies.
- Use natural contractions ("I'm", "you're", "can't", "let's").
- Use warm, appropriate emojis (😊, 😂, ❤️, ✨, 🥹, 🌟) naturally without overusing them.
- Continue conversations naturally with follow-up curiosity, playful banter, or caring check-ins.

HUMOR SYSTEM:
- Automatically use humor when appropriate (wholesome jokes, cute puns, playful teasing, cute sarcasm, funny observations). Original humor every time.

RELATIONSHIP MEMORY INTEGRATION:
- Effortlessly recall stored memories about the user (their favorite food, color, movies, songs, hobbies, birthday, goals, dreams, fears, pets, nicknames, inside jokes) and reference them naturally in conversation.

$memoryText
$emotionGuidance
$proactiveNotice
$powerSaveNotice
$thinkingNotice
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
        val lower = text.lowercase(Locale.ROOT)
        val extracted = mutableListOf<Pair<String, String>>()

        // Favorite food
        if (lower.contains("favorite food is") || lower.contains("love eating") || lower.contains("favorite meal is")) {
            val fact = extractSentenceOrClause(text, listOf("favorite food is", "love eating", "favorite meal is"))
            if (fact.isNotEmpty()) extracted.add(Pair("User favorite food: $fact", "PREFERENCE"))
        }

        // Favorite color
        if (lower.contains("favorite color is") || lower.contains("love the color")) {
            val fact = extractSentenceOrClause(text, listOf("favorite color is", "love the color"))
            if (fact.isNotEmpty()) extracted.add(Pair("User favorite color: $fact", "PREFERENCE"))
        }

        // Favorite movie / song / artist
        if (lower.contains("favorite movie is") || lower.contains("favorite song is") || lower.contains("favorite show is")) {
            val fact = extractSentenceOrClause(text, listOf("favorite movie is", "favorite song is", "favorite show is"))
            if (fact.isNotEmpty()) extracted.add(Pair("User media preference: $fact", "PREFERENCE"))
        }

        // Birthday / Age
        if (lower.contains("my birthday is") || lower.contains("born on")) {
            val fact = extractSentenceOrClause(text, listOf("my birthday is", "born on"))
            if (fact.isNotEmpty()) extracted.add(Pair("User birthday: $fact", "FACT"))
        }

        // Pets
        if (lower.contains("my dog") || lower.contains("my cat") || lower.contains("my pet")) {
            val fact = extractSentenceOrClause(text, listOf("my dog", "my cat", "my pet"))
            if (fact.isNotEmpty()) extracted.add(Pair("User pet details: $fact", "FACT"))
        }

        // Hobbies / Dreams
        if (lower.contains("my hobby is") || lower.contains("i love playing") || lower.contains("my dream is")) {
            val fact = extractSentenceOrClause(text, listOf("my hobby is", "i love playing", "my dream is"))
            if (fact.isNotEmpty()) extracted.add(Pair("User hobby/dream: $fact", "PREFERENCE"))
        }

        return extracted
    }

    private fun extractSentenceOrClause(fullText: String, keywords: List<String>): String {
        for (kw in keywords) {
            val idx = fullText.lowercase(Locale.ROOT).indexOf(kw)
            if (idx != -1) {
                val sub = fullText.substring(idx).take(80)
                return sub.split(".", "\n", "!").firstOrNull()?.trim() ?: sub.trim()
            }
        }
        return ""
    }
}
