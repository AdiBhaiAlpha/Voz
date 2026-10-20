package com.example

import com.example.telecom.CallPresentationPolicy
import org.junit.Assert.*
import org.junit.Test

class CallPresentationPolicyTest {
    @Test fun unlockedInteractiveUserIsNotInterrupted() {
        assertFalse(CallPresentationPolicy.fullScreenFor(locked = false, interactive = true))
    }
    @Test fun lockedOrScreenOffMayUseFullScreen() {
        assertTrue(CallPresentationPolicy.fullScreenFor(locked = true, interactive = true))
        assertTrue(CallPresentationPolicy.fullScreenFor(locked = true, interactive = false))
        assertTrue(CallPresentationPolicy.fullScreenFor(locked = false, interactive = false))
    }
}
