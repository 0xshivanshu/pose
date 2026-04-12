package com.example.pose;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Bundle;
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
    private static final long GESTURE_HOLD_MS = 3000;

    // Latency optimization: Shared resources
    private Bitmap sharedBitmap = null;
    private Bitmap processedBitmap = null;
    private Canvas processedCanvas = null;
    private Matrix drawMatrix = new Matrix();

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

        btnEndSession.setOnClickListener(v -> onSessionComplete());

        tts = new TextToSpeech(this, this);

        repCounter = new RepCounter();
        repCounter.setRepListener((exercise, count) -> speak(String.valueOf(count)));

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
                speak("Set finished. " + set.getTotalReps() + " reps");
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

    private void speak(String text) {
        if (isTtsInitialized && tts != null && handsAboveHeadStartTime == -1) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.US);
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

                    // Strict latency optimization: Avoid all allocations in the loop
                    if (sharedBitmap == null || sharedBitmap.getWidth() != image.getWidth() || sharedBitmap.getHeight() != image.getHeight()) {
                        sharedBitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
                        
                        int rotation = image.getImageInfo().getRotationDegrees();
                        int targetW = (rotation % 180 == 0) ? image.getWidth() : image.getHeight();
                        int targetH = (rotation % 180 == 0) ? image.getHeight() : image.getWidth();
                        
                        processedBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
                        processedCanvas = new Canvas(processedBitmap);
                        
                        drawMatrix.reset();
                        drawMatrix.postRotate(rotation);
                        
                        float centerX = targetW / 2f;
                        float centerY = targetH / 2f;
                        drawMatrix.postScale(-1f, 1f, centerX, centerY);
                    }
                    
                    sharedBitmap.copyPixelsFromBuffer(image.getPlanes()[0].getBuffer());
                    processedCanvas.drawBitmap(sharedBitmap, drawMatrix, null);

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
                
                if (handsAboveHeadStartTime == -1) {
                    checkRestGestures(landmarks);
                    repCounter.processLandmarks(landmarks);
                    updateUI();
                }
            }
        });
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> Toast.makeText(this, error, Toast.LENGTH_SHORT).show());
    }

    private void updateUI() {
        if (repCounter.isResting()) {
            tvExercise.setText("FINISHED SET / RESTING");
            tvExercise.setTextColor(Color.YELLOW);
            tvCount.setText("0"); 
        } else {
            String category = repCounter.getActiveExerciseCategory();
            if (category.isEmpty()) {
                tvExercise.setText("Ready to start...");
                tvExercise.setTextColor(Color.WHITE);
                tvCount.setText("0");
            } else {
                tvExercise.setText(category);
                tvExercise.setTextColor(Color.GREEN);
                
                if (category.equals(RepCounter.CAT_BICEP_CURL)) {
                    Map<String, Integer> counts = repCounter.getCurrentSetExerciseCounts();
                    Integer left = counts.getOrDefault(RepCounter.BICEP_CURL_LEFT, 0);
                    Integer right = counts.getOrDefault(RepCounter.BICEP_CURL_RIGHT, 0);
                    tvCount.setText("L: " + (left != null ? left : 0) + "  R: " + (right != null ? right : 0));
                } else {
                    tvCount.setText(String.valueOf(repCounter.getCurrentSetTotalReps()));
                }
            }
        }
    }

    private void checkHandsAboveHeadGesture(List<NormalizedLandmark> landmarks) {
        NormalizedLandmark lWrist = landmarks.get(15);
        NormalizedLandmark rWrist = landmarks.get(16);
        NormalizedLandmark lShoulder = landmarks.get(11);
        NormalizedLandmark rShoulder = landmarks.get(12);

        float visThreshold = 0.5f;
        if (lWrist.visibility().orElse(0f) > visThreshold && rWrist.visibility().orElse(0f) > visThreshold &&
                lWrist.y() < lShoulder.y() && rWrist.y() < rShoulder.y()) {
            
            if (handsAboveHeadStartTime == -1) {
                handsAboveHeadStartTime = SystemClock.elapsedRealtime();
                gestureFeedbackCard.setVisibility(View.VISIBLE);
            }
            
            long elapsed = SystemClock.elapsedRealtime() - handsAboveHeadStartTime;
            gestureProgressBar.setProgress((int) ((elapsed / (float) GESTURE_HOLD_MS) * 100));

            if (elapsed >= GESTURE_HOLD_MS) {
                onSessionComplete();
            }
        } else {
            handsAboveHeadStartTime = -1;
            gestureFeedbackCard.setVisibility(View.GONE);
            gestureProgressBar.setProgress(0);
        }
    }

    private void checkRestGestures(List<NormalizedLandmark> landmarks) {
        NormalizedLandmark lShoulder = landmarks.get(11); // Physical Left
        NormalizedLandmark rShoulder = landmarks.get(12); // Physical Right
        NormalizedLandmark lWrist = landmarks.get(15);
        NormalizedLandmark rWrist = landmarks.get(16);

        float visThreshold = 0.5f;
        if (lWrist.visibility().orElse(0f) > visThreshold && rWrist.visibility().orElse(0f) > visThreshold) {
            
            // MIRRORED VIEW LOGIC:
            // Landmark 16 (Physical Right) has smaller X than Landmark 15 (Physical Left) normally.
            // When crossed, Landmark 16.x > Landmark 15.x.
            boolean crossing = rWrist.x() > lWrist.x();
            
            // Touching opposite shoulders while crossed:
            // Physical Left Wrist (15) near Physical Right Shoulder (12)
            // Physical Right Wrist (16) near Physical Left Shoulder (11)
            double distLtoR = Math.sqrt(Math.pow(lWrist.x() - rShoulder.x(), 2) + Math.pow(lWrist.y() - rShoulder.y(), 2));
            double distRtoL = Math.sqrt(Math.pow(rWrist.x() - lShoulder.x(), 2) + Math.pow(rWrist.y() - lShoulder.y(), 2));
            
            boolean touchingShoulders = distLtoR < 0.15 && distRtoL < 0.15;
            
            if (crossing && touchingShoulders) {
                if (!repCounter.isResting()) {
                    repCounter.setResting(true);
                    speak("Set complete. Resting.");
                }
            } else if (lWrist.y() < lShoulder.y() - 0.05 || rWrist.y() < rShoulder.y() - 0.05) {
                // Resume when at least one hand is raised above shoulder level
                if (repCounter.isResting()) {
                    repCounter.setResting(false);
                    speak("Resuming workout.");
                }
            }
        }
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
        cameraExecutor.shutdown();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}
