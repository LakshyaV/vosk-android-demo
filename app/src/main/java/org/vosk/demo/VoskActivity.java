package org.vosk.demo;

import android.app.Activity;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.IOException;

public class VoskActivity extends Activity implements RecognitionListener {

    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    private Model model;
    private SpeechService speechService;
    private TextView resultView;

    // Accumulates the full transcript
    private final StringBuilder fullTranscript = new StringBuilder();
    // Snapshot of the transcript for external use
    private String transcriptString = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // Initialize Vosk logging
        LibVosk.setLogLevel(LogLevel.INFO);

        // UI hooks
        resultView = findViewById(R.id.result_text);
        resultView.setMovementMethod(new ScrollingMovementMethod());

        Button micButton = findViewById(R.id.recognize_mic);
        micButton.setEnabled(false);
        micButton.setOnClickListener(v -> recognizeMicrophone());

        // Pause/Continue toggle
        ToggleButton pauseButton = findViewById(R.id.pause);
        pauseButton.setOnCheckedChangeListener((CompoundButton btn, boolean isChecked) -> {
            if (speechService != null) {
                if (isChecked) {
                    // PAUSE: stop listening but keep model loaded
                    speechService.stop();
                    btn.setText(R.string.continueRecognition);
                } else {
                    // RESUME: start a fresh listening session
                    try {
                        Recognizer rec = new Recognizer(model, 16000.0f);
                        speechService = new SpeechService(rec, 16000.0f);
                        speechService.startListening(this);
                        btn.setText(R.string.pause);
                    } catch (IOException e) {
                        resultView.setText("Error resuming: " + e.getMessage());
                    }
                }
            }
        });

        // Analyze button
        Button analyzeButton = findViewById(R.id.btn_analyze);
        analyzeButton.setEnabled(true);
        analyzeButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AnalysisActivity.class);
            intent.putExtra("TRANSCRIPT", fullTranscript.toString());
            startActivity(intent);
        });

        // Request RECORD_AUDIO permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSIONS_REQUEST_RECORD_AUDIO
            );
        } else {
            initModel();
        }
    }

    private void initModel() {
        StorageService.unpack(
                this,
                "model-en-us",
                "model",
                model -> {
                    this.model = model;
                    runOnUiThread(() -> findViewById(R.id.recognize_mic).setEnabled(true));
                },
                exception -> runOnUiThread(() ->
                        resultView.setText("Model unpack failed: " + exception.getMessage())
                )
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initModel();
        } else {
            finish();
        }
    }

    private void recognizeMicrophone() {
        Button micButton = findViewById(R.id.recognize_mic);

        if (speechService != null) {
            // STOP listening completely
            speechService.stop();
            speechService.shutdown();
            speechService = null;
            micButton.setText(R.string.recognize_microphone);
        } else {
            try {
                Recognizer rec = new Recognizer(model, 16000.0f);
                speechService = new SpeechService(rec, 16000.0f);
                speechService.startListening(this);
                micButton.setText(R.string.stop_microphone);
            } catch (IOException e) {
                resultView.setText("Recognizer init error: " + e.getMessage());
            }
        }
    }

    @Override
    public void onPartialResult(String hypothesisJson) {
        try {
            JSONObject j = new JSONObject(hypothesisJson);
            String partial = j.optString("partial");
            resultView.setText(fullTranscript.toString() + partial);
        } catch (JSONException e) {
            resultView.setText(fullTranscript.toString() + hypothesisJson);
        }
    }

    @Override
    public void onResult(String hypothesisJson) {
        try {
            JSONObject j = new JSONObject(hypothesisJson);
            String text = j.optString("text");
            if (!text.isEmpty()) {
                if (!text.matches(".*[.!?]$")) text += ".";
                fullTranscript.append(text).append(" ");
                transcriptString = fullTranscript.toString();
                resultView.setText(transcriptString);
            }
        } catch (JSONException e) {
            resultView.setText(fullTranscript.toString());
        }
    }

    @Override
    public void onFinalResult(String hypothesisJson) {
        onResult(hypothesisJson);
    }

    @Override
    public void onError(Exception e) {
        resultView.setText("Error: " + e.getMessage());
    }

    @Override
    public void onTimeout() {
        recognizeMicrophone();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
        }
    }

    /** Expose the transcript to other Activities */
    public String getTranscript() {
        return transcriptString;
    }
}
