package com.example.simplecamera.Adapter;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
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

        if (mediaFile != null && mediaFile.getFilePath() != null) {
            String filePath = mediaFile.getFilePath();
            Log.d("MediaAdapter", "Loading media file: " + filePath);

            // 处理文件路径
            if (filePath.startsWith("content://")) {
                // MediaStore URI
                loadMediaStoreUri(holder, Uri.parse(filePath));
            } else if (filePath.startsWith("file://")) {
                // 文件URI
                loadFileUri(holder, filePath.replace("file://", ""));
            } else {
                // 直接文件路径
                loadFileUri(holder, filePath);
            }

            // 设置点击事件
            holder.itemView.setOnClickListener(v -> {
                if (onItemClickListener != null) {
                    onItemClickListener.onItemClick(mediaFile);
                }
            });
        } else {
            // 显示错误图标
            holder.imageView.setImageResource(R.drawable.ic_error);
            Log.e("MediaAdapter", "MediaFile or filePath is null");
        }
    }

    private void loadMediaStoreUri(ViewHolder holder, Uri uri) {
        Glide.with(holder.itemView.getContext())
                .load(uri)
                .placeholder(R.drawable.ic_placeholder)
                .error(R.drawable.ic_error)
                .addListener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        Log.e("MediaAdapter", "Failed to load MediaStore URI: " + uri, e);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        Log.d("MediaAdapter", "Successfully loaded MediaStore URI: " + uri);
                        return false;
                    }
                })
                .centerCrop()
                .into(holder.imageView);
    }

    private void loadFileUri(ViewHolder holder, String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            Log.d("MediaAdapter", "Loading file from path: " + filePath + ", size: " + file.length());
            Glide.with(holder.itemView.getContext())
                    .load(file)
                    .placeholder(R.drawable.ic_placeholder)
                    .error(R.drawable.ic_error)
                    .addListener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            Log.e("MediaAdapter", "Failed to load file: " + filePath, e);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            Log.d("MediaAdapter", "Successfully loaded file: " + filePath);
                            return false;
                        }
                    })
                    .centerCrop()
                    .into(holder.imageView);
        } else {
            // 文件不存在，显示错误图标
            holder.imageView.setImageResource(R.drawable.ic_error);
            Log.e("MediaAdapter", "File does not exist: " + filePath);
        }
    }

    @Override
    public int getItemCount() {
        return mediaFiles != null ? mediaFiles.size() : 0;
    }

    public void setMediaFiles(List<MediaFile> mediaFiles) {
        this.mediaFiles = mediaFiles;
        notifyDataSetChanged();
    }

    // Item点击监听接口
    public interface OnItemClickListener {
        void onItemClick(MediaFile mediaFile);
    }

    private OnItemClickListener onItemClickListener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.mediaImage);
        }
    }
}