package com.example.pose;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.components.containers.Connection;
import java.util.List;

/**
 * Custom view for rendering Pose Landmarker results.
 * Draws a skeleton (connections) and joints (points) over the camera preview.
 */
public class OverlayView extends View {
    private PoseLandmarkerResult results;
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
        linePaint.setStrokeWidth(6F); // Decreased from 12F for a cleaner look
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setAntiAlias(true);

        pointPaint = new Paint();
        pointPaint.setColor(Color.YELLOW);
        pointPaint.setStrokeWidth(8F); // Decreased from 16F
        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setAntiAlias(true);
    }

    public void setResults(PoseLandmarkerResult results, int imageWidth, int imageHeight) {
        this.results = results;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        invalidate(); // Force a redraw on the UI thread
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (results == null || results.landmarks().isEmpty()) return;

        // Calculate scaling to fit the image into the view
        float scaleX = (float) getWidth() / imageWidth;
        float scaleY = (float) getHeight() / imageHeight;
        float scale = Math.max(scaleX, scaleY);

        float offsetX = (getWidth() - imageWidth * scale) / 2f;
        float offsetY = (getHeight() - imageHeight * scale) / 2f;

        float minVisibility = 0.5f;

        for (List<NormalizedLandmark> landmarks : results.landmarks()) {
            // 1. Draw Skeleton Connections
            for (Connection connection : PoseLandmarker.POSE_LANDMARKS) {
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

            // 2. Draw Joint Points
            for (NormalizedLandmark landmark : landmarks) {
                if (landmark.visibility().orElse(0f) > minVisibility) {
                    canvas.drawCircle(
                            landmark.x() * imageWidth * scale + offsetX,
                            landmark.y() * imageHeight * scale + offsetY,
                            5f, // Decreased point radius from 8f
                            pointPaint
                    );
                }
            }
        }
    }
}
