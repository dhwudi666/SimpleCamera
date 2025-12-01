// java
package com.example.simplecamera.ui.camera;

import android.content.Context;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import java.io.File;

/**
 * CameraController：封装摄像头初始化、绑定、录像控制等逻辑的骨架类。
 * 当前仅提供方法签名与必要字段，后续可逐步把 CameraFragment 中的实现迁移过来。
 */
public class CameraController {
    private final Context context;
    private final LifecycleOwner lifecycleOwner;
    private final PreviewView previewView;
    private final File appStorageDir;

    // 状态/回调（可根据需要扩展）
    public CameraController(Context context, LifecycleOwner lifecycleOwner, PreviewView previewView, File appStorageDir) {
        this.context = context;
        this.lifecycleOwner = lifecycleOwner;
        this.previewView = previewView;
        this.appStorageDir = appStorageDir;
    }

    // 启动摄像头（等同于 CameraFragment.startCamera）
    public void startCamera() {
        // 暂空实现，迁移时填充原有逻辑或调用 CameraFragment 中的适配代码
    }

    // 重新绑定用例（等同于 bindCameraUseCases）
    public void bindCameraUseCases() {
    }

    // 录像控制（开始/停止）
    public void startRecording() {
    }

    public void stopRecording() {
    }

    public void toggleRecording() {
    }

    // 切换模式或摄像头方向
    public void switchCameraMode() {
    }

    public void flipCamera() {
    }

    // 释放资源（Fragment.onDestroy 中调用）
    public void release() {
    }

    // 可扩展：设置回调接口以通知 Fragment 事件（录制开始/结束/错误等）
    public interface Callback {
        void onRecordingStarted();
        void onRecordingStopped();
        void onRecordingError(String message);
    }
}
