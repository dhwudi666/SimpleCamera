package com.example.simplecamera.viewmodel;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.simplecamera.database.entity.MediaFile;
import com.example.simplecamera.database.repository.MediaRepository;
import java.util.List;

public class GalleryViewModel extends AndroidViewModel {
    private static final String TAG = "GalleryViewModel";

    private MediaRepository repository;
    private LiveData<List<MediaFile>> allMediaFiles;
    private MutableLiveData<String> operationStatus = new MutableLiveData<>("");
    private MutableLiveData<Boolean> isDeleting = new MutableLiveData<>(false);

    public GalleryViewModel(@NonNull Application application) {
        super(application);
        repository = new MediaRepository(application);
        allMediaFiles = repository.getAllMediaFiles();
    }

    public LiveData<List<MediaFile>> getAllMediaFiles() {
        return allMediaFiles;
    }

    public LiveData<String> getOperationStatus() {
        return operationStatus;
    }

    /**
     * 获取删除状态
     */
    public LiveData<Boolean> getIsDeleting() {
        return isDeleting;
    }

    /**
     * 批量删除媒体文件
     */
    public void deleteMediaFiles(List<MediaFile> mediaFiles) {
        if (mediaFiles == null || mediaFiles.isEmpty()) {
            operationStatus.postValue("没有选择要删除的文件");
            return;
        }

        isDeleting.postValue(true);
        operationStatus.postValue("正在删除 " + mediaFiles.size() + " 个文件...");

        repository.deleteMediaFiles(mediaFiles, new MediaRepository.MediaDeleteCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Successfully deleted " + mediaFiles.size() + " media files");
                operationStatus.postValue("成功删除 " + mediaFiles.size() + " 个文件");
                isDeleting.postValue(false);
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Failed to delete media files: " + errorMessage);
                operationStatus.postValue("删除失败: " + errorMessage);
                isDeleting.postValue(false);
            }
        });
    }
}