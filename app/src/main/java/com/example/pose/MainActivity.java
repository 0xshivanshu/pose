package com.example.pose;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
    
    // Rest Timer UI
    private View restTimerContainer;
    private TextView tvRestTimer;
    private long restStartTime = 0;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (repCounter != null && repCounter.isResting() && restStartTime > 0) {
                long elapsedSeconds = (SystemClock.elapsedRealtime() - restStartTime) / 1000;
                long minutes = elapsedSeconds / 60;
                long seconds = elapsedSeconds % 60;
                tvRestTimer.setText(String.format(Locale.US, "%02d:%02d", minutes, seconds));
                timerHandler.postDelayed(this, 1000);
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
    private static final long GESTURE_HOLD_MS = 3000;
    private int completedSetsCount = 0;

    // Bitmap reuse for latency
    private Bitmap sharedBitmap = null;

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

        repCounter = new RepCounter();
        repCounter.setRepListener((exercise, count) -> {
            // SWAPPED L/R TTS
            if (exercise.equals(RepCounter.BICEP_CURL_LEFT)) {
                speak("Right " + count);
            } else if (exercise.equals(RepCounter.BICEP_CURL_RIGHT)) {
                speak("Left " + count);
            } else {
                speak(String.valueOf(count));
            }
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
                runOnUiThread(() -> {
                    restTimerContainer.setVisibility(View.GONE);
                    timerHandler.removeCallbacks(timerRunnable);
                });
            }

            @Override
            public void onSetFinished(WorkoutSet set) {
                completedSetsCount++;
                speak("Set finished. " + set.getTotalReps() + " reps");
                
                // Water reminder logic
                if (completedSetsCount % 2 == 0) {
                    speak("Great job! Don't forget to drink some water.");
                }
                
                runOnUiThread(() -> {
                    restTimerContainer.setVisibility(View.VISIBLE);
                    restStartTime = SystemClock.elapsedRealtime();
                    timerHandler.post(timerRunnable);
                });
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
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, null); 
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
        completedSetsCount = 0;
    }

    private void onSessionComplete() {
        if (!isSessionActive) return;
        isSessionActive = false;
        
        // Save current set if it has reps
        repCounter.finishCurrentSet();

        // Run saving logic in background thread to avoid ANR/Crash
        new Thread(() -> {
            long durationSeconds = (SystemClock.elapsedRealtime() - sessionStartTime) / 1000;
            Map<String, Integer> totalCounts = repCounter.getTotalCounts();
            List<WorkoutSet> completedSets = repCounter.getCompletedSets();

            ExerciseSession session = new ExerciseSession(totalCounts, completedSets, durationSeconds);
            WorkoutManager.saveSession(this, session);

            runOnUiThread(() -> {
                if (poseLandmarkerHelper != null) {
                    poseLandmarkerHelper.close();
                }
                
                Intent intent = new Intent(this, SummaryActivity.class);
                intent.putExtra("EXERCISE_SESSION", session);
                startActivity(intent);
                finish();
            });
        }).start();
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

                    if (sharedBitmap == null || sharedBitmap.getWidth() != image.getWidth() || sharedBitmap.getHeight() != image.getHeight()) {
                        sharedBitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
                    }
                    
                    sharedBitmap.copyPixelsFromBuffer(image.getPlanes()[0].getBuffer());

                    Matrix matrix = new Matrix();
                    matrix.postRotate(image.getImageInfo().getRotationDegrees());
                    matrix.postScale(-1f, 1f, (float) image.getWidth() / 2f, (float) image.getHeight() / 2f);

                    Bitmap processedBitmap = Bitmap.createBitmap(sharedBitmap, 0, 0, sharedBitmap.getWidth(), sharedBitmap.getHeight(), matrix, false);

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
                    // SWAPPED L/R UI
                    Integer leftVal = counts.getOrDefault(RepCounter.BICEP_CURL_LEFT, 0);
                    Integer rightVal = counts.getOrDefault(RepCounter.BICEP_CURL_RIGHT, 0);
                    tvCount.setText("L: " + (rightVal != null ? rightVal : 0) + "  R: " + (leftVal != null ? leftVal : 0));
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
            
            // MIRRORED VIEW LOGIC (Corrected for mirrored X coordinates)
            // Normal: 16.x < 15.x
            // Crossed: 16.x > 15.x
            boolean crossing = rWrist.x() > lWrist.x();
            
            // Proximity check: Physical Left Wrist (15) near Physical Right Shoulder (12)
            // Physical Right Wrist (16) near Physical Left Shoulder (11)
            // Increased distance slightly to 0.10 for comfort, but removed vertical limit
            double distLtoR = Math.sqrt(Math.pow(lWrist.x() - rShoulder.x(), 2) + Math.pow(lWrist.y() - rShoulder.y(), 2));
            double distRtoL = Math.sqrt(Math.pow(rWrist.x() - lShoulder.x(), 2) + Math.pow(rWrist.y() - lShoulder.y(), 2));
            
            boolean touchingShoulders = distLtoR < 0.10 && distRtoL < 0.10;
            
            if (crossing && touchingShoulders) {
                if (!repCounter.isResting()) {
                    repCounter.setResting(true);
                    speak("Set complete. Resting.");
                }
            } else if (lWrist.y() < lShoulder.y() - 0.05 || rWrist.y() < rShoulder.y() - 0.05) {
                // Resume when hands are raised significantly above shoulders
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
        timerHandler.removeCallbacks(timerRunnable);
    }
}
