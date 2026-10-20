package com.example.telecom

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager

/** Never steal focus from an unlocked interactive app. No usage-history permission required. */
object CallPresentationPolicy {
    var mainVisible = false

    private val visibleOwners = mutableSetOf<Int>()
    val fullScreenVisible: Boolean get() = visibleOwners.isNotEmpty()

    fun setFullScreenVisible(visible: Boolean, owner: Any) {
        val id = System.identityHashCode(owner)
        if (visible) visibleOwners.add(id) else visibleOwners.remove(id)
    }

    fun fullScreenFor(locked: Boolean, interactive: Boolean): Boolean = locked || !interactive

    fun shouldUseFullScreen(context: Context): Boolean {
        val locked = (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isKeyguardLocked == true
        val interactive = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive == true
        return fullScreenFor(locked, interactive)
    }
}
