package com.example.simplecamera.database.entity;
// MediaFile.java
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "media_files")
public class MediaFile {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "file_path")
    public String filePath;

    @ColumnInfo(name = "file_type") // 0 for image, 1 for video
    public int fileType;

    @ColumnInfo(name = "created_date")
    public long createdDate;

    @ColumnInfo(name = "duration") // for video, in milliseconds
    public Long duration;

    // 空构造函数，Room所需
    public MediaFile() {}

    // 便捷构造函数
    @Ignore
    public MediaFile(String filePath, int fileType, long createdDate, Long duration) {
        this.filePath = filePath;
        this.fileType = fileType;
        this.createdDate = createdDate;
        this.duration = duration;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public int getFileType() { return fileType; }
    public void setFileType(int fileType) { this.fileType = fileType; }

    public long getCreatedDate() { return createdDate; }
    public void setCreatedDate(long createdDate) { this.createdDate = createdDate; }

    public Long getDuration() { return duration; }
    public void setDuration(Long duration) { this.duration = duration; }
}