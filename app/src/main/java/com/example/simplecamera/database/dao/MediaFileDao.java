package com.example.simplecamera.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.simplecamera.database.entity.MediaFile;

import java.util.List;

@Dao
public interface MediaFileDao {
    @Insert
    void insert(MediaFile mediaFile);

    @Query("SELECT * FROM media_files ORDER BY created_date DESC")
    LiveData<List<MediaFile>> getAllMediaFiles();

    @Query("DELETE FROM media_files WHERE file_path = :filePath")
    void deleteByFilePath(String filePath);
}