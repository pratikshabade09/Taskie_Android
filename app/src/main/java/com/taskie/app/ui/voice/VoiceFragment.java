package com.taskie.app.ui.voice;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.taskie.app.R;
import com.taskie.app.data.model.Task;
import com.taskie.app.utils.DateUtils;
import com.taskie.app.viewmodel.TaskViewModel;
import com.taskie.app.voice.IntentParser;
import com.taskie.app.voice.VoiceManager;

/**
 * Voice assistant screen.
 * Tap the microphone FAB → SpeechRecognizer listens → IntentParser extracts task fields
 * → TTS confirms → User taps Confirm or Cancel.
 */
public class VoiceFragment extends Fragment implements VoiceManager.VoiceCallback {

    private TaskViewModel    taskViewModel;
    private VoiceManager     voiceManager;

    private FloatingActionButton fabMic;
    private TextView             tvStatus;
    private TextView             tvTranscript;
    private TextView             tvParsedResult;
    private View                 cardParsed;
    private Button               btnConfirm;
    private Button               btnCancel;
    private View                 waveAnimation;
    private TextView             tvHistory;

    private boolean isListening = false;
    private Task    parsedTask  = null;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startListening();
                else if (isAdded()) tvStatus.setText("Microphone permission required");
            });

    public static VoiceFragment newInstance() {
        return new VoiceFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_voice, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        taskViewModel = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);
        voiceManager  = new VoiceManager(requireContext(), this);

        bindViews(view);
        setupListeners();
        showIdleState();

        // Observe confirmed/pending voice tasks
        taskViewModel.getPendingVoiceTask().observe(getViewLifecycleOwner(), task -> {
            if (task != null) showParsedCard(task);
            else if (cardParsed != null) cardParsed.setVisibility(View.GONE);
        });
    }

    // ── View binding ──────────────────────────────────────────────────────────

    private void bindViews(View view) {
        fabMic         = view.findViewById(R.id.fab_mic);
        tvStatus       = view.findViewById(R.id.tv_voice_status);
        tvTranscript   = view.findViewById(R.id.tv_transcript);
        tvParsedResult = view.findViewById(R.id.tv_parsed_result);
        cardParsed     = view.findViewById(R.id.card_parsed_task);
        btnConfirm     = view.findViewById(R.id.btn_confirm_task);
        btnCancel      = view.findViewById(R.id.btn_cancel_task);
        waveAnimation  = view.findViewById(R.id.view_wave);
        tvHistory      = view.findViewById(R.id.tv_usage_hint);
    }

    // ── Listeners ─────────────────────────────────────────────────────────────

    private void setupListeners() {
        fabMic.setOnClickListener(v -> {
            if (isListening) {
                stopListening();
            } else {
                requestMicAndListen();
            }
        });

        btnConfirm.setOnClickListener(v -> confirmTask());
        btnCancel.setOnClickListener(v -> cancelTask());
    }

    // ── Mic permission ────────────────────────────────────────────────────────

    private void requestMicAndListen() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    // ── Listening state machine ───────────────────────────────────────────────

    private void startListening() {
        isListening = true;
        tvStatus.setText("Listening…");
        tvTranscript.setText("");
        cardParsed.setVisibility(View.GONE);

        // Pulse animation on FAB
        Animation pulse = AnimationUtils.loadAnimation(requireContext(), R.anim.pulse);
        fabMic.startAnimation(pulse);
        fabMic.setImageResource(R.drawable.ic_mic_active);
        waveAnimation.setVisibility(View.VISIBLE);
        waveAnimation.startAnimation(
                AnimationUtils.loadAnimation(requireContext(), R.anim.wave));

        voiceManager.startListening();
    }

    private void stopListening() {
        isListening = false;
        voiceManager.stopListening();
        fabMic.clearAnimation();
        fabMic.setImageResource(R.drawable.ic_mic);
        waveAnimation.clearAnimation();
        waveAnimation.setVisibility(View.INVISIBLE);
        tvStatus.setText("Tap the mic to speak");
    }

    // ── VoiceManager.VoiceCallback ────────────────────────────────────────────

    @Override
    public void onSpeechResult(String text) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (!isAdded()) return;
            tvTranscript.setText("\"" + text + "\"");
            tvStatus.setText("Parsing intent…");
            stopListening();
            parseAndPreview(text);
        });
    }

    @Override
    public void onPartialResult(String partial) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (!isAdded()) return;
            tvTranscript.setText(partial + "…");
        });
    }

    @Override
    public void onError(String error) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (!isAdded()) return;
            stopListening();
            tvStatus.setText("Could not hear you. Tap to retry.");
        });
    }

    // ── Intent parsing ────────────────────────────────────────────────────────

    private void parseAndPreview(String text) {
        Task task = IntentParser.parse(text);
        if (task != null) {
            parsedTask = task;
            taskViewModel.setPendingVoiceTask(task);

            // TTS confirmation
            String confirmation = buildConfirmation(task);
            voiceManager.speak(confirmation);
        } else {
            tvStatus.setText("Could not understand. Please try again.");
            tvParsedResult.setText("");
            cardParsed.setVisibility(View.GONE);
        }
    }

    private void showParsedCard(Task task) {
        if (!isAdded() || cardParsed == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("📝 ").append(task.getTitle()).append("\n");
        if (task.getDueDate() > 0) {
            sb.append("📅 ").append(DateUtils.formatRelative(task.getDueDate())).append("\n");
        }
        sb.append("🔥 Priority: ").append(task.getPriorityLabel());
        if (task.getTags() != null && !task.getTags().isEmpty()) {
            sb.append("\n🏷 ").append(task.getTags());
        }
        tvParsedResult.setText(sb.toString());
        cardParsed.setVisibility(View.VISIBLE);
        btnConfirm.setVisibility(View.VISIBLE);
        btnCancel.setVisibility(View.VISIBLE);
        cardParsed.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.slide_up_fade));
        tvStatus.setText("Does this look right?");
    }

    private String buildConfirmation(Task task) {
        StringBuilder sb = new StringBuilder("Got it. ");
        sb.append(task.getTitle());
        if (task.getDueDate() > 0) {
            sb.append(", ").append(DateUtils.formatVoice(task.getDueDate()));
        }
        return sb.toString();
    }

    // ── Confirm / Cancel ──────────────────────────────────────────────────────

    private void confirmTask() {
        taskViewModel.confirmVoiceTask();
        cardParsed.setVisibility(View.GONE);
        btnConfirm.setVisibility(View.GONE);
        btnCancel.setVisibility(View.GONE);
        tvStatus.setText("Task added!");
        tvTranscript.setText("");
        parsedTask = null;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isAdded()) showIdleState();
        }, 2000);
    }

    private void cancelTask() {
        taskViewModel.discardVoiceTask();
        cardParsed.setVisibility(View.GONE);
        btnConfirm.setVisibility(View.GONE);
        btnCancel.setVisibility(View.GONE);
        parsedTask = null;
        showIdleState();
    }

    private void showIdleState() {
        if (!isAdded()) return;
        tvStatus.setText("Tap the mic to create a task with your voice");
        tvTranscript.setText("");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (voiceManager != null) {
            voiceManager.destroy();
        }
    }
}
