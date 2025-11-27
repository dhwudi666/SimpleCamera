package com.example.simplecamera.ui;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.annotation.NonNull;

import com.example.simplecamera.database.entity.MediaFile;
import com.example.simplecamera.database.repository.MediaRepository;

import java.util.List;

public class GalleryViewModel extends AndroidViewModel {
    private MediaRepository repository;
    private LiveData<List<MediaFile>> allMediaFiles;

    public GalleryViewModel(@NonNull Application application) {
        super(application);
        repository = new MediaRepository(application);
        allMediaFiles = repository.getAllMediaFiles();
    }

    public LiveData<List<MediaFile>> getAllMediaFiles() {
        return allMediaFiles;
    }
}