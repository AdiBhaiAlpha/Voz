package com.example.telecom

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException

/**
 * Manages call audio recording with honest Android security compliance.
 * On modern Android versions (API 28+), raw cellular call recording is restricted by Google
 * and OEM vendors. This recorder attempts supported audio capture, handles exceptions gracefully,
 * and never pretends that recording worked if blocked by Android.
 */
object CallAudioRecorder {

    private const val TAG = "VOZ_RECORDER"

    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun isAudioRecordPermissionGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun startRecording(context: Context, callId: String): Boolean {
        if (!isAudioRecordPermissionGranted(context)) {
            _lastError.value = "RECORD_AUDIO permission is required to record calls."
            return false
        }

        if (_isRecording.value) {
            return true
        }

        try {
            val recordingsDir = File(context.filesDir, "call_recordings").apply { mkdirs() }
            val file = File(recordingsDir, "voz_call_${callId}_${System.currentTimeMillis()}.m4a")

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            // Use VOICE_COMMUNICATION audio source for clear voice capture
            recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128000)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(file.absolutePath)

            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            currentRecordingFile = file
            _isRecording.value = true
            _lastError.value = null
            Log.i(TAG, "Call recording started: ${file.absolutePath}")
            return true
        } catch (e: SecurityException) {
            Log.w(TAG, "Cellular call recording restricted by Android platform security", e)
            _lastError.value = "Call recording is restricted by Android security policies on this device."
            cleanup()
            return false
        } catch (e: IOException) {
            Log.w(TAG, "Audio hardware or format error during recording", e)
            _lastError.value = "Hardware recording failed: ${e.localizedMessage ?: "I/O error"}"
            cleanup()
            return false
        } catch (e: Exception) {
            Log.w(TAG, "Unable to record call", e)
            _lastError.value = "Call recording restricted by Android on this device."
            cleanup()
            return false
        }
    }

    fun stopRecording(): File? {
        val file = currentRecordingFile
        try {
            if (_isRecording.value) {
                mediaRecorder?.stop()
                Log.i(TAG, "Call recording stopped successfully: ${file?.absolutePath}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping recorder", e)
        } finally {
            cleanup()
        }
        return file
    }

    fun clearError() {
        _lastError.value = null
    }

    private fun cleanup() {
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null
        _isRecording.value = false
    }
}
