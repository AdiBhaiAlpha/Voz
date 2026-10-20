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

    var recordingCallId: String? = null
        private set

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

    @Synchronized
    fun startRecording(context: Context, callId: String): Boolean {
        if (!isAudioRecordPermissionGranted(context)) {
            _lastError.value = "RECORD_AUDIO permission is required to record calls."
            return false
        }

        if (_isRecording.value) {
            if (recordingCallId == callId) return true
            _lastError.value = "Another call is already being recorded."
            return false
        }

        try {
            val recordingsDir = File(context.filesDir, "call_recordings").apply { mkdirs() }
            val file = File(recordingsDir, "voz_mic_${java.util.UUID.randomUUID()}.m4a")

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder = recorder
            currentRecordingFile = file
            // Microphone only: Android does not grant third-party access to cellular downlink.
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128000)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(file.absolutePath)

            recorder.prepare()
            recorder.start()
            recordingCallId = callId

            mediaRecorder = recorder
            currentRecordingFile = file
            _isRecording.value = true
            _lastError.value = null
            Log.i(TAG, "Call recording started: ${file.absolutePath}")
            return true
        } catch (e: SecurityException) {
            Log.w(TAG, "Cellular call recording restricted by Android platform security", e)
            _lastError.value = "Call recording is restricted by Android security policies on this device."
            currentRecordingFile?.delete()
            cleanup()
            return false
        } catch (e: IOException) {
            Log.w(TAG, "Audio hardware or format error during recording", e)
            _lastError.value = "Hardware recording failed: ${e.localizedMessage ?: "I/O error"}"
            currentRecordingFile?.delete()
            cleanup()
            return false
        } catch (e: Exception) {
            Log.w(TAG, "Unable to record call", e)
            _lastError.value = "Call recording restricted by Android on this device."
            currentRecordingFile?.delete()
            cleanup()
            return false
        }
    }

    @Synchronized
    fun stopRecording(): File? {
        if (!_isRecording.value && mediaRecorder == null) return null
        val file = currentRecordingFile
        var success = false
        try {
            if (_isRecording.value) {
                mediaRecorder?.stop()
                success = file != null && file.length() > 0
            }
            if (!success) _lastError.value = "No usable audio was captured."
        } catch (_: Exception) {
            _lastError.value = "Recording could not be saved. Android may restrict microphone access during calls."
        } finally {
            cleanup()
        }
        if (!success) file?.delete()
        return if (success) file else null
    }

    fun clearError() {
        _lastError.value = null
    }

    private fun cleanup() {
        try { mediaRecorder?.reset() } catch (_: Exception) {}
        try { mediaRecorder?.release() } catch (_: Exception) {}
        mediaRecorder = null
        recordingCallId = null
        currentRecordingFile = null
        _isRecording.value = false
    }
}
