package com.example.simplecamera.ui;

// CameraViewModel.java
import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.annotation.NonNull;

import com.example.simplecamera.database.entity.MediaFile;
import com.example.simplecamera.database.repository.MediaRepository;

public class CameraViewModel extends AndroidViewModel {
    private MediaRepository repository;
    public MutableLiveData<Boolean> isRecording = new MutableLiveData<>(false);
    public MutableLiveData<Boolean> isFrontCamera = new MutableLiveData<>(false);

    public CameraViewModel(@NonNull Application application) {
        super(application);
        repository = new MediaRepository(application);
    }

    public void saveMediaFile(MediaFile mediaFile) {
        repository.insertMediaFile(mediaFile);
    }

    public void toggleCameraFacing() {
        Boolean current = isFrontCamera.getValue();
        if (current != null) {
            isFrontCamera.setValue(!current);
        }
    }
}