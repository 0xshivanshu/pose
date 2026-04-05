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
import java.util.List;
import java.util.Optional;

/**
 * High-performance OverlayView for rendering Pose Landmarker results.
 * Optimized for zero-allocation in the draw loop and responsive smoothing.
 */
public class OverlayView extends View {
    // Responsive smoothing: 0.85 ensures minimal lag while still filtering micro-jitter.
    private static final float SMOOTHING_FACTOR = 0.85f; 
    
    // Landmark storage using simple arrays to avoid object allocation (ArrayList) per frame.
    private float[][] smoothedX;
    private float[][] smoothedY;
    private float[][] smoothedV;
    
    private Paint linePaint;
    private Paint pointPaint;
    private int imageWidth = 1;
    private int imageHeight = 1;
    private boolean hasResults = false;

    public OverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initPaints();
    }

    private void initPaints() {
        linePaint = new Paint();
        linePaint.setColor(Color.WHITE);
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
        
        if (results == null || results.landmarks() == null || results.landmarks().isEmpty()) {
            hasResults = false;
        } else {
            hasResults = true;
            updateSmoothedLandmarks(results.landmarks());
        }
        postInvalidate();
    }

    private void updateSmoothedLandmarks(List<List<NormalizedLandmark>> newResults) {
        int numPersons = newResults.size();
        
        // Initialize or resize arrays if needed
        if (smoothedX == null || smoothedX.length != numPersons) {
            smoothedX = new float[numPersons][33];
            smoothedY = new float[numPersons][33];
            smoothedV = new float[numPersons][33];
            
            for (int i = 0; i < numPersons; i++) {
                List<NormalizedLandmark> person = newResults.get(i);
                for (int j = 0; j < 33; j++) {
                    NormalizedLandmark lm = person.get(j);
                    smoothedX[i][j] = lm.x();
                    smoothedY[i][j] = lm.y();
                    smoothedV[i][j] = lm.visibility().orElse(0f);
                }
            }
            return;
        }

        for (int i = 0; i < numPersons; i++) {
            List<NormalizedLandmark> person = newResults.get(i);
            for (int j = 0; j < 33; j++) {
                NormalizedLandmark lm = person.get(j);
                // EMA filter: more weight (0.85) on current frame for zero lag
                smoothedX[i][j] = (SMOOTHING_FACTOR * lm.x()) + ((1 - SMOOTHING_FACTOR) * smoothedX[i][j]);
                smoothedY[i][j] = (SMOOTHING_FACTOR * lm.y()) + ((1 - SMOOTHING_FACTOR) * smoothedY[i][j]);
                smoothedV[i][j] = (SMOOTHING_FACTOR * lm.visibility().orElse(0f)) + ((1 - SMOOTHING_FACTOR) * smoothedV[i][j]);
            }
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (!hasResults || smoothedX == null) return;

        float scaleX = (float) getWidth() / imageWidth;
        float scaleY = (float) getHeight() / imageHeight;
        float scale = Math.max(scaleX, scaleY);

        float offsetX = (getWidth() - imageWidth * scale) / 2f;
        float offsetY = (getHeight() - imageHeight * scale) / 2f;

        float minVisibility = 0.5f;

        for (int p = 0; p < smoothedX.length; p++) {
            // 1. Draw Connections
            for (Connection connection : PoseLandmarker.POSE_LANDMARKS) {
                int startIdx = connection.start();
                int endIdx = connection.end();
                
                // Skip face landmarks for cleaner UI and faster render
                if (startIdx <= 10 || endIdx <= 10) continue;

                if (smoothedV[p][startIdx] > minVisibility && smoothedV[p][endIdx] > minVisibility) {
                    canvas.drawLine(
                            smoothedX[p][startIdx] * imageWidth * scale + offsetX,
                            smoothedY[p][startIdx] * imageHeight * scale + offsetY,
                            smoothedX[p][endIdx] * imageWidth * scale + offsetX,
                            smoothedY[p][endIdx] * imageHeight * scale + offsetY,
                            linePaint
                    );
                }
            }

            // 2. Draw Joint Circles
            for (int i = 11; i < 33; i++) {
                if (smoothedV[p][i] > minVisibility) {
                    canvas.drawCircle(
                            smoothedX[p][i] * imageWidth * scale + offsetX,
                            smoothedY[p][i] * imageHeight * scale + offsetY,
                            8f,
                            pointPaint
                    );
                }
            }
        }
    }
}
