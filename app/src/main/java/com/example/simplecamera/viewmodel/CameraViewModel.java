package com.example.simplecamera.viewmodel;

import android.app.Application;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import com.example.simplecamera.database.entity.MediaFile;
import com.example.simplecamera.database.repository.MediaRepository;
import java.io.File;

public class CameraViewModel extends AndroidViewModel {
    private MediaRepository repository;

    // UI 状态
    public MutableLiveData<Boolean> isFrontCamera = new MutableLiveData<>(false);
    public MutableLiveData<CameraMode> currentMode = new MutableLiveData<>(CameraMode.PHOTO);
    public MutableLiveData<Boolean> isRecording = new MutableLiveData<>(false);
    public MutableLiveData<Long> recordingStartTime = new MutableLiveData<>(0L);

    // 权限状态
    public MutableLiveData<Boolean> hasAllPermissions = new MutableLiveData<>(false);

    // 存储目录
    private File appStorageDir;

    public enum CameraMode {
        PHOTO, VIDEO
    }

    public CameraViewModel(@NonNull Application application) {
        super(application);
        repository = new MediaRepository(application);
        initAppStorageDir();
    }

    /**
     * 初始化存储目录
     */
    private void initAppStorageDir() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appStorageDir = new File(getApplication().getExternalFilesDir(Environment.DIRECTORY_PICTURES), "SimpleCamera");
        } else {
            appStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "SimpleCamera");
        }
        if (!appStorageDir.exists()) {
            appStorageDir.mkdirs();
        }
    }

    /**
     * 获取存储目录
     */
    public File getAppStorageDir() {
        return appStorageDir;
    }

    /**
     * 检查所有必需权限
     */
    public boolean checkAllPermissions(String[] requiredPermissions) {
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(getApplication(), permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 切换摄像头方向
     */
    public void toggleCameraFacing() {
        Boolean current = isFrontCamera.getValue();
        if (current != null) {
            isFrontCamera.setValue(!current);
        }
    }

    /**
     * 切换相机模式
     */
    public void toggleCameraMode() {
        CameraMode current = currentMode.getValue();
        if (current == CameraMode.PHOTO) {
            currentMode.setValue(CameraMode.VIDEO);
        } else {
            currentMode.setValue(CameraMode.PHOTO);
        }
    }

    /**
     * 保存照片到数据库
     */
    public void savePhoto(String filePathOrUri) {
        if (filePathOrUri != null) {
            MediaFile mediaFile = new MediaFile(filePathOrUri, 0, System.currentTimeMillis(), null);
            repository.insertMediaFile(mediaFile);
        }
    }

    /**
     * 保存视频到数据库
     */
    public void saveVideo(String fileUri, long durationMs) {
        if (fileUri != null) {
            MediaFile mediaFile = new MediaFile(fileUri, 1, System.currentTimeMillis(), durationMs);
            repository.insertMediaFile(mediaFile);
        }
    }

    /**
     * 开始录制
     */
    public void startRecording() {
        isRecording.setValue(true);
        recordingStartTime.setValue(System.currentTimeMillis());
    }

    /**
     * 停止录制
     */
    public void stopRecording() {
        isRecording.setValue(false);
        recordingStartTime.setValue(0L);
    }
}