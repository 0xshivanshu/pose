package com.example.pose;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.components.containers.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Custom view for rendering Pose Landmarker results with balanced smoothing.
 */
public class OverlayView extends View {
    private List<List<NormalizedLandmark>> smoothedLandmarks = null;
    
    // Balanced smoothing: 0.35 provides responsiveness while maintaining stability.
    // This fixes the "wiggle" issue on fast movements like bicep curls.
    private static final float SMOOTHING_FACTOR = 0.35f; 
    
    private Paint linePaint;
    private Paint pointPaint;
    private int imageWidth = 1;
    private int imageHeight = 1;

    public OverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initPaints();
    }

    private void initPaints() {
        linePaint = new Paint();
        linePaint.setColor(Color.CYAN);
        linePaint.setStrokeWidth(10F); 
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setAntiAlias(true);

        pointPaint = new Paint();
        pointPaint.setColor(Color.YELLOW);
        pointPaint.setStrokeWidth(12F);
        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setAntiAlias(true);
    }

    public void setResults(PoseLandmarkerResult results, int imageWidth, int imageHeight) {
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        
        if (results == null || results.landmarks().isEmpty()) {
            smoothedLandmarks = null;
        } else {
            smoothLandmarks(results.landmarks());
        }
        invalidate();
    }

    private void smoothLandmarks(List<List<NormalizedLandmark>> newResults) {
        if (smoothedLandmarks == null || smoothedLandmarks.size() != newResults.size()) {
            smoothedLandmarks = new ArrayList<>();
            for (List<NormalizedLandmark> list : newResults) {
                smoothedLandmarks.add(new ArrayList<>(list));
            }
            return;
        }

        for (int i = 0; i < newResults.size(); i++) {
            List<NormalizedLandmark> currentList = newResults.get(i);
            List<NormalizedLandmark> prevList = smoothedLandmarks.get(i);
            List<NormalizedLandmark> smoothedList = new ArrayList<>();

            for (int j = 0; j < currentList.size(); j++) {
                NormalizedLandmark c = currentList.get(j);
                NormalizedLandmark p = prevList.get(j);

                // EMA Smoothing logic
                float sx = (SMOOTHING_FACTOR * c.x()) + ((1 - SMOOTHING_FACTOR) * p.x());
                float sy = (SMOOTHING_FACTOR * c.y()) + ((1 - SMOOTHING_FACTOR) * p.y());
                float sz = (SMOOTHING_FACTOR * c.z()) + ((1 - SMOOTHING_FACTOR) * p.z());
                float sv = (SMOOTHING_FACTOR * c.visibility().orElse(0f)) + ((1 - SMOOTHING_FACTOR) * p.visibility().orElse(0f));
                float sp = (SMOOTHING_FACTOR * c.presence().orElse(0f)) + ((1 - SMOOTHING_FACTOR) * p.presence().orElse(0f));

                smoothedList.add(NormalizedLandmark.create(sx, sy, sz, Optional.of(sv), Optional.of(sp)));
            }
            smoothedLandmarks.set(i, smoothedList);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (smoothedLandmarks == null) return;

        float scaleX = (float) getWidth() / imageWidth;
        float scaleY = (float) getHeight() / imageHeight;
        float scale = Math.max(scaleX, scaleY);

        float offsetX = (getWidth() - imageWidth * scale) / 2f;
        float offsetY = (getHeight() - imageHeight * scale) / 2f;

        // Slightly lower visibility threshold for drawing to keep overlay visible in grainy light
        float minVisibility = 0.4f;

        for (List<NormalizedLandmark> landmarks : smoothedLandmarks) {
            for (Connection connection : PoseLandmarker.POSE_LANDMARKS) {
                if (connection.start() <= 10 || connection.end() <= 10) continue;

                NormalizedLandmark start = landmarks.get(connection.start());
                NormalizedLandmark end = landmarks.get(connection.end());

                if (start.visibility().orElse(0f) > minVisibility && end.visibility().orElse(0f) > minVisibility) {
                    canvas.drawLine(
                            start.x() * imageWidth * scale + offsetX,
                            start.y() * imageHeight * scale + offsetY,
                            end.x() * imageWidth * scale + offsetX,
                            end.y() * imageHeight * scale + offsetY,
                            linePaint
                    );
                }
            }

            for (int i = 0; i < landmarks.size(); i++) {
                if (i <= 10) continue;
                NormalizedLandmark landmark = landmarks.get(i);
                if (landmark.visibility().orElse(0f) > minVisibility) {
                    canvas.drawCircle(
                            landmark.x() * imageWidth * scale + offsetX,
                            landmark.y() * imageHeight * scale + offsetY,
                            6f,
                            pointPaint
                    );
                }
            }
        }
    }
}
