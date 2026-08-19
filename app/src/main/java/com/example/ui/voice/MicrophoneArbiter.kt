package com.example.ui.voice

/**
 * Process-wide microphone ownership guard. Android's audio stack is prone to
 * native failures when AudioRecord and SpeechRecognizer are started at the
 * same time. Only one Baby voice component may own the microphone at once.
 */
object MicrophoneArbiter {
    private val lock = Any()
    private var owner: String? = null

    fun tryAcquire(requester: String): Boolean = synchronized(lock) {
        if (owner == null || owner == requester) {
            owner = requester
            true
        } else {
            false
        }
    }

    fun release(requester: String) = synchronized(lock) {
        if (owner == requester) owner = null
    }

    fun isOwnedBy(requester: String): Boolean = synchronized(lock) { owner == requester }
}
