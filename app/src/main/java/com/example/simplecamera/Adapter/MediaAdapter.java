package com.example.simplecamera.Adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.simplecamera.R;
import com.example.simplecamera.database.entity.MediaFile;

import java.io.File;
import java.util.List;

public class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.ViewHolder> {
    private List<MediaFile> mediaFiles;

    public MediaAdapter(List<MediaFile> mediaFiles) {
        this.mediaFiles = mediaFiles;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_media, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MediaFile mediaFile = mediaFiles.get(position);

        // 使用Glide加载图片或视频缩略图
        if (mediaFile.fileType == 0) { // Image
            Glide.with(holder.itemView.getContext())
                    .load(new File(mediaFile.filePath))
                    .into(holder.imageView);
        } else { // Video
            // 加载视频第一帧作为缩略图
            Glide.with(holder.itemView.getContext())
                    .load(new File(mediaFile.filePath))
                    .into(holder.imageView);
        }
    }

    @Override
    public int getItemCount() {
        return mediaFiles.size();
    }

    public void setMediaFiles(List<MediaFile> mediaFiles) {
        this.mediaFiles = mediaFiles;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.mediaImage);
        }
    }
}