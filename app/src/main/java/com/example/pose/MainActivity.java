package com.example.pose;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.util.List;
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
    private PoseLandmarkerHelper poseLandmarkerHelper;
    private RepCounter repCounter;
    private ExecutorService cameraExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewFinder = findViewById(R.id.viewFinder);
        overlayView = findViewById(R.id.overlayView);
        tvCount = findViewById(R.id.tvCount);

        repCounter = new RepCounter();
        poseLandmarkerHelper = new PoseLandmarkerHelper(this, this);
        cameraExecutor = Executors.newSingleThreadExecutor();

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
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
                    Bitmap bitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(image.getPlanes()[0].getBuffer());

                    // Rotate bitmap to upright orientation
                    Matrix matrix = new Matrix();
                    matrix.postRotate(image.getImageInfo().getRotationDegrees());
                    
                    // Flip horizontally if using front camera to match user perspective
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
        runOnUiThread(() -> {
            // Updated to pass image dimensions as required by OverlayView.setResults
            if (overlayView != null) {
                overlayView.setResults(result, image.getWidth(), image.getHeight());
            }

            if (result.landmarks() != null && !result.landmarks().isEmpty()) {
                List<NormalizedLandmark> landmarks = result.landmarks().get(0);
                
                // Use landmarks with higher visibility for bicep curl (11, 13, 15)
                if (landmarks.size() > 15) {
                    NormalizedLandmark shoulder = landmarks.get(11);
                    NormalizedLandmark elbow = landmarks.get(13);
                    NormalizedLandmark wrist = landmarks.get(15);

                    // Use visibility() for better detection accuracy (standard for Pose Landmarker)
                    if (shoulder.visibility().orElse(0f) > 0.5f && 
                        elbow.visibility().orElse(0f) > 0.5f && 
                        wrist.visibility().orElse(0f) > 0.5f) {
                        
                        double angle = ExerciseUtils.calculateAngle(shoulder, elbow, wrist);
                        int count = repCounter.onNewAngle(angle);
                        tvCount.setText("Reps: " + count);
                    }
                }
            }
        });
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
        poseLandmarkerHelper.close();
    }
}
