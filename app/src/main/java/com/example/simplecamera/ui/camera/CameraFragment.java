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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.simplecamera.R;
import com.example.simplecamera.database.entity.MediaFile;
import com.example.simplecamera.viewmodel.CameraViewModel;

import java.io.File;
import java.util.Locale;

public class CameraFragment extends Fragment implements CameraController.Callback {
    private static final String TAG = "CameraFragment";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private PreviewView previewView;
    private TextView recordingTimer;
    private ImageButton captureButton;
    private ImageButton recordButton;
    private ImageButton modeSwitchButton;
    private CameraViewModel viewModel;
    private CameraController cameraController;
    private File appStorageDir;
    private enum CameraMode { PHOTO, VIDEO }
    private CameraMode currentMode = CameraMode.PHOTO;

    // 计时器
    private boolean isRecording = false;
    private long recordingStartTime = 0;
    private final Handler recordingTimerHandler = new Handler();
    private final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_camera, container, false);
        previewView = view.findViewById(R.id.previewView);
        recordingTimer = view.findViewById(R.id.recordingTimer);
        captureButton = view.findViewById(R.id.captureButton);
        recordButton = view.findViewById(R.id.recordButton);
        modeSwitchButton = view.findViewById(R.id.modeSwitchButton);

        // 创建存储目录
        initAppStorageDir();
        // 获取 ViewModel（数据管理）
        viewModel = new ViewModelProvider(requireActivity()).get(CameraViewModel.class);

        // 创建相机控制器（业务逻辑）
        cameraController = new CameraController(requireContext(), getViewLifecycleOwner(), previewView, appStorageDir, this);

        // 观察摄像头方向变化，转发给 controller
        viewModel.isFrontCamera.observe(getViewLifecycleOwner(), isFront -> {
            cameraController.setUseFrontCamera(isFront != null && isFront);
            cameraController.startCamera();
        });

        // 设置点击事件
        captureButton.setOnClickListener(v -> cameraController.takePhoto());
        recordButton.setOnClickListener(v -> {
            if (currentMode == CameraMode.VIDEO) cameraController.toggleRecording();
        });
        view.findViewById(R.id.flipButton).setOnClickListener(v -> {
            viewModel.toggleCameraFacing();
        });
        view.findViewById(R.id.galleryButton).setOnClickListener(v -> openGallery());
        modeSwitchButton.setOnClickListener(v -> switchCameraMode());

        updateUIForCurrentMode();

        if (allPermissionsGranted()) {
            // 有权限：立即启动相机
            cameraController.setUseFrontCamera(viewModel.isFrontCamera.getValue() != null && viewModel.isFrontCamera.getValue());
            cameraController.startCamera();
        } else {
            // 无权限：请求权限
            requestPermissions(REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
        }

        return view;
    }

    private void initAppStorageDir() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ (API 29+)：使用应用专属目录
            appStorageDir = new File(requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), "SimpleCamera");
        } else {
            // Android 9及以下：使用公共目录
            appStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "SimpleCamera");
        }
        // 确保目录存在
        if (!appStorageDir.exists()) appStorageDir.mkdirs();
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                cameraController.startCamera();
            } else {
                Toast.makeText(getContext(), "Permissions not granted", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void switchCameraMode() {
        if (isRecording) {
            Toast.makeText(getContext(), "Please stop recording first", Toast.LENGTH_SHORT).show();
            return;
        }
        currentMode = (currentMode == CameraMode.PHOTO) ? CameraMode.VIDEO : CameraMode.PHOTO;
        updateUIForCurrentMode();
        previewView.postDelayed(() -> cameraController.startCamera(), 200);
    }

    private void updateUIForCurrentMode() {
        if (captureButton == null || recordButton == null || modeSwitchButton == null) return;
        if (currentMode == CameraMode.VIDEO) {
            captureButton.setVisibility(View.GONE);
            recordButton.setVisibility(View.VISIBLE);
            modeSwitchButton.setImageResource(R.drawable.ic_photo);
            recordingTimer.setVisibility(View.VISIBLE);
        } else {
            captureButton.setVisibility(View.VISIBLE);
            recordButton.setVisibility(View.GONE);
            modeSwitchButton.setImageResource(R.drawable.ic_video);
            recordingTimer.setVisibility(View.GONE);
        }
    }

    private void openGallery() {
        try {
            GalleryFragment galleryFragment = new GalleryFragment();
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, galleryFragment)
                    .addToBackStack("camera")
                    .commit();
        } catch (Exception e) {
            Log.e(TAG, "Error opening gallery", e);
            Toast.makeText(getContext(), "Error opening gallery", Toast.LENGTH_SHORT).show();
        }
    }

    // CameraController.Callback 实现
    @Override
    public void onRecordingStarted() {
        requireActivity().runOnUiThread(() -> {
            isRecording = true;
            recordingStartTime = System.currentTimeMillis();
            startRecordingTimer();
            updateRecordButton(true);
            Toast.makeText(getContext(), "Recording started", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onRecordingStopped() {
        requireActivity().runOnUiThread(() -> {
            isRecording = false;
            stopRecordingTimer();
            updateRecordButton(false);
            Toast.makeText(getContext(), "Recording stopped", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onRecordingError(String message) {
        requireActivity().runOnUiThread(() -> {
            stopRecordingTimer();
            isRecording = false;
            updateRecordButton(false);
            Toast.makeText(getContext(), "Recording error: " + message, Toast.LENGTH_LONG).show();
            Log.e(TAG, "Recording error: " + message);
            // 如果需要可以尝试重新启动摄像头
            previewView.postDelayed(() -> cameraController.startCamera(), 500);
        });
    }

    @Override
    public void onPhotoSaved(String filePathOrUri) {
        requireActivity().runOnUiThread(() -> {
            if (filePathOrUri != null) {
                MediaFile mediaFile = new MediaFile(filePathOrUri, 0, System.currentTimeMillis(), null);
                viewModel.saveMediaFile(mediaFile);
                Toast.makeText(getContext(), "Photo saved", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Photo saved but path is null", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onVideoSaved(String fileUri, long durationMs) {
        requireActivity().runOnUiThread(() -> {
            if (fileUri != null) {
                MediaFile mediaFile = new MediaFile(fileUri, 1, System.currentTimeMillis(), durationMs);
                viewModel.saveMediaFile(mediaFile);
                Toast.makeText(getContext(), "Video saved", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Video saved but uri is null", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 计时器逻辑（UI）
    private void startRecordingTimer() {
        recordingTimerHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isRecording) {
                    long elapsedTime = System.currentTimeMillis() - recordingStartTime;
                    updateTimerText(elapsedTime);
                    recordingTimerHandler.postDelayed(this, 1000);
                }
            }
        }, 1000);
    }

    private void stopRecordingTimer() {
        recordingTimerHandler.removeCallbacksAndMessages(null);
        if (recordingTimer != null) recordingTimer.setText("00:00");
    }

    private void updateTimerText(long elapsedTime) {
        long seconds = elapsedTime / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        String timeText = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        if (recordingTimer != null) recordingTimer.setText(timeText);
    }

    private void updateRecordButton(boolean recording) {
        if (recordButton != null) {
            if (recording) {
                recordButton.setImageResource(R.drawable.ic_stop);
                recordButton.setBackgroundColor(0x80FF0000);
            } else {
                recordButton.setImageResource(R.drawable.ic_video);
                recordButton.setBackgroundColor(0x00000000);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraController != null) cameraController.release();
        recordingTimerHandler.removeCallbacksAndMessages(null);
    }
}
