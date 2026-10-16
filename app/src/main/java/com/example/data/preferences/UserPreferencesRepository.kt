package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class VozUserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val languageCode: String = "en", // "en" or "bn"
    val hapticFeedbackEnabled: Boolean = true,
    val dialpadSoundEnabled: Boolean = true,
    val preferredSimId: String = "",
    val oneHandedModeEnabled: Boolean = false,
    val reduceMotionEnabled: Boolean = false,
    val confirmBeforeCallEnabled: Boolean = false
)

class UserPreferencesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("voz_user_preferences", Context.MODE_PRIVATE)

    val preferencesFlow: Flow<VozUserPreferences> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(loadPreferences())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(loadPreferences())
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    fun loadPreferences(): VozUserPreferences {
        val themeStr = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val theme = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.SYSTEM)
        return VozUserPreferences(
            themeMode = theme,
            languageCode = prefs.getString("language_code", "en") ?: "en",
            hapticFeedbackEnabled = prefs.getBoolean("haptic_feedback", true),
            dialpadSoundEnabled = prefs.getBoolean("dialpad_sound", true),
            preferredSimId = prefs.getString("preferred_sim_id", "") ?: "",
            oneHandedModeEnabled = prefs.getBoolean("one_handed", false),
            reduceMotionEnabled = prefs.getBoolean("reduce_motion", false),
            confirmBeforeCallEnabled = prefs.getBoolean("confirm_before_call", false)
        )
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun setLanguageCode(code: String) {
        prefs.edit().putString("language_code", code).apply()
    }

    fun setHapticFeedback(enabled: Boolean) {
        prefs.edit().putBoolean("haptic_feedback", enabled).apply()
    }

    fun setDialpadSound(enabled: Boolean) {
        prefs.edit().putBoolean("dialpad_sound", enabled).apply()
    }

    fun setPreferredSimId(simId: String) {
        prefs.edit().putString("preferred_sim_id", simId).apply()
    }

    fun setOneHandedMode(enabled: Boolean) {
        prefs.edit().putBoolean("one_handed", enabled).apply()
    }

    fun setReduceMotion(enabled: Boolean) {
        prefs.edit().putBoolean("reduce_motion", enabled).apply()
    }

    fun setConfirmBeforeCall(enabled: Boolean) {
        prefs.edit().putBoolean("confirm_before_call", enabled).apply()
    }
}
