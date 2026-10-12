package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.vozDataStore: DataStore<Preferences> by preferencesDataStore(name = "voz_user_preferences")

data class VozUserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val languageCode: String = "en",
    val hapticFeedbackEnabled: Boolean = true,
    val dialpadSoundEnabled: Boolean = true,
    val preferredSimId: String = "",
    val oneHandedModeEnabled: Boolean = false,
    val reduceMotionEnabled: Boolean = false,
    val confirmBeforeCallEnabled: Boolean = false
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val DIALPAD_SOUND = booleanPreferencesKey("dialpad_sound")
        val PREFERRED_SIM_ID = stringPreferencesKey("preferred_sim_id")
        val ONE_HANDED_MODE = booleanPreferencesKey("one_handed_mode")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val CONFIRM_BEFORE_CALL = booleanPreferencesKey("confirm_before_call")
    }

    val preferencesFlow: Flow<VozUserPreferences> = context.vozDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val themeName = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
            val theme = runCatching { ThemeMode.valueOf(themeName) }.getOrDefault(ThemeMode.SYSTEM)
            VozUserPreferences(
                themeMode = theme,
                languageCode = prefs[Keys.LANGUAGE_CODE] ?: "en",
                hapticFeedbackEnabled = prefs[Keys.HAPTIC_FEEDBACK] ?: true,
                dialpadSoundEnabled = prefs[Keys.DIALPAD_SOUND] ?: true,
                preferredSimId = prefs[Keys.PREFERRED_SIM_ID] ?: "",
                oneHandedModeEnabled = prefs[Keys.ONE_HANDED_MODE] ?: false,
                reduceMotionEnabled = prefs[Keys.REDUCE_MOTION] ?: false,
                confirmBeforeCallEnabled = prefs[Keys.CONFIRM_BEFORE_CALL] ?: false
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.vozDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguageCode(code: String) {
        context.vozDataStore.edit { it[Keys.LANGUAGE_CODE] = code }
    }

    suspend fun setHapticFeedback(enabled: Boolean) {
        context.vozDataStore.edit { it[Keys.HAPTIC_FEEDBACK] = enabled }
    }

    suspend fun setDialpadSound(enabled: Boolean) {
        context.vozDataStore.edit { it[Keys.DIALPAD_SOUND] = enabled }
    }

    suspend fun setPreferredSimId(simId: String) {
        context.vozDataStore.edit { it[Keys.PREFERRED_SIM_ID] = simId }
    }

    suspend fun setOneHandedMode(enabled: Boolean) {
        context.vozDataStore.edit { it[Keys.ONE_HANDED_MODE] = enabled }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        context.vozDataStore.edit { it[Keys.REDUCE_MOTION] = enabled }
    }

    suspend fun setConfirmBeforeCall(enabled: Boolean) {
        context.vozDataStore.edit { it[Keys.CONFIRM_BEFORE_CALL] = enabled }
    }
}
