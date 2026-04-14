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

public class OverlayView extends View {
    private float[][] smoothedX = new float[1][33];
    private float[][] smoothedY = new float[1][33];
    private float[][] smoothedV = new float[1][33];
    private boolean hasResults = false;

    private static final float SMOOTHING_FACTOR = 0.85f;
    private static final float MIN_VISIBILITY = 0.5f;

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
        linePaint.setColor(Color.WHITE);
        linePaint.setStrokeWidth(8F);
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
            hasResults = false;
        } else {
            hasResults = true;
            List<NormalizedLandmark> landmarks = results.landmarks().get(0);
            for (int i = 0; i < 33; i++) {
                NormalizedLandmark l = landmarks.get(i);
                float vis = l.visibility().orElse(0f);
                if (smoothedX[0][i] == 0) {
                    smoothedX[0][i] = l.x();
                    smoothedY[0][i] = l.y();
                    smoothedV[0][i] = vis;
                } else {
                    smoothedX[0][i] = smoothedX[0][i] + SMOOTHING_FACTOR * (l.x() - smoothedX[0][i]);
                    smoothedY[0][i] = smoothedY[0][i] + SMOOTHING_FACTOR * (l.y() - smoothedY[0][i]);
                    smoothedV[0][i] = smoothedV[0][i] + SMOOTHING_FACTOR * (vis - smoothedV[0][i]);
                }
            }
        }
        postInvalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (!hasResults) return;

        float scaleX = (float) getWidth() / imageWidth;
        float scaleY = (float) getHeight() / imageHeight;
        float scale = Math.max(scaleX, scaleY);

        float offsetX = (getWidth() - imageWidth * scale) / 2f;
        float offsetY = (getHeight() - imageHeight * scale) / 2f;

        for (Connection connection : PoseLandmarker.POSE_LANDMARKS) {
            int startIdx = connection.start();
            int endIdx = connection.end();
            if (startIdx <= 10 || endIdx <= 10) continue;

            if (smoothedV[0][startIdx] > MIN_VISIBILITY && smoothedV[0][endIdx] > MIN_VISIBILITY) {
                canvas.drawLine(
                        smoothedX[0][startIdx] * imageWidth * scale + offsetX,
                        smoothedY[0][startIdx] * imageHeight * scale + offsetY,
                        smoothedX[0][endIdx] * imageWidth * scale + offsetX,
                        smoothedY[0][endIdx] * imageHeight * scale + offsetY,
                        linePaint
                );
            }
        }

        for (int i = 11; i < 33; i++) {
            if (smoothedV[0][i] > MIN_VISIBILITY) {
                canvas.drawCircle(
                        smoothedX[0][i] * imageWidth * scale + offsetX,
                        smoothedY[0][i] * imageHeight * scale + offsetY,
                        6f,
                        pointPaint
                );
            }
        }
    }
}