package com.example.simplecamera.activity;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.VideoCapture;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.camera.view.PreviewView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.simplecamera.R;
import com.example.simplecamera.database.entity.MediaFile;
import com.example.simplecamera.ui.CameraViewModel;
import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraFragment extends Fragment {
    private static final String TAG = "CameraFragment";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private PreviewView previewView;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ExecutorService cameraExecutor;
    private ImageCapture imageCapture;
    private VideoCapture<Recorder> videoCapture;
    private CameraViewModel viewModel;

    // 权限数组
    private final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_camera, container, false);
        previewView = view.findViewById(R.id.previewView);

        // 初始化ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(CameraViewModel.class);

        // 设置按钮点击事件
        view.findViewById(R.id.captureButton).setOnClickListener(v -> takePhoto());
        view.findViewById(R.id.recordButton).setOnClickListener(v -> toggleRecording());
        view.findViewById(R.id.flipButton).setOnClickListener(v -> flipCamera());
        view.findViewById(R.id.galleryButton).setOnClickListener(v -> openGallery());

        cameraExecutor = Executors.newSingleThreadExecutor();
        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());

        // 检查并请求权限
        if (allPermissionsGranted()) {
            initializeCamera();
        } else {
            requestPermissions(REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
        }

        return view;
    }

    // 检查所有权限是否已授予
    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    // 处理权限请求结果
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                initializeCamera();
            } else {
                Toast.makeText(getContext(), "Permissions not granted", Toast.LENGTH_SHORT).show();
                requireActivity().finish();
            }
        }
    }

    // 初始化相机
    private void initializeCamera() {
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera initialization failed", e);
                Toast.makeText(getContext(), "Camera initialization failed", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void bindCameraUseCases(ProcessCameraProvider cameraProvider) {
        // 检查权限
        if (!allPermissionsGranted()) {
            return;
        }

        // 配置Preview
        Preview preview = new Preview.Builder().build();

        // 配置ImageCapture
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        // 配置VideoCapture
        Recorder recorder = new Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build();
        videoCapture = VideoCapture.withOutput(recorder);

        // 选择摄像头方向
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(viewModel.isFrontCamera.getValue() != null && viewModel.isFrontCamera.getValue() ?
                        CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK)
                .build();

        try {
            // 解绑所有用例
            cameraProvider.unbindAll();

            // 绑定用例到生命周期
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, videoCapture);

            // 设置预览
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

        } catch (Exception e) {
            Log.e(TAG, "Camera binding failed", e);
            Toast.makeText(getContext(), "Camera binding failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void takePhoto() {
        if (imageCapture == null) {
            Toast.makeText(getContext(), "Camera not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建文件名
        String name = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(System.currentTimeMillis());

        // 创建内容值
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_" + name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");

        // 如果是Android 10及以上，需要添加RELATIVE_PATH
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SimpleCamera");
        }

        // 创建输出选项
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(
                requireContext().getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues).build();

        // 拍摄照片
        imageCapture.takePicture(outputOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Photo saved successfully", Toast.LENGTH_SHORT).show();

                    if (outputFileResults.getSavedUri() != null) {
                        // 保存到数据库
                        MediaFile mediaFile = new MediaFile(
                                outputFileResults.getSavedUri().toString(),
                                0, // 0 for image
                                System.currentTimeMillis(),
                                null
                        );
                        viewModel.saveMediaFile(mediaFile);
                    }
                });
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "Photo capture failed: " + exception.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void toggleRecording() {
        // 视频录制功能占位实现
        Toast.makeText(getContext(), "Video recording not implemented yet", Toast.LENGTH_SHORT).show();
    }

    private void flipCamera() {
        if (viewModel.isFrontCamera.getValue() == null) {
            viewModel.isFrontCamera.setValue(false);
        }

        // 切换摄像头方向
        viewModel.isFrontCamera.setValue(!viewModel.isFrontCamera.getValue());

        // 重新绑定相机
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera flip failed", e);
                Toast.makeText(getContext(), "Failed to flip camera", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void openGallery() {
        // 跳转到相册Fragment
        Toast.makeText(getContext(), "Opening gallery", Toast.LENGTH_SHORT).show();
        // 这里需要根据您的实际Fragment类名进行调整
        // requireActivity().getSupportFragmentManager().beginTransaction()
        //         .replace(R.id.fragment_container, new GalleryFragment())
        //         .addToBackStack(null)
        //         .commit();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}