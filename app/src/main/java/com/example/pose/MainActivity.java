package com.example.pose;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements PoseLandmarkerHelper.LandmarkerListener, TextToSpeech.OnInitListener {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};

    private PreviewView viewFinder;
    private OverlayView overlayView;
    private TextView tvCount;
    private TextView tvExercise;
    private View gestureFeedbackCard;
    private ProgressBar gestureProgressBar;
    
    // Rest UI
    private View restTimerContainer;
    private TextView tvRestTimer;
    private long restStartTime = 0;
    private final Handler restHandler = new Handler();
    private final Runnable restRunnable = new Runnable() {
        @Override
        public void run() {
            if (repCounter.isResting()) {
                long elapsed = (SystemClock.elapsedRealtime() - restStartTime) / 1000;
                long mins = elapsed / 60;
                long secs = elapsed % 60;
                tvRestTimer.setText(String.format(Locale.US, "%02d:%02d", mins, secs));
                restHandler.postDelayed(this, 1000);
            }
        }
    };
    
    private PoseLandmarkerHelper poseLandmarkerHelper;
    private RepCounter repCounter;
    private ExecutorService cameraExecutor;
    private TextToSpeech tts;
    private boolean isTtsInitialized = false;
    private long lastFeedbackTime = 0;
    private static final long FEEDBACK_COOLDOWN_MS = 3000;

    // Session Management
    private long sessionStartTime = 0;
    private boolean isSessionActive = false;
    private long handsAboveHeadStartTime = -1;
    private static final long GESTURE_HOLD_MS = 2500; // Slightly faster for responsiveness

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        View mainView = findViewById(R.id.main);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        viewFinder = findViewById(R.id.viewFinder);
        overlayView = findViewById(R.id.overlayView);
        tvCount = findViewById(R.id.tvCount);
        tvExercise = findViewById(R.id.tvExercise);
        Button btnEndSession = findViewById(R.id.btnEndSession);
        gestureFeedbackCard = findViewById(R.id.gestureFeedbackCard);
        gestureProgressBar = findViewById(R.id.gestureProgressBar);
        restTimerContainer = findViewById(R.id.restTimerContainer);
        tvRestTimer = findViewById(R.id.tvRestTimer);

        btnEndSession.setOnClickListener(v -> onSessionComplete());

        tts = new TextToSpeech(this, this);

        repCounter = new RepCounter(this);
        repCounter.setRepListener((exercise, count) -> {
            speak(String.valueOf(count));
        });

        repCounter.setFormListener(feedback -> {
            long currentTime = SystemClock.elapsedRealtime();
            if (currentTime - lastFeedbackTime > FEEDBACK_COOLDOWN_MS) {
                speak(feedback);
                lastFeedbackTime = currentTime;
            }
        });

        repCounter.setSetListener(new RepCounter.SetListener() {
            @Override
            public void onSetStarted(String category) {
                speak("Started " + category);
            }

            @Override
            public void onSetFinished(WorkoutSet set) {
                speak("Set finished");
                showRestUI(true);
            }
        });

        poseLandmarkerHelper = new PoseLandmarkerHelper(this, this);
        cameraExecutor = Executors.newSingleThreadExecutor();

        if (allPermissionsGranted()) {
            startCamera();
            startSession();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    private void showRestUI(boolean show) {
        if (show) {
            restTimerContainer.setVisibility(View.VISIBLE);
            restStartTime = SystemClock.elapsedRealtime();
            restHandler.post(restRunnable);
        } else {
            restTimerContainer.setVisibility(View.GONE);
            restHandler.removeCallbacks(restRunnable);
        }
    }

    private void speak(String text) {
        if (isTtsInitialized && tts != null && handsAboveHeadStartTime == -1) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true;
        }
    }

    private void startSession() {
        sessionStartTime = SystemClock.elapsedRealtime();
        isSessionActive = true;
    }

    private void onSessionComplete() {
        if (!isSessionActive) return;
        isSessionActive = false;
        
        repCounter.finishCurrentSet();

        long durationSeconds = (SystemClock.elapsedRealtime() - sessionStartTime) / 1000;
        Map<String, Integer> totalCounts = repCounter.getTotalCounts();
        List<WorkoutSet> completedSets = repCounter.getCompletedSets();

        if (poseLandmarkerHelper != null) {
            poseLandmarkerHelper.close();
        }

        ExerciseSession session = new ExerciseSession(totalCounts, completedSets, durationSeconds);
        WorkoutManager.saveSession(this, session);

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
                        .setTargetRotation(viewFinder.getDisplay().getRotation())
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

                    Bitmap processedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);

                    poseLandmarkerHelper.detectLiveStream(processedBitmap);
                    image.close();
                    bitmap.recycle();
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
    public void onError(String error) {
        runOnUiThread(() -> Toast.makeText(this, error, Toast.LENGTH_SHORT).show());
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
                
                if (handsAboveHeadStartTime == -1) {
                    checkRestGestures(landmarks);
                    repCounter.processLandmarks(landmarks);
                    
                    if (repCounter.isResting()) {
                        tvExercise.setText("RESTING");
                        tvExercise.setTextColor(Color.parseColor("#D88C6C"));
                        tvCount.setText(""); 
                    } else {
                        String category = repCounter.getActiveExerciseCategory();
                        if (category.isEmpty()) {
                            tvExercise.setText("Ready to start");
                            tvExercise.setTextColor(Color.WHITE);
                            tvCount.setText("0");
                        } else {
                            tvExercise.setText(category);
                            tvExercise.setTextColor(Color.parseColor("#8E9AAF"));
                            tvCount.setText(repCounter.getCurrentSetDisplayString());
                        }
                    }
                }
            }
        });
    }

    private void checkRestGestures(List<NormalizedLandmark> landmarks) {
        if (landmarks.size() < 33) return;

        NormalizedLandmark lShoulder = landmarks.get(11);
        NormalizedLandmark rShoulder = landmarks.get(12);
        NormalizedLandmark lWrist = landmarks.get(15);
        NormalizedLandmark rWrist = landmarks.get(16);
        NormalizedLandmark nose = landmarks.get(0);
        
        float lVis = lWrist.visibility().orElse(0f);
        float rVis = rWrist.visibility().orElse(0f);
        if (lVis < 0.5f && rVis < 0.5f) return;

        // PAUSE: Hands crossing to opposite shoulders
        double distLtoR = Math.sqrt(Math.pow(lWrist.x() - rShoulder.x(), 2) + Math.pow(lWrist.y() - rShoulder.y(), 2));
        double distRtoL = Math.sqrt(Math.pow(rWrist.x() - lShoulder.x(), 2) + Math.pow(rWrist.y() - lShoulder.y(), 2));

        if (distLtoR < 0.15f && distRtoL < 0.15f && !repCounter.isResting()) {
            repCounter.setResting(true);
            speak("Resting");
            return;
        } 
        
        // RESUME: Either hand above nose
        if (repCounter.isResting()) {
            boolean handUp = (lVis > 0.5f && lWrist.y() < nose.y()) || (rVis > 0.5f && rWrist.y() < nose.y());
            if (handUp) {
                repCounter.setResting(false);
                showRestUI(false);
                speak("Resuming");
            }
        }
    }

    private void checkHandsAboveHeadGesture(List<NormalizedLandmark> landmarks) {
        if (landmarks.size() < 33) return;

        NormalizedLandmark lWrist = landmarks.get(15);
        NormalizedLandmark rWrist = landmarks.get(16);
        NormalizedLandmark nose = landmarks.get(0);

        if (lWrist.visibility().orElse(0f) < 0.5f || rWrist.visibility().orElse(0f) < 0.5f) {
            resetGesture();
            return;
        }

        if (lWrist.y() < (nose.y() - 0.18f) && rWrist.y() < (nose.y() - 0.18f)) {
            if (handsAboveHeadStartTime == -1) {
                handsAboveHeadStartTime = SystemClock.elapsedRealtime();
                gestureFeedbackCard.setVisibility(View.VISIBLE);
            }

            long elapsed = SystemClock.elapsedRealtime() - handsAboveHeadStartTime;
            gestureProgressBar.setProgress((int) elapsed);

            if (elapsed >= GESTURE_HOLD_MS) {
                onSessionComplete();
            }
        } else {
            resetGesture();
        }
    }

    private void resetGesture() {
        handsAboveHeadStartTime = -1;
        gestureFeedbackCard.setVisibility(View.GONE);
        gestureProgressBar.setProgress(0);
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
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        cameraExecutor.shutdown();
        if (poseLandmarkerHelper != null) {
            poseLandmarkerHelper.close();
        }
        restHandler.removeCallbacks(restRunnable);
    }
}
