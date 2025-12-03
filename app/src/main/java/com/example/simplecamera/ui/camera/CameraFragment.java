package com.example.simplecamera.ui.camera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
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
import com.example.simplecamera.viewmodel.CameraViewModel;
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

    // UI 计时器（仅用于显示）
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

        // 获取 ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(CameraViewModel.class);

        // 创建相机控制器
        cameraController = new CameraController(
                requireContext(),
                getViewLifecycleOwner(),
                previewView,
                viewModel.getAppStorageDir(),
                this
        );

        // 观察摄像头方向变化
        viewModel.isFrontCamera.observe(getViewLifecycleOwner(), isFront -> {
            cameraController.setUseFrontCamera(isFront != null && isFront);
            cameraController.startCamera();
        });

        // 观察相机模式变化
        viewModel.currentMode.observe(getViewLifecycleOwner(), mode -> {
            updateUIForCurrentMode(mode);
            if (mode == CameraViewModel.CameraMode.VIDEO) {
                previewView.postDelayed(() -> cameraController.startCamera(), 200);
            }
        });

        // 观察录制状态
        viewModel.isRecording.observe(getViewLifecycleOwner(), isRecording -> {
            if (isRecording) {
                startRecordingTimer();
                updateRecordButton(true);
            } else {
                stopRecordingTimer();
                updateRecordButton(false);
            }
        });

        // 设置点击事件
        captureButton.setOnClickListener(v -> cameraController.takePhoto());
        recordButton.setOnClickListener(v -> {
            if (viewModel.currentMode.getValue() == CameraViewModel.CameraMode.VIDEO) {
                cameraController.toggleRecording();
            }
        });
        view.findViewById(R.id.flipButton).setOnClickListener(v -> {
            viewModel.toggleCameraFacing();
        });
        view.findViewById(R.id.galleryButton).setOnClickListener(v -> openGallery());
        modeSwitchButton.setOnClickListener(v -> {
            // 检查是否正在录制
            if (viewModel.isRecording.getValue() != null && viewModel.isRecording.getValue()) {
                Toast.makeText(getContext(), "Please stop recording first", Toast.LENGTH_SHORT).show();
                return;
            }
            viewModel.toggleCameraMode();
        });

        // 检查权限
        if (viewModel.checkAllPermissions(REQUIRED_PERMISSIONS)) {
            cameraController.setUseFrontCamera(viewModel.isFrontCamera.getValue() != null && viewModel.isFrontCamera.getValue());
            cameraController.startCamera();
        } else {
            requestPermissions(REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
        }

        // 初始化 UI
        updateUIForCurrentMode(viewModel.currentMode.getValue());

        return view;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (viewModel.checkAllPermissions(REQUIRED_PERMISSIONS)) {
                cameraController.startCamera();
            } else {
                Toast.makeText(getContext(), "Permissions not granted", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateUIForCurrentMode(CameraViewModel.CameraMode mode) {
        if (captureButton == null || recordButton == null || modeSwitchButton == null) return;
        if (mode == CameraViewModel.CameraMode.VIDEO) {
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
            viewModel.startRecording();
            Toast.makeText(getContext(), "Recording started", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onRecordingStopped() {
        requireActivity().runOnUiThread(() -> {
            viewModel.stopRecording();
            Toast.makeText(getContext(), "Recording stopped", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onRecordingError(String message) {
        requireActivity().runOnUiThread(() -> {
            viewModel.stopRecording();
            Toast.makeText(getContext(), "Recording error: " + message, Toast.LENGTH_LONG).show();
            Log.e(TAG, "Recording error: " + message);
            previewView.postDelayed(() -> cameraController.startCamera(), 500);
        });
    }

    @Override
    public void onPhotoSaved(String filePathOrUri) {
        requireActivity().runOnUiThread(() -> {
            viewModel.savePhoto(filePathOrUri);
            Toast.makeText(getContext(), "Photo saved", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onVideoSaved(String fileUri, long durationMs) {
        requireActivity().runOnUiThread(() -> {
            viewModel.saveVideo(fileUri, durationMs);
            Toast.makeText(getContext(), "Video saved", Toast.LENGTH_SHORT).show();
        });
    }

    // 计时器逻辑（仅用于 UI 显示）
    private void startRecordingTimer() {
        recordingTimerHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Boolean isRecording = viewModel.isRecording.getValue();
                Long startTime = viewModel.recordingStartTime.getValue();
                if (isRecording != null && isRecording && startTime != null && startTime > 0) {
                    long elapsedTime = System.currentTimeMillis() - startTime;
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