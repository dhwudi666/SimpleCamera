package com.example.simplecamera.adapter;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.ViewHolder> {
    private static final String TAG = "MediaAdapter";

    private List<MediaFile> mediaFiles;
    private Set<Integer> selectedPositions;
    private boolean isSelectionMode = false;

    // 监听器接口
    public interface OnItemClickListener {
        void onItemClick(MediaFile mediaFile);
    }

    public interface OnSelectionModeChangeListener {
        void onSelectionModeChanged(boolean isSelectionMode);
        void onSelectionChanged(int selectedCount);
    }

    private OnItemClickListener onItemClickListener;
    private OnSelectionModeChangeListener onSelectionModeChangeListener;

    public MediaAdapter(List<MediaFile> mediaFiles) {
        this.mediaFiles = mediaFiles != null ? new ArrayList<>(mediaFiles) : new ArrayList<>();
        this.selectedPositions = new HashSet<>();
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
        if (position < 0 || position >= mediaFiles.size()) {
            Log.e(TAG, "Invalid position: " + position);
            return;
        }

        MediaFile mediaFile = mediaFiles.get(position);
        if (mediaFile == null) {
            Log.e(TAG, "MediaFile is null at position: " + position);
            holder.imageView.setImageResource(R.drawable.ic_error);
            return;
        }

        // 绑定数据
        bindMediaFile(holder, mediaFile, position);

        // 设置选择状态
        updateSelectionState(holder, position);

        // 设置点击事件
        setupClickListeners(holder, position, mediaFile);
    }

    /**
     * 绑定媒体文件数据
     */
    private void bindMediaFile(ViewHolder holder, MediaFile mediaFile, int position) {
        String filePath = mediaFile.getFilePath();
        if (filePath == null) {
            Log.e(TAG, "File path is null for media file at position: " + position);
            holder.imageView.setImageResource(R.drawable.ic_error);
            return;
        }

        Log.d(TAG, "Loading media file: " + filePath);

        if (filePath.startsWith("content://")) {
            // MediaStore URI
            loadMediaStoreUri(holder, Uri.parse(filePath));
        } else {
            // 文件路径
            String actualPath = filePath.startsWith("file://") ?
                    filePath.replace("file://", "") : filePath;
            loadFileUri(holder, actualPath);
        }

        // 设置文件类型标识
        setupFileTypeIndicator(holder, mediaFile);
    }

    /**
     * 加载MediaStore URI
     */
    private void loadMediaStoreUri(ViewHolder holder, Uri uri) {
        Glide.with(holder.itemView.getContext())
                .load(uri)
                .placeholder(R.drawable.ic_placeholder)
                .error(R.drawable.ic_error)
                .addListener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        Log.e(TAG, "Failed to load MediaStore URI: " + uri, e);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        Log.d(TAG, "Successfully loaded MediaStore URI: " + uri);
                        return false;
                    }
                })
                .centerCrop()
                .into(holder.imageView);
    }

    /**
     * 加载文件URI
     */
    private void loadFileUri(ViewHolder holder, String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            Log.d(TAG, "Loading file from path: " + filePath + ", size: " + file.length());
            Glide.with(holder.itemView.getContext())
                    .load(file)
                    .placeholder(R.drawable.ic_placeholder)
                    .error(R.drawable.ic_error)
                    .addListener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            Log.e(TAG, "Failed to load file: " + filePath, e);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            Log.d(TAG, "Successfully loaded file: " + filePath);
                            return false;
                        }
                    })
                    .centerCrop()
                    .into(holder.imageView);
        } else {
            // 文件不存在，显示错误图标
            holder.imageView.setImageResource(R.drawable.ic_error);
            Log.e(TAG, "File does not exist: " + filePath);
        }
    }

    /**
     * 设置文件类型标识
     */
    private void setupFileTypeIndicator(ViewHolder holder, MediaFile mediaFile) {
        if (holder.videoIcon != null) {
            if (mediaFile.getFileType() == 1) { // 视频
                holder.videoIcon.setVisibility(View.VISIBLE);
            } else { // 图片
                holder.videoIcon.setVisibility(View.GONE);
            }
        }

        if (holder.fileTypeText != null) {
            if (mediaFile.getFileType() == 1) { // 视频
                holder.fileTypeText.setVisibility(View.VISIBLE);
                holder.fileTypeText.setText("视频");
            } else { // 图片
                holder.fileTypeText.setVisibility(View.GONE);
            }
        }
    }

    /**
     * 更新选择状态
     */
    private void updateSelectionState(ViewHolder holder, int position) {
        if (holder.checkBox == null) {
            Log.e(TAG, "CheckBox is null in ViewHolder");
            return;
        }

        if (isSelectionMode) {
            holder.checkBox.setVisibility(View.VISIBLE);
            holder.checkBox.setChecked(selectedPositions.contains(position));

            // 添加选中效果
            if (selectedPositions.contains(position)) {
                holder.imageView.setAlpha(0.7f);
            } else {
                holder.imageView.setAlpha(1.0f);
            }
        } else {
            holder.checkBox.setVisibility(View.GONE);
            holder.imageView.setAlpha(1.0f);
        }
    }

    /**
     * 设置点击事件
     */
    private void setupClickListeners(ViewHolder holder, int position, MediaFile mediaFile) {
        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                // 选择模式下，点击切换选中状态
                toggleSelection(position);
                notifyItemChanged(position);

                // 通知选择变化
                if (onSelectionModeChangeListener != null) {
                    onSelectionModeChangeListener.onSelectionChanged(getSelectedCount());
                }
            } else {
                // 正常模式下，点击查看详情
                if (onItemClickListener != null) {
                    onItemClickListener.onItemClick(mediaFile);
                }
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (!isSelectionMode) {
                // 进入选择模式
                setSelectionMode(true);
                toggleSelection(position);
                notifyItemChanged(position);

                // 通知选择模式变化
                if (onSelectionModeChangeListener != null) {
                    onSelectionModeChangeListener.onSelectionModeChanged(true);
                    onSelectionModeChangeListener.onSelectionChanged(getSelectedCount());
                }
                return true;
            }
            return false;
        });
    }

    /**
     * 切换选择状态
     */
    private void toggleSelection(int position) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position);
        } else {
            selectedPositions.add(position);
        }
        Log.d(TAG, "Toggled selection at position: " + position + ", selected: " + selectedPositions.contains(position));
    }

    /**
     * 设置选择模式
     */
    public void setSelectionMode(boolean selectionMode) {
        boolean oldMode = this.isSelectionMode;
        this.isSelectionMode = selectionMode;

        if (oldMode != selectionMode) {
            Log.d(TAG, "Selection mode changed to: " + selectionMode);

            if (!selectionMode) {
                // 退出选择模式时清空选择
                clearSelection();
            }
            notifyDataSetChanged();
        }
    }

    /**
     * 获取选择模式状态
     */
    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    /**
     * 获取选中的媒体文件
     */
    public List<MediaFile> getSelectedMediaFiles() {
        List<MediaFile> selectedFiles = new ArrayList<>();
        for (int position : selectedPositions) {
            if (position >= 0 && position < mediaFiles.size()) {
                MediaFile file = mediaFiles.get(position);
                if (file != null) {
                    selectedFiles.add(file);
                }
            }
        }
        Log.d(TAG, "getSelectedMediaFiles: " + selectedFiles.size() + " files selected");
        return selectedFiles;
    }

    /**
     * 获取选中数量
     */
    public int getSelectedCount() {
        return selectedPositions.size();
    }

    /**
     * 清空选择
     */
    public void clearSelection() {
        selectedPositions.clear();
        Log.d(TAG, "Selection cleared");
        if (isSelectionMode) {
            notifyDataSetChanged();
        }
    }

    /**
     * 设置媒体文件数据
     */
    public void setMediaFiles(List<MediaFile> mediaFiles) {
        this.mediaFiles = mediaFiles != null ? new ArrayList<>(mediaFiles) : new ArrayList<>();
        clearSelection();
        setSelectionMode(false);
        notifyDataSetChanged();
        Log.d(TAG, "Media files updated: " + this.mediaFiles.size() + " items");
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    public void setOnSelectionModeChangeListener(OnSelectionModeChangeListener listener) {
        this.onSelectionModeChangeListener = listener;
    }

    @Override
    public int getItemCount() {
        return mediaFiles != null ? mediaFiles.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        CheckBox checkBox;
        ImageView videoIcon;
        TextView fileTypeText;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.mediaImage);
            checkBox = itemView.findViewById(R.id.checkBox);
            videoIcon = itemView.findViewById(R.id.videoIcon);
            fileTypeText = itemView.findViewById(R.id.fileTypeText);

            if (checkBox != null) {
                checkBox.setClickable(false);
            }
        }
    }
}