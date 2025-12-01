// java
package com.example.simplecamera.ui.camera;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.core.content.ContextCompat;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CameraController：封装摄像头初始化、绑定、拍照与录像控制。
 * 通过 Callback 将结果与错误回调给调用方（通常为 CameraFragment）。
 */
public class CameraController {
    private static final String TAG = "CameraController";

    private final Context context;
    private final LifecycleOwner lifecycleOwner;
    private final PreviewView previewView;
    private final File appStorageDir;
    private final ExecutorService cameraExecutor;

    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private VideoCapture<Recorder> videoCapture;
    private Recording activeRecording;
    private boolean isRecording = false;
    private long recordingStartTime = 0;

    private boolean useFrontCamera = false;

    private final Callback callback;

    public CameraController(Context context, LifecycleOwner lifecycleOwner, PreviewView previewView, File appStorageDir, Callback callback) {
        this.context = context.getApplicationContext();
        this.lifecycleOwner = lifecycleOwner;
        this.previewView = previewView;
        this.appStorageDir = appStorageDir;
        this.callback = callback;
        this.cameraExecutor = Executors.newSingleThreadExecutor();
    }

    public interface Callback {
        void onRecordingStarted();
        void onRecordingStopped();
        void onRecordingError(String message);
        void onPhotoSaved(String filePathOrUri);
        void onVideoSaved(String fileUri, long durationMs);
    }

    public void setUseFrontCamera(boolean useFront) {
        this.useFrontCamera = useFront;
    }

    public void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (Exception e) {
                Log.e(TAG, "Camera start failed", e);
                if (callback != null) callback.onRecordingError("Camera start failed: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        try {
            cameraProvider.unbindAll();

            int lensFacing = useFrontCamera ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
            CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();

            // 使用 AspectRatio 让 CameraX 选择更合适的分辨率，避免强制 1920x1080 导致性能问题
            Preview preview = new Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    .build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            // 对照片使用 4:3 比例（通常更节省资源），并保留最小延迟模式
            imageCapture = new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setTargetRotation(previewView.getDisplay() != null ? previewView.getDisplay().getRotation() : Surface.ROTATION_0)
                    .build();

            // 如果已经准备了 videoCapture 则一起绑定，否则只绑定 preview + imageCapture
            if (videoCapture == null) {
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture);
            } else {
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture, videoCapture);
            }

