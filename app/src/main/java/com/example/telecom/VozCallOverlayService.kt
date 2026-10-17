package com.example.telecom

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.ui.components.DynamicIslandCallPill

/**
 * System-wide floating overlay service providing the Dynamic Island call pill
 * on top of other applications when permission (SYSTEM_ALERT_WINDOW) is granted.
 */
class VozCallOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var overlayComposeView: ComposeView? = null

    companion object {
        private const val TAG = "VOZ_OVERLAY"

        fun startIfPermitted(context: Context) {
            if (Settings.canDrawOverlays(context)) {
                try {
                    val intent = Intent(context, VozCallOverlayService::class.java)
                    context.startService(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to start overlay service", e)
                }
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, VozCallOverlayService::class.java)
                context.stopService(intent)
            } catch (_: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        setupOverlayView()
    }

    private fun setupOverlayView() {
        if (overlayComposeView != null) return

        val wm = windowManager ?: return

        val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutParamsType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 8
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@VozCallOverlayService)
            setViewTreeSavedStateRegistryOwner(this@VozCallOverlayService)

            setContent {
                val activeCalls by CallSessionController.activeCalls.collectAsState()
                val ringingCall = activeCalls.firstOrNull { it.state == CallLifecycleState.RINGING }
                val currentCall = ringingCall ?: activeCalls.firstOrNull()

                if (currentCall != null) {
                    DynamicIslandCallPill(
                        call = currentCall,
                        onOpenFullScreen = {
                            val intent = Intent(this@VozCallOverlayService, InCallActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            try {
                                startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    )
                } else {
                    // Automatically dismiss overlay when call ends
                    removeOverlayView()
                    stopSelf()
                }
            }
        }

        try {
            wm.addView(composeView, params)
            overlayComposeView = composeView
            Log.i(TAG, "Dynamic Island overlay attached successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach overlay window", e)
            stopSelf()
        }
    }

    private fun removeOverlayView() {
        overlayComposeView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {}
        }
        overlayComposeView = null
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        removeOverlayView()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
