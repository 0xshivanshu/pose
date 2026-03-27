package com.example.pose;

import android.content.Context;
import android.os.SystemClock;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

public class PoseLandmarkerHelper {
    private static final String TAG = "PoseLandmarkerHelper";
    private static final String MP_POSE_LANDMARKER_TASK = "pose_landmarker_lite.task";

    private final Context context;
    private final LandmarkerListener listener;
    private PoseLandmarker poseLandmarker;

    public interface LandmarkerListener {
        void onError(String error);
        void onResults(PoseLandmarkerResult result, MPImage image);
    }

    public PoseLandmarkerHelper(Context context, LandmarkerListener listener) {
        this.context = context;
        this.listener = listener;
        setupPoseLandmarker();
    }

    private void setupPoseLandmarker() {
        BaseOptions.Builder baseOptionsBuilder = BaseOptions.builder()
                .setDelegate(Delegate.GPU)
                .setModelAssetPath(MP_POSE_LANDMARKER_TASK);

        try {
            PoseLandmarker.PoseLandmarkerOptions options = PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptionsBuilder.build())
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setMinPoseDetectionConfidence(0.7f)
                    .setMinPosePresenceConfidence(0.7f)
                    .setMinTrackingConfidence(0.7f)
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(error -> listener.onError(error.getMessage()))
                    .build();

            poseLandmarker = PoseLandmarker.createFromOptions(context, options);
        } catch (IllegalStateException e) {
            listener.onError("Pose Landmarker failed to initialize. See error logs for details");
        }
    }

    public void detectLiveStream(android.graphics.Bitmap bitmap) {
        if (poseLandmarker == null) return;

        long frameTime = SystemClock.uptimeMillis();
        MPImage mpImage = new BitmapImageBuilder(bitmap).build();
        poseLandmarker.detectAsync(mpImage, frameTime);
    }

    private void returnLivestreamResult(PoseLandmarkerResult result, MPImage image) {
        listener.onResults(result, image);
    }

    public void close() {
        if (poseLandmarker != null) {
            poseLandmarker.close();
        }
    }
}
