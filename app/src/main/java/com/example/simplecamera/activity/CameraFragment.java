package com.example.simplecamera.activity;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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
import androidx.core.content.ContextCompat;
import androidx.camera.view.PreviewView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.simplecamera.R;
import com.example.simplecamera.database.entity.MediaFile;
import com.example.simplecamera.ui.CameraViewModel;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraFragment extends Fragment {
    private static final String TAG = "CameraFragment";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private ImageCapture imageCapture;
    private CameraViewModel viewModel;
    private ProcessCameraProvider cameraProvider;

    // 添加存储权限
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

        // 初始化应用专属目录
        initAppStorageDir();

        // 初始化ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(CameraViewModel.class);

        // 设置按钮点击事件
        view.findViewById(R.id.captureButton).setOnClickListener(v -> takePhoto());
        view.findViewById(R.id.flipButton).setOnClickListener(v -> flipCamera());
        view.findViewById(R.id.galleryButton).setOnClickListener(v -> openGallery());

        cameraExecutor = Executors.newSingleThreadExecutor();

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
            // Android 10+ 使用应用专属目录，无需权限
            appStorageDir = new File(requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), "SimpleCamera");
        } else {
            // Android 9及以下使用公共目录
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

            // 配置Preview
            Preview preview = new Preview.Builder().build();

            // 配置ImageCapture - 优化配置
            imageCapture = new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(previewView.getDisplay().getRotation())
                    .build();

            // 设置预览
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            // 绑定到生命周期
            cameraProvider.bindToLifecycle(getViewLifecycleOwner(), cameraSelector, preview, imageCapture);

            Log.d(TAG, "Camera started successfully");

        } catch (Exception e) {
            Log.e(TAG, "Camera binding failed", e);
            Toast.makeText(getContext(), "Camera binding failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void takePhoto() {
        if (imageCapture == null) {
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

        // 拍照
        imageCapture.takePicture(outputOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
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
                // 按最后修改时间排序，获取最新的文件
                File latestFile = files[0];
                for (File file : files) {
                    if (file.lastModified() > latestFile.lastModified()) {
                        latestFile = file;
                    }
                }
                // 返回文件绝对路径
                return latestFile.getAbsolutePath();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting latest image path", e);
        }
        return null;
    }

    private void flipCamera() {
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
            // 跳转到应用内的相册Fragment
            navigateToAppGallery();
        } catch (Exception e) {
            Log.e(TAG, "Error opening gallery", e);
            Toast.makeText(getContext(), "Error opening gallery", Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateToAppGallery() {
        try {
            // 创建GalleryFragment实例
            GalleryFragment galleryFragment = new GalleryFragment();

            // 使用Fragment事务进行跳转
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, galleryFragment) // 替换当前Fragment
                    .addToBackStack("camera") // 添加到返回栈，可以返回相机界面
                    .commit();

            Log.d(TAG, "Navigated to gallery successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error navigating to gallery", e);
            Toast.makeText(getContext(), "Cannot open gallery", Toast.LENGTH_SHORT).show();
        }
    }

    private void openPicturesDirectory() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = Uri.parse(appStorageDir.getAbsolutePath());
            intent.setDataAndType(uri, "resource/folder");

            if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening pictures directory", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }
}