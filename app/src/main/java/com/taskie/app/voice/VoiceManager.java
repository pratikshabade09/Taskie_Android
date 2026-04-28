package com.taskie.app.voice;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

/**
 * Wraps Android's SpeechRecognizer and TextToSpeech into one clean API.
 * Call startListening() → callback fires onSpeechResult(text).
 * Call speak(text) to play TTS confirmation.
 */
public class VoiceManager {

    private final SpeechRecognizer recognizer;
    private TextToSpeech           tts;
    private final VoiceCallback    callback;

    private boolean ttsReady = false;

    public VoiceManager(Context context, VoiceCallback callback) {
        this.callback = callback;

        // ── SpeechRecognizer ─────────────────────────────────────────────────
        recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizer.setRecognitionListener(new RecognitionListener() {

            @Override
            public void onReadyForSpeech(Bundle params) {}

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {}

            @Override
            public void onError(int error) {
                String msg;
                switch (error) {
                    case SpeechRecognizer.ERROR_NO_MATCH:
                        msg = "No speech matched. Please try again.";
                        break;
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        msg = "Listening timed out.";
                        break;
                    case SpeechRecognizer.ERROR_AUDIO:
                        msg = "Audio recording error.";
                        break;
                    case SpeechRecognizer.ERROR_NETWORK:
                        msg = "Network error. Check connection.";
                        break;
                    case SpeechRecognizer.ERROR_CLIENT:
                        // This often happens if we call start/stop too quickly
                        return;
                    default:
                        msg = "Could not recognise speech (error " + error + ").";
                }
                callback.onError(msg);
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    callback.onSpeechResult(matches.get(0));
                } else {
                    callback.onError("No result");
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> partial =
                        partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (partial != null && !partial.isEmpty()) {
                    callback.onPartialResult(partial.get(0));
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });

        // ── TextToSpeech ──────────────────────────────────────────────────────
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.getDefault());
                ttsReady = (result != TextToSpeech.LANG_MISSING_DATA
                         && result != TextToSpeech.LANG_NOT_SUPPORTED);
                tts.setSpeechRate(0.95f);
                tts.setPitch(1.0f);
            }
        });

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {}
            @Override public void onDone(String utteranceId) {}
            @Override public void onError(String utteranceId) {}
        });
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void startListening() {
        // Ensure we run on UI thread as some implementations require it
        new Handler(Looper.getMainLooper()).post(() -> {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            recognizer.startListening(intent);
        });
    }

    public void stopListening() {
        new Handler(Looper.getMainLooper()).post(recognizer::stopListening);
    }

    public void speak(String text) {
        if (ttsReady) {
            String uid = UUID.randomUUID().toString();
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, uid);
        }
    }

    public void destroy() {
        recognizer.destroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    // ── Callback interface ────────────────────────────────────────────────────

    public interface VoiceCallback {
        void onSpeechResult(String text);
        void onPartialResult(String partial);
        void onError(String error);
    }
}
