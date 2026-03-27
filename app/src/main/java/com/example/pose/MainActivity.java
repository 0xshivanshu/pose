package com.example.pose;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements PoseLandmarkerHelper.LandmarkerListener {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};

    private PreviewView viewFinder;
    private OverlayView overlayView;
    private TextView tvCount;
    private TextView tvExercise;
    private Button btnEndSession;
    private View gestureFeedbackCard;
    private ProgressBar gestureProgressBar;
    
    private PoseLandmarkerHelper poseLandmarkerHelper;
    private RepCounter repCounter;
    private ExecutorService cameraExecutor;

    // Session Management
    private long sessionStartTime = 0;
    private boolean isSessionActive = false;
    private long handsAboveHeadStartTime = -1;
    private static final long GESTURE_HOLD_MS = 3000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Handle Window Insets to bring the app screen below the notification bar
        View mainView = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        viewFinder = findViewById(R.id.viewFinder);
        overlayView = findViewById(R.id.overlayView);
        tvCount = findViewById(R.id.tvCount);
        tvExercise = findViewById(R.id.tvExercise);
        btnEndSession = findViewById(R.id.btnEndSession);
        gestureFeedbackCard = findViewById(R.id.gestureFeedbackCard);
        gestureProgressBar = findViewById(R.id.gestureProgressBar);

        btnEndSession.setOnClickListener(v -> onSessionComplete());

        repCounter = new RepCounter();
        poseLandmarkerHelper = new PoseLandmarkerHelper(this, this);
        cameraExecutor = Executors.newSingleThreadExecutor();

        if (allPermissionsGranted()) {
            startCamera();
            startSession();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    private void startSession() {
        sessionStartTime = SystemClock.elapsedRealtime();
        isSessionActive = true;
    }

    private void onSessionComplete() {
        if (!isSessionActive) return;
        isSessionActive = false;

        long durationSeconds = (SystemClock.elapsedRealtime() - sessionStartTime) / 1000;
        Map<String, Integer> counts = repCounter.getCounts();

        // Stop processing
        if (poseLandmarkerHelper != null) {
            poseLandmarkerHelper.close();
        }

        ExerciseSession session = new ExerciseSession(counts, durationSeconds);
        Intent intent = new Intent(this, SummaryActivity.class);
        intent.putExtra("EXERCISE_SESSION", session);
        startActivity(intent);
        finish();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    if (!isSessionActive) {
                        image.close();
                        return;
                    }
                    Bitmap bitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(image.getPlanes()[0].getBuffer());

                    Matrix matrix = new Matrix();
                    matrix.postRotate(image.getImageInfo().getRotationDegrees());
                    matrix.postScale(-1f, 1f, (float) image.getWidth() / 2f, (float) image.getHeight() / 2f);

                    Bitmap processedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

                    poseLandmarkerHelper.detectLiveStream(processedBitmap);
                    image.close();
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    public void onResults(PoseLandmarkerResult result, MPImage image) {
        if (!isSessionActive) return;

        runOnUiThread(() -> {
            if (overlayView != null) {
                overlayView.setResults(result, image.getWidth(), image.getHeight());
            }

            if (result.landmarks() != null && !result.landmarks().isEmpty()) {
                List<NormalizedLandmark> landmarks = result.landmarks().get(0);
                
                checkHandsAboveHeadGesture(landmarks);

                // Let RepCounter handle multiple exercises autonomously
                repCounter.processLandmarks(landmarks);
                
                // Update UI display
                tvCount.setText(String.valueOf(repCounter.getTotalReps()));
                String exercise = repCounter.getLastDetectedExercise();
                tvExercise.setText(exercise.isEmpty() ? "Detecting..." : exercise);
            }
        });
    }

    private void checkHandsAboveHeadGesture(List<NormalizedLandmark> landmarks) {
        if (landmarks.size() < 20) {
            resetGestureFeedback();
            return;
        }

        NormalizedLandmark leftWrist = landmarks.get(15);
        NormalizedLandmark rightWrist = landmarks.get(16);
        NormalizedLandmark leftShoulder = landmarks.get(11);
        NormalizedLandmark rightShoulder = landmarks.get(12);

        // Visibility check for gesture
        if (leftWrist.visibility().orElse(0f) < 0.5f || rightWrist.visibility().orElse(0f) < 0.5f) {
            resetGestureFeedback();
            return;
        }

        // Gesture: Both wrists significantly above both shoulders (y is smaller at top)
        boolean handsUp = leftWrist.y() < (leftShoulder.y() - 0.1f) && 
                         rightWrist.y() < (rightShoulder.y() - 0.1f);

        if (handsUp) {
            if (handsAboveHeadStartTime == -1) {
                handsAboveHeadStartTime = SystemClock.elapsedRealtime();
                gestureFeedbackCard.setVisibility(View.VISIBLE);
            }
            
            long elapsed = SystemClock.elapsedRealtime() - handsAboveHeadStartTime;
            gestureProgressBar.setProgress((int) elapsed);

            if (elapsed > GESTURE_HOLD_MS) {
                onSessionComplete();
            }
        } else {
            resetGestureFeedback();
        }
    }

    private void resetGestureFeedback() {
        handsAboveHeadStartTime = -1;
        gestureFeedbackCard.setVisibility(View.GONE);
        gestureProgressBar.setProgress(0);
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> Toast.makeText(this, error, Toast.LENGTH_SHORT).show());
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
                startSession();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isSessionActive = false;
        cameraExecutor.shutdown();
        if (poseLandmarkerHelper != null) {
            poseLandmarkerHelper.close();
        }
    }
}