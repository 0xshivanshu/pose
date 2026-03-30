package com.example.pose;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.Map;

public class SummaryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summary);

        // Handle Window Insets
        View rootView = findViewById(R.id.summary_root);
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        ExerciseSession session = (ExerciseSession) getIntent().getSerializableExtra("EXERCISE_SESSION");
        
        TextView tvTotalReps = findViewById(R.id.tvTotalReps);
        TextView tvDuration = findViewById(R.id.tvDuration);
        LinearLayout llBreakdownContainer = findViewById(R.id.llBreakdownContainer);
        Button btnRestart = findViewById(R.id.btnRestart);
        Button btnHome = findViewById(R.id.btnHome);

        if (session != null) {
            tvTotalReps.setText(String.valueOf(session.getTotalReps()));
            tvDuration.setText(session.getDurationSeconds() + "s");

            Map<String, Integer> counts = session.getExerciseCounts();
            boolean hasData = false;
            
            LayoutInflater inflater = LayoutInflater.from(this);
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (entry.getValue() > 0) {
                    View itemView = inflater.inflate(R.layout.item_exercise_summary, llBreakdownContainer, false);
                    TextView tvName = itemView.findViewById(R.id.tvExerciseName);
                    TextView tvCount = itemView.findViewById(R.id.tvExerciseCount);
                    
                    tvName.setText(entry.getKey());
                    tvCount.setText(String.valueOf(entry.getValue()));
                    
                    llBreakdownContainer.addView(itemView);
                    hasData = true;
                }
            }

            if (!hasData) {
                TextView tvNoData = new TextView(this);
                tvNoData.setText("No exercises detected.");
                tvNoData.setTextColor(android.graphics.Color.WHITE);
                tvNoData.setPadding(0, 32, 0, 0);
                tvNoData.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                llBreakdownContainer.addView(tvNoData);
            }
        }

        btnRestart.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }
}
