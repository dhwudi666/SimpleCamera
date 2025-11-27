package com.example.simplecamera.database.repository;

import android.app.Application;
import androidx.lifecycle.LiveData;

import com.example.simplecamera.database.AppDatabase;
import com.example.simplecamera.database.dao.MediaFileDao;
import com.example.simplecamera.database.entity.MediaFile;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaRepository {
    private MediaFileDao mediaFileDao;
    private ExecutorService executorService;

    public MediaRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        mediaFileDao = db.mediaFileDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    public void insertMediaFile(MediaFile mediaFile) {
        executorService.execute(() -> mediaFileDao.insert(mediaFile));
    }

    public LiveData<List<MediaFile>> getAllMediaFiles() {
        return mediaFileDao.getAllMediaFiles();
    }
}