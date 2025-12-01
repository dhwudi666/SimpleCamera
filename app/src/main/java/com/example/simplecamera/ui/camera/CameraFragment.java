package com.example.simplecamera.ui.camera;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.simplecamera.R;
import com.example.simplecamera.database.entity.MediaFile;
import com.example.simplecamera.viewmodel.CameraViewModel;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraFragment extends Fragment {
    // 当前模式：拍照或录像
    private enum CameraMode { PHOTO, VIDEO }
    private CameraMode currentMode = CameraMode.PHOTO;
    private static final String TAG = "CameraFragment";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    // 录像相关变量
    private VideoCapture<Recorder> videoCapture;
    private Recording activeRecording;
    private boolean isRecording = false;
    private long recordingStartTime = 0;
    private Handler recordingTimerHandler = new Handler();

    // UI控件
    private TextView recordingTimer;
    private ImageButton captureButton; // 拍照按钮
    private ImageButton recordButton;   // 录像按钮
    private ImageButton modeSwitchButton;
    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private ImageCapture imageCapture;
    private CameraViewModel viewModel;
    private ProcessCameraProvider cameraProvider;

    // 权限数组
    private final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    // 应用专属目录
    private File appStorageDir;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_camera, container, false);
        previewView = view.findViewById(R.id.previewView);

        // 初始化UI控件
        recordingTimer = view.findViewById(R.id.recordingTimer);
        captureButton = view.findViewById(R.id.captureButton); // 拍照按钮
        recordButton = view.findViewById(R.id.recordButton);   // 录像按钮
        modeSwitchButton = view.findViewById(R.id.modeSwitchButton);

        // 初始化应用专属目录
        initAppStorageDir();

        // 初始化ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(CameraViewModel.class);

        // 设置按钮点击事件
        captureButton.setOnClickListener(v -> takePhoto());
        view.findViewById(R.id.flipButton).setOnClickListener(v -> flipCamera());
        view.findViewById(R.id.galleryButton).setOnClickListener(v -> openGallery());

        // 录像按钮点击事件
        recordButton.setOnClickListener(v -> {
            if (currentMode == CameraMode.VIDEO) {
                toggleRecording();
            }
        });

        // 模式切换按钮
        modeSwitchButton.setOnClickListener(v -> switchCameraMode());

        cameraExecutor = Executors.newSingleThreadExecutor();

        // 初始化UI状态
        updateUIForCurrentMode();

        // 检查并请求权限
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            requestPermissions(REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
        }

        return view;
    }

    private void initAppStorageDir() {
        // 创建应用专属目录
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appStorageDir = new File(requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), "SimpleCamera");
        } else {
            appStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "SimpleCamera");
        }

        if (!appStorageDir.exists()) {
            appStorageDir.mkdirs();
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(getContext(), "Permissions not granted", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (Exception e) {
                Log.e(TAG, "Camera start failed", e);
                Toast.makeText(getContext(), "Camera start failed", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        try {
            // 解绑所有用例
            cameraProvider.unbindAll();

            // 选择摄像头方向
            boolean useFrontCamera = viewModel.isFrontCamera.getValue() != null &&
                    viewModel.isFrontCamera.getValue();
            int lensFacing = useFrontCamera ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;

            CameraSelector cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build();

            // 配置Preview - 使用与VideoCapture兼容的配置
            Preview preview = new Preview.Builder()
                    .setTargetResolution(new Size(1920, 1080)) // 设置与录像匹配的分辨率
                    .build();

            // 配置ImageCapture
            imageCapture = new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(previewView.getDisplay().getRotation())
                    .build();

            // 设置预览
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            // 根据模式绑定不同的用例组合
            if (currentMode == CameraMode.VIDEO) {
                // 录像模式：只绑定Preview和VideoCapture
                try {
                    Recorder recorder = new Recorder.Builder()
                            .setQualitySelector(QualitySelector.from(Quality.HIGHEST,
                                    FallbackStrategy.higherQualityOrLowerThan(Quality.SD)))
                            .build();
                    videoCapture = VideoCapture.withOutput(recorder);

                    // 绑定Preview和VideoCapture
                    cameraProvider.bindToLifecycle(getViewLifecycleOwner(), cameraSelector, preview, videoCapture);

                } catch (Exception e) {
                    Log.e(TAG, "VideoCapture configuration failed, falling back to photo mode", e);
                    // 如果VideoCapture配置失败，回退到拍照模式
                    cameraProvider.bindToLifecycle(getViewLifecycleOwner(), cameraSelector, preview, imageCapture);
                    videoCapture = null;
                }
            } else {
                // 拍照模式：绑定Preview和ImageCapture
                cameraProvider.bindToLifecycle(getViewLifecycleOwner(), cameraSelector, preview, imageCapture);
                videoCapture = null;
            }

            Log.d(TAG, "Camera started successfully in mode: " + currentMode);

        } catch (Exception e) {
            Log.e(TAG, "Camera binding failed", e);
            Toast.makeText(getContext(), "Camera binding failed", Toast.LENGTH_SHORT).show();

            // 尝试最基本的配置
            tryFallbackCameraConfiguration();
        }
    }
    private void tryFallbackCameraConfiguration() {
        if (cameraProvider == null) return;

        try {
            cameraProvider.unbindAll();

            // 使用最基本的配置
            CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            // 只绑定Preview，确保至少能看到画面
            cameraProvider.bindToLifecycle(getViewLifecycleOwner(), cameraSelector, preview);

            Log.w(TAG, "Using fallback camera configuration");

        } catch (Exception e) {
            Log.e(TAG, "Fallback camera configuration also failed", e);
        }
    }

    // 切换相机模式（拍照/录像）
    private void switchCameraMode() {
        if (isRecording) {
            Toast.makeText(getContext(), "Please stop recording first", Toast.LENGTH_SHORT).show();
            return;
        }

        currentMode = (currentMode == CameraMode.PHOTO) ? CameraMode.VIDEO : CameraMode.PHOTO;
        updateUIForCurrentMode();

        // 添加模式切换延迟
        previewView.postDelayed(() -> {
            // 重新绑定摄像头用例
            startCamera();
        }, 200);
    }

    // 更新UI以适应当前模式
    private void updateUIForCurrentMode() {
        if (captureButton == null || recordButton == null || modeSwitchButton == null) return;

        if (currentMode == CameraMode.VIDEO) {
            // 录像模式：隐藏拍照按钮，显示录像按钮
            captureButton.setVisibility(View.GONE);
            recordButton.setVisibility(View.VISIBLE);
            modeSwitchButton.setImageResource(R.drawable.ic_photo);
            recordingTimer.setVisibility(View.VISIBLE);
        } else {
            // 拍照模式：显示拍照按钮，隐藏录像按钮
            captureButton.setVisibility(View.VISIBLE);
            recordButton.setVisibility(View.GONE);
            modeSwitchButton.setImageResource(R.drawable.ic_video);
            recordingTimer.setVisibility(View.GONE);
        }
    }

    private void takePhoto() {
        Log.d(TAG, "takePhoto called, currentMode: " + currentMode);

        if (currentMode != CameraMode.PHOTO) {
            Log.d(TAG, "Not in photo mode, ignoring takePhoto");
            return;
        }

        if (imageCapture == null) {
            Log.e(TAG, "ImageCapture is null");
            Toast.makeText(getContext(), "Camera not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建文件名
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "IMG_" + timeStamp + ".jpg";

        // 创建输出选项
        ImageCapture.OutputFileOptions outputOptions;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用 MediaStore
            outputOptions = createMediaStoreOutputOptions(fileName);
        } else {
            // Android 9及以下使用文件保存
            outputOptions = createFileOutputOptions(fileName);
        }

        Log.d(TAG, "Taking photo with filename: " + fileName);

        // 拍照
        imageCapture.takePicture(outputOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Log.d(TAG, "Photo saved successfully");
                requireActivity().runOnUiThread(() -> {
                    try {
                        String filePath = null;
                        Uri savedUri = outputFileResults.getSavedUri();

                        if (savedUri != null) {
                            // MediaStore 方式保存
                            filePath = savedUri.toString();
                            Log.d(TAG, "Photo saved via MediaStore: " + filePath);
                        } else {
                            // 文件方式保存
                            filePath = getLatestImagePath();
                            Log.d(TAG, "Photo saved via file: " + filePath);
                        }

                        if (filePath != null) {
                            // 保存到数据库
                            MediaFile mediaFile = new MediaFile(
                                    filePath,
                                    0, // 0 for image
                                    System.currentTimeMillis(),
                                    null
                            );
                            viewModel.saveMediaFile(mediaFile);
                            Toast.makeText(getContext(), "Photo saved successfully", Toast.LENGTH_SHORT).show();
                        } else {
                            Log.e(TAG, "Photo saved but path is null");
                            Toast.makeText(getContext(), "Photo saved but path is null", Toast.LENGTH_SHORT).show();
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Error saving photo info", e);
                        Toast.makeText(getContext(), "Error saving photo info", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "Photo failed: " + exception.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    // 录像控制
    private void toggleRecording() {
        if (currentMode != CameraMode.VIDEO) {
            return;
        }

        if (!isRecording) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    private void startRecording() {
        if (videoCapture == null) {
            Toast.makeText(getContext(), "Video capture not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        // 添加短暂延迟，确保Preview稳定
        previewView.postDelayed(() -> {
            try {
                // 创建视频文件名
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "VID_" + timeStamp + ".mp4");
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/SimpleCamera");
                }

                // 创建输出选项
                MediaStoreOutputOptions outputOptions = new MediaStoreOutputOptions.Builder(
                        requireContext().getContentResolver(),
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                        .setContentValues(contentValues)
                        .build();

                // 开始录像
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED) {

                    activeRecording = videoCapture.getOutput()
                            .prepareRecording(requireContext(), outputOptions)
                            .withAudioEnabled() // 确保启用音频
                            .start(ContextCompat.getMainExecutor(requireContext()), this::handleRecordingEvent);

                    isRecording = true;
                    recordingStartTime = System.currentTimeMillis();
                    startRecordingTimer();

                    Log.d(TAG, "Recording started");

                } else {
                    Toast.makeText(getContext(), "Audio recording permission required", Toast.LENGTH_SHORT).show();
                }

            } catch (Exception e) {
                Log.e(TAG, "Start recording failed", e);
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "Start recording failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, 100); // 100ms延迟，确保Preview稳定
    }

    private void stopRecording() {
        if (activeRecording != null) {
            activeRecording.stop();
            activeRecording = null;
        }
        isRecording = false;
        stopRecordingTimer();
        Log.d(TAG, "Recording stopped");
    }

    private void handleRecordingEvent(VideoRecordEvent event) {
        requireActivity().runOnUiThread(() -> {
            if (event instanceof VideoRecordEvent.Start) {
                // 录像开始
                Log.d(TAG, "Recording started successfully");
                Toast.makeText(getContext(), "Recording started", Toast.LENGTH_SHORT).show();
                updateRecordButton(true);

            } else if (event instanceof VideoRecordEvent.Finalize) {
                // 录像结束
                VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) event;
                Log.d(TAG, "Recording finalized, hasError: " + finalizeEvent.hasError());

                updateRecordButton(false);

                if (finalizeEvent.hasError()) {
                    // 录像失败
                    int errorCode = finalizeEvent.getError();
                    String errorMessage = getErrorMessage(errorCode);
                    Log.e(TAG, "Recording failed with error: " + errorMessage);
                    Toast.makeText(getContext(), "Recording failed: " + errorMessage, Toast.LENGTH_LONG).show();

                    // 录像失败后重新绑定摄像头
                    previewView.postDelayed(() -> {
                        startCamera();
                    }, 500);
                } else {
                    // 录像成功
                    Log.d(TAG, "Recording completed successfully");
                    Toast.makeText(getContext(), "Video saved successfully", Toast.LENGTH_SHORT).show();
                    saveVideoToDatabase(finalizeEvent);
                }
            } else if (event instanceof VideoRecordEvent.Status) {
                // 录像状态更新
                VideoRecordEvent.Status statusEvent = (VideoRecordEvent.Status) event;
                Log.d(TAG, "Recording status: " + statusEvent.getRecordingStats());
            }
        });
    }
    private String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case VideoRecordEvent.Finalize.ERROR_NONE:
                return "No error";
            case VideoRecordEvent.Finalize.ERROR_UNKNOWN:
                return "Unknown error";
            case VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED:
                return "File size limit reached";
            case VideoRecordEvent.Finalize.ERROR_INVALID_OUTPUT_OPTIONS:
                return "Invalid output options";
            case VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED:
                return "Encoding failed";
            case VideoRecordEvent.Finalize.ERROR_RECORDER_ERROR:
                return "Recorder error";
            case VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA:
                return "No valid data";
            case VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE:
                return "Source inactive";
            default:
                return "Unknown error code: " + errorCode;
        }
    }

    private void saveVideoToDatabase(VideoRecordEvent.Finalize finalizeEvent) {
        try {
            Uri videoUri = finalizeEvent.getOutputResults().getOutputUri();

            if (videoUri != null) {
                MediaFile mediaFile = new MediaFile(
                        videoUri.toString(),
                        1, // 1 for video
                        System.currentTimeMillis(),
                        System.currentTimeMillis() - recordingStartTime // 视频时长
                );
                viewModel.saveMediaFile(mediaFile);
                Log.d(TAG, "Video saved to database: " + videoUri.toString());
            } else {
                Log.e(TAG, "Video URI is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving video to database", e);
        }
    }

    // 录像计时器
    private void startRecordingTimer() {
        recordingTimerHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isRecording) {
                    long elapsedTime = System.currentTimeMillis() - recordingStartTime;
                    updateTimerText(elapsedTime);
                    recordingTimerHandler.postDelayed(this, 1000); // 每秒更新
                }
            }
        }, 1000);
    }

    private void stopRecordingTimer() {
        recordingTimerHandler.removeCallbacksAndMessages(null);
        recordingTimer.setText("00:00");
    }

    private void updateTimerText(long elapsedTime) {
        long seconds = elapsedTime / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;

        String timeText = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        recordingTimer.setText(timeText);
    }

    private void updateRecordButton(boolean recording) {
        if (recordButton != null) {
            if (recording) {
                recordButton.setImageResource(R.drawable.ic_stop);
                recordButton.setBackgroundColor(0x80FF0000); // 半透明红色背景
            } else {
                recordButton.setImageResource(R.drawable.ic_video);
                recordButton.setBackgroundColor(0x00000000); // 透明背景
            }
        }
    }

    private ImageCapture.OutputFileOptions createMediaStoreOutputOptions(String fileName) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SimpleCamera");

        return new ImageCapture.OutputFileOptions.Builder(
                requireContext().getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues).build();
    }

    private ImageCapture.OutputFileOptions createFileOutputOptions(String fileName) {
        File photoFile = new File(appStorageDir, fileName);
        return new ImageCapture.OutputFileOptions.Builder(photoFile).build();
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

    private void flipCamera() {
        if (isRecording) {
            Toast.makeText(getContext(), "Please stop recording first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (viewModel.isFrontCamera.getValue() == null) {
            viewModel.isFrontCamera.setValue(false);
        }

        boolean current = viewModel.isFrontCamera.getValue();
        viewModel.isFrontCamera.setValue(!current);

        // 重新启动摄像头
        startCamera();
    }

    private void openGallery() {
        try {
            navigateToAppGallery();
        } catch (Exception e) {
            Log.e(TAG, "Error opening gallery", e);
            Toast.makeText(getContext(), "Error opening gallery", Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateToAppGallery() {
        try {
            GalleryFragment galleryFragment = new GalleryFragment();
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, galleryFragment)
                    .addToBackStack("camera")
                    .commit();
            Log.d(TAG, "Navigated to gallery successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to gallery", e);
            Toast.makeText(getContext(), "Cannot open gallery", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 停止录像
        if (isRecording) {
            stopRecording();
        }
        // 停止计时器
        recordingTimerHandler.removeCallbacksAndMessages(null);
        // 关闭执行器
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        // 解绑摄像头
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }
}