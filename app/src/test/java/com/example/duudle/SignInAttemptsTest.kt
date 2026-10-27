package com.example.duudle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInAttemptsTest {
    @Test
    fun aReplyBeforeTheTimeoutIsHandledOnceAndTheTimeoutIsSkipped() {
        val a = SignInAttempts(); val id = a.start()
        assertTrue(a.accept(id, success = false))   // e.g. "That username is taken"
        assertFalse(a.timeOut(id))                  // must not overwrite that message
        assertFalse(a.accept(id, success = true))   // handled once
    }

    @Test
    fun theTimeoutFiresOnceForTheCurrentAttempt() {
        val a = SignInAttempts(); val id = a.start()
        assertTrue(a.timeOut(id))
        assertFalse(a.timeOut(id))
    }

    @Test
    fun aLateSuccessStillSignsInButALateFailureIsIgnored() {
        val a = SignInAttempts(); val id = a.start()
        a.timeOut(id)
        assertTrue(a.accept(id, success = true))
        assertFalse(a.accept(id, success = false))
    }

    @Test
    fun repliesAndTimeoutsFromAnOlderAttemptAreIgnored() {
        val a = SignInAttempts(); val old = a.start(); a.timeOut(old); val new = a.start()
        assertFalse(a.timeOut(old))
        assertFalse(a.accept(old, success = true))
        assertTrue(a.accept(new, success = false))
    }
}
