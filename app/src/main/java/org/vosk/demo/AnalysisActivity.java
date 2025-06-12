package org.vosk.demo;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

public class AnalysisActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analysis);

        // 1) Read the transcript passed from VoskActivity
        String transcript = getIntent().getStringExtra("TRANSCRIPT");

        // 2) Show it in the first TextView
        TextView orig = findViewById(R.id.original_transcript);
        orig.setText(transcript != null ? transcript : "");

        // 3) Enable “Simplify” only if we actually have text
        Button simplify = findViewById(R.id.btn_simplify);
        simplify.setEnabled(transcript != null && !transcript.isEmpty());
    }
}
