package com.example.duudle

const val SIGN_IN_TIMEOUT_MS = 20_000L
const val SIGN_IN_TIMEOUT_MESSAGE = "Couldn't connect. Check your internet (try Wi-Fi) and try again."

/**
 * Sign-up attempts on the Welcome screen. Firebase can wait a long time without replying
 * (seen on a mobile network with broken IPv6), so the screen gives up after
 * [SIGN_IN_TIMEOUT_MS]. A reply that still arrives later is handled once: a late success
 * still signs the user in (the profile really was saved), a late failure is ignored, and a
 * reply from an older attempt never overrides a newer one.
 */
class SignInAttempts {
    private var current = 0
    private var timedOut = false
    private var finished = false

    /** Starts a new attempt and returns its id. */
    fun start(): Int {
        current++
        timedOut = false
        finished = false
        return current
    }

    /** True if the timeout should be shown for attempt [id] (current, still waiting, no reply yet). */
    fun timeOut(id: Int): Boolean {
        if (id != current || timedOut || finished) return false
        timedOut = true
        return true
    }

    /**
     * True if Firebase's reply for attempt [id] should be acted on; the attempt is then
     * finished, so its timeout can no longer overwrite the real result.
     */
    fun accept(id: Int, success: Boolean): Boolean {
        if (id != current || finished || (timedOut && !success)) return false
        finished = true
        return true
    }
}
