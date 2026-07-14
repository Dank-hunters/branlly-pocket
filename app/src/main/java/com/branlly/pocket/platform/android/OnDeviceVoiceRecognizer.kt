package com.branlly.pocket.platform.android

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi

sealed interface VoiceRecognitionResult {
    data class Transcripts(val values: List<String>) : VoiceRecognitionResult
    data object NoMatch : VoiceRecognitionResult
    data object Busy : VoiceRecognitionResult
    data object Unavailable : VoiceRecognitionResult
    data object PermissionMissing : VoiceRecognitionResult
    data object Failure : VoiceRecognitionResult
}

/** Utilise exclusivement le moteur Android embarqué. Aucun fallback réseau. */
class OnDeviceVoiceRecognizer(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun start(onResult: (VoiceRecognitionResult) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            onResult(VoiceRecognitionResult.Unavailable)
            return
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            onResult(VoiceRecognitionResult.Unavailable)
            return
        }
        val speechRecognizer = recognizer ?: createRecognizer().also { recognizer = it }
        speechRecognizer.setRecognitionListener(StrictRecognitionListener(onResult))
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        speechRecognizer.startListening(intent)
    }

    fun destroy() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun createRecognizer(): SpeechRecognizer =
        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)

    private class StrictRecognitionListener(
        private val onResult: (VoiceRecognitionResult) -> Unit,
    ) : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onError(error: Int) {
            val result = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceRecognitionResult.NoMatch
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceRecognitionResult.Busy
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceRecognitionResult.PermissionMissing
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_SERVER,
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_AUDIO -> VoiceRecognitionResult.Failure
                else -> VoiceRecognitionResult.Failure
            }
            onResult(result)
        }

        override fun onResults(results: Bundle?) {
            val transcripts = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.asSequence()
                ?.map(String::trim)
                ?.filter(String::isNotEmpty)
                ?.take(MAX_RESULTS)
                ?.toList()
                .orEmpty()
            onResult(
                if (transcripts.isEmpty()) VoiceRecognitionResult.NoMatch
                else VoiceRecognitionResult.Transcripts(transcripts),
            )
        }

        override fun onSegmentResults(segmentResults: Bundle) = Unit
        override fun onEndOfSegmentedSession() = Unit
    }

    companion object {
        private const val MAX_RESULTS = 3
    }
}