            Log.d(TAG, "Camera bound successfully (aspect ratio optimized)");
        } catch (Exception e) {
            Log.e(TAG, "Camera binding failed", e);
            if (callback != null) callback.onRecordingError("Camera binding failed: " + e.getMessage());
            tryFallbackCameraConfiguration();
        }
    }

    private void tryFallbackCameraConfiguration() {
        if (cameraProvider == null) return;
        try {
            cameraProvider.unbindAll();
            CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview);
            Log.w(TAG, "Using fallback camera configuration");
        } catch (Exception e) {
            Log.e(TAG, "Fallback camera configuration also failed", e);
            if (callback != null) callback.onRecordingError("Fallback camera configuration failed: " + e.getMessage());
        }
    }

    public void takePhoto() {
        if (imageCapture == null) {
            if (callback != null) callback.onRecordingError("ImageCapture not ready");
            return;
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "IMG_" + timeStamp + ".jpg";

        ImageCapture.OutputFileOptions outputOptions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SimpleCamera");
            outputOptions = new ImageCapture.OutputFileOptions.Builder(
                    context.getContentResolver(),
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
            ).build();
        } else {
            File photoFile = new File(appStorageDir, fileName);
            outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();
        }

        imageCapture.takePicture(outputOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                try {
                    String filePath;
                    if (outputFileResults.getSavedUri() != null) {
                        filePath = outputFileResults.getSavedUri().toString();
                    } else {
                        filePath = getLatestImagePath();
                    }
                    if (callback != null) callback.onPhotoSaved(filePath);
                } catch (Exception e) {
                    Log.e(TAG, "onImageSaved error", e);
                    if (callback != null) callback.onRecordingError("Photo saved but handling failed: " + e.getMessage());
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Photo capture failed", exception);
                if (callback != null) callback.onRecordingError("Photo failed: " + exception.getMessage());
            }
        });
    }

    public void toggleRecording() {
        if (!isRecording) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    public void startRecording() {
        if (cameraProvider == null) {
            if (callback != null) callback.onRecordingError("Camera provider not ready");
            return;
        }

        // 如果还没有 videoCapture，延迟创建 Recorder（选择较低质量以减少启动开销）
        if (videoCapture == null) {
            try {
                Recorder recorder = new Recorder.Builder()
                        // 优先使用 HD 或 SD，避免使用 Quality.HIGHEST 导致编码/初始化延迟
                        .setQualitySelector(QualitySelector.from(Quality.HD,
                                FallbackStrategy.higherQualityOrLowerThan(Quality.SD)))
                        .build();
                videoCapture = VideoCapture.withOutput(recorder);
            } catch (Exception e) {
                Log.w(TAG, "Recorder init failed", e);
                if (callback != null) callback.onRecordingError("Recorder init failed: " + e.getMessage());
                // 仍然尝试继续（不创建 videoCapture 则无法录制）
                return;
            }
            // 重新绑定用例（此时 videoCapture 已就绪）
            bindCameraUseCases();
        }

        // 准备 MediaStore 输出等（与原逻辑一致）
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "VID_" + timeStamp + ".mp4");
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/SimpleCamera");
        }

        MediaStoreOutputOptions outputOptions = new MediaStoreOutputOptions.Builder(
                context.getContentResolver(),
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues)
                .build();

        try {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                activeRecording = videoCapture.getOutput()
                        .prepareRecording(context, outputOptions)
                        .withAudioEnabled()
                        .start(ContextCompat.getMainExecutor(context), this::handleRecordingEvent);

                isRecording = true;
                recordingStartTime = System.currentTimeMillis();
                if (callback != null) callback.onRecordingStarted();
                Log.d(TAG, "Recording started");
            } else {
                if (callback != null) callback.onRecordingError("Audio permission not granted");
            }
        } catch (Exception e) {
            Log.e(TAG, "Start recording failed", e);
            if (callback != null) callback.onRecordingError("Start recording failed: " + e.getMessage());
        }
    }

    public void stopRecording() {
        try {
            if (activeRecording != null) {
                activeRecording.stop();
                activeRecording = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping recording", e);
            if (callback != null) callback.onRecordingError("Stop recording error: " + e.getMessage());
        } finally {
            isRecording = false;
            if (callback != null) callback.onRecordingStopped();
        }
    }

    private void handleRecordingEvent(VideoRecordEvent event) {
        if (event instanceof VideoRecordEvent.Finalize) {
            VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) event;
            if (finalizeEvent.hasError()) {
                String message = "Recording finalize error: " + finalizeEvent.getError();
                Log.e(TAG, message);
                if (callback != null) callback.onRecordingError(message);
                // 尝试重新绑定摄像头以恢复状态
                startCamera();
            } else {
                long duration = System.currentTimeMillis() - recordingStartTime;
                String uriStr = finalizeEvent.getOutputResults().getOutputUri() != null ?
                        finalizeEvent.getOutputResults().getOutputUri().toString() : null;
                if (uriStr != null && callback != null) {
                    callback.onVideoSaved(uriStr, duration);
                } else {
                    if (callback != null) callback.onRecordingError("Video saved but URI is null");
                }
            }
        } else if (event instanceof VideoRecordEvent.Start) {
            Log.d(TAG, "Recording event: Start");
        } else if (event instanceof VideoRecordEvent.Status) {
            // 可用于监控编码状态
            Log.d(TAG, "Recording status: " + ((VideoRecordEvent.Status) event).getRecordingStats());
        }
    }

    private String getLatestImagePath() {
        try {
            File[] files = appStorageDir.listFiles((dir, name) -> name.endsWith(".jpg"));
            if (files != null && files.length > 0) {
                File latestFile = files[0];
                for (File file : files) {
                    if (file.lastModified() > latestFile.lastModified()) {
                        latestFile = file;
                    }
                }
                return latestFile.getAbsolutePath();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting latest image path", e);
        }
        return null;
    }

    public void release() {
        try {
            if (isRecording) stopRecording();
            if (cameraExecutor != null) cameraExecutor.shutdown();
            if (cameraProvider != null) cameraProvider.unbindAll();
        } catch (Exception e) {
            Log.e(TAG, "Release error", e);
        }
    }
}
