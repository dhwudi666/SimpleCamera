package com.example.simplecamera.database.repository;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.example.simplecamera.database.AppDatabase;
import com.example.simplecamera.database.dao.MediaFileDao;
import com.example.simplecamera.database.entity.MediaFile;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaRepository {
    private static final String TAG = "MediaRepository";
    private final MediaFileDao mediaFileDao;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    public MediaRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        mediaFileDao = db.mediaFileDao();
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public interface MediaDeleteCallback {
        void onSuccess();
        void onError(String errorMessage);
    }


    /**
     * 批量删除媒体文件
     */
    public void deleteMediaFiles(List<MediaFile> mediaFiles, MediaDeleteCallback callback) {
        executorService.execute(() -> {
            try {
                int deletedCount = 0;
                for (MediaFile mediaFile : mediaFiles) {
                    try {
                        mediaFileDao.delete(mediaFile);
                        deletedCount++;
                        Log.d(TAG, "Deleted media file: " + mediaFile.getFilePath());
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to delete media file: " + mediaFile.getFilePath(), e);
                    }
                }

                final int finalDeletedCount = deletedCount;
                mainHandler.post(() -> {
                    if (callback != null) {
                        if (finalDeletedCount > 0) {
                            callback.onSuccess();
                        } else {
                            callback.onError("没有成功删除任何文件");
                        }
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error in batch deletion", e);
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onError("批量删除失败: " + e.getMessage());
                    }
                });
            }
        });
    }

    public LiveData<List<MediaFile>> getAllMediaFiles() {
        return mediaFileDao.getAllMediaFiles();
    }

    public void insertMediaFile(MediaFile mediaFile) {
        executorService.execute(() -> mediaFileDao.insert(mediaFile));
    }

}