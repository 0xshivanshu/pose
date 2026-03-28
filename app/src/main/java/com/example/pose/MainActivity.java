package com.example.pose;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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
    private Button btnEndSession;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Keep the screen on while the app is active
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Handle Window Insets
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

        // Initialize TextToSpeech
        tts = new TextToSpeech(this, this);

        repCounter = new RepCounter();
        // Voice feedback listener
        repCounter.setRepListener((exercise, count) -> {
            speak(exercise + " " + count);
        });

        // Form feedback listener
        repCounter.setFormListener(feedback -> {
            long currentTime = SystemClock.elapsedRealtime();
            if (currentTime - lastFeedbackTime > FEEDBACK_COOLDOWN_MS) {
                speak(feedback);
                lastFeedbackTime = currentTime;
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
        if (isTtsInitialized && tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.US);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language not supported");
            } else {
                isTtsInitialized = true;
            }
        } else {
            Log.e(TAG, "TTS Initialization failed");
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
                checkRestGestures(landmarks);

                repCounter.processLandmarks(landmarks);
                
                tvCount.setText(String.valueOf(repCounter.getTotalReps()));
                if (repCounter.isResting()) {
                    tvExercise.setText("RESTING...");
                    tvExercise.setTextColor(Color.YELLOW);
                } else {
                    String exercise = repCounter.getLastDetectedExercise();
                    tvExercise.setText(exercise.isEmpty() ? "Detecting..." : exercise);
                    tvExercise.setTextColor(Color.WHITE);
                }
            }
        });
    }

    private void checkRestGestures(List<NormalizedLandmark> landmarks) {
        if (landmarks.size() < 33) return;

        // Using Right Hand landmarks (16=wrist, 18=pinky, 20=index, 22=thumb)
        NormalizedLandmark rightWrist = landmarks.get(16);
        NormalizedLandmark rightShoulder = landmarks.get(12);
        NormalizedLandmark rightIndex = landmarks.get(20);
        NormalizedLandmark rightThumb = landmarks.get(22);
        NormalizedLandmark rightPinky = landmarks.get(18);

        // Visibility check
        if (rightWrist.visibility().orElse(0f) < 0.5f) return;

        boolean handRaised = rightWrist.y() < rightShoulder.y();

        // REST: Palm showing (Fingers spread)
        // Check horizontal distance between pinky and index finger
        boolean palmOpen = Math.abs(rightIndex.x() - rightPinky.x()) > 0.05f;

        if (handRaised && palmOpen && !repCounter.isResting()) {
            repCounter.setResting(true);
            speak("Resting");
        } 
        
        // RESUME: Thumbs up
        // Thumb is significantly higher than index finger
        boolean thumbsUp = rightThumb.y() < (rightIndex.y() - 0.04f);
        
        if (handRaised && thumbsUp && repCounter.isResting()) {
            repCounter.setResting(false);
            speak("Resuming");
        }
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

        if (leftWrist.visibility().orElse(0f) < 0.5f || rightWrist.visibility().orElse(0f) < 0.5f) {
            resetGestureFeedback();
            return;
        }

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
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        cameraExecutor.shutdown();
        if (poseLandmarkerHelper != null) {
            poseLandmarkerHelper.close();
        }
    }
}