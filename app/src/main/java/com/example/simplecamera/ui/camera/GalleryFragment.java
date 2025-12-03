package com.example.simplecamera.ui.camera;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.simplecamera.R;
import com.example.simplecamera.adapter.MediaAdapter;
import com.example.simplecamera.database.entity.MediaFile;
import com.example.simplecamera.viewmodel.GalleryViewModel;

import java.util.ArrayList;
import java.util.List;

public class GalleryFragment extends Fragment implements MediaAdapter.OnSelectionModeChangeListener {
    private static final String TAG = "GalleryFragment";

    private RecyclerView recyclerView;
    private MediaAdapter adapter;
    private GalleryViewModel viewModel;
    private TextView titleText;
    private ImageButton backButton;
    private ImageButton deleteButton;
    private ImageButton cancelSelectionButton;
    private TextView selectionCountText;
    private TextView emptyText;

    // 选择模式相关UI
    private View selectionModeToolbar;
    private View normalModeToolbar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_gallery, container, false);
        initViews(view);
        setupRecyclerView();
        setupViewModel();
        setupClickListeners();
        updateUIForNormalMode(); // 初始化为正常模式
        return view;
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recyclerView);
        titleText = view.findViewById(R.id.titleText);
        backButton = view.findViewById(R.id.backButton);
        deleteButton = view.findViewById(R.id.deleteButton);
        cancelSelectionButton = view.findViewById(R.id.cancelSelectionButton);
        selectionCountText = view.findViewById(R.id.selectionCountText);
        emptyText = view.findViewById(R.id.emptyText);

        // 选择模式工具栏
        selectionModeToolbar = view.findViewById(R.id.selectionModeToolbar);
        normalModeToolbar = view.findViewById(R.id.normalModeToolbar);
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3));

        adapter = new MediaAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        // 设置点击监听
        adapter.setOnItemClickListener(new MediaAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(MediaFile mediaFile) {
                openMediaDetail(mediaFile);
            }
        });

        // 设置选择模式变化监听
        adapter.setOnSelectionModeChangeListener(this);
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(requireActivity()).get(GalleryViewModel.class);
        viewModel.getAllMediaFiles().observe(getViewLifecycleOwner(), mediaFiles -> {
            if (mediaFiles != null && !mediaFiles.isEmpty()) {
                adapter.setMediaFiles(mediaFiles);
                adapter.notifyDataSetChanged();
                showEmptyState(false);
            } else {
                showEmptyState(true);
            }
        });

        // 观察操作状态
        viewModel.getOperationStatus().observe(getViewLifecycleOwner(), status -> {
            if (status != null && !status.isEmpty()) {
                Toast.makeText(getContext(), status, Toast.LENGTH_SHORT).show();
            }
        });

        // 观察删除状态（可选，用于禁用按钮等）
        viewModel.getIsDeleting().observe(getViewLifecycleOwner(), isDeleting -> {
            if (deleteButton != null) {
                deleteButton.setEnabled(!isDeleting);
            }
        });
    }

    private void setupClickListeners() {
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                if (adapter.isSelectionMode()) {
                    // 选择模式下，返回按钮退出选择模式
                    exitSelectionMode();
                } else {
                    // 正常模式下返回相机
                    goBackToCamera();
                }
            });
        }

        if (deleteButton != null) {
            deleteButton.setOnClickListener(v -> showDeleteConfirmationDialog());
        }

        if (cancelSelectionButton != null) {
            cancelSelectionButton.setOnClickListener(v -> exitSelectionMode());
        }
    }

    /**
     * 显示删除确认对话框
     */
    private void showDeleteConfirmationDialog() {
        List<MediaFile> selectedFiles = adapter.getSelectedMediaFiles();
        if (selectedFiles.isEmpty()) {
            Toast.makeText(getContext(), "请先选择要删除的文件", Toast.LENGTH_SHORT).show();
            return;
        }

        int count = selectedFiles.size();
        String message = count == 1 ?
                "确定要删除这个文件吗？此操作不可撤销。" :
                "确定要删除这" + count + "个文件吗？此操作不可撤销。";

        new AlertDialog.Builder(requireContext())
                .setTitle("确认删除")
                .setMessage(message)
                .setPositiveButton("删除", (dialog, which) -> {
                    deleteSelectedFiles();
                })
                .setNegativeButton("取消", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    /**
     * 删除选中的文件
     */
    private void deleteSelectedFiles() {
        // 检查是否正在删除
        if (viewModel.getIsDeleting().getValue() != null && viewModel.getIsDeleting().getValue()) {
            Log.w(TAG, "Delete operation already in progress");
            return;
        }

        if (adapter == null || viewModel == null || !isAdded()) {
            Log.e(TAG, "Cannot delete files: Fragment not ready");
            return;
        }

        List<MediaFile> selectedFiles = adapter.getSelectedMediaFiles();
        if (selectedFiles.isEmpty()) {
            Toast.makeText(getContext(), "没有选择要删除的文件", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "Starting deletion of " + selectedFiles.size() + " files");

        try {
            // 显示删除进度
            Toast.makeText(getContext(), "正在删除 " + selectedFiles.size() + " 个文件...", Toast.LENGTH_SHORT).show();

            // 使用一次性观察者
            final androidx.lifecycle.Observer<String> statusObserver = new androidx.lifecycle.Observer<String>() {
                @Override
                public void onChanged(String status) {
                    if (status != null && !status.isEmpty()) {
                        Log.d(TAG, "Delete operation status: " + status);

                        if (status.contains("成功") || status.contains("失败")) {
                            // 删除操作完成
                            exitSelectionMode();

                            // 安全地移除观察者
                            if (viewModel != null && viewModel.getOperationStatus().hasObservers()) {
                                viewModel.getOperationStatus().removeObserver(this);
                            }
                        }
                    }
                }
            };

            viewModel.getOperationStatus().observe(getViewLifecycleOwner(), statusObserver);
            viewModel.deleteMediaFiles(selectedFiles);

        } catch (Exception e) {
            Log.e(TAG, "Error during file deletion", e);
            Toast.makeText(getContext(), "删除过程出错", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 退出选择模式
     */
    private void exitSelectionMode() {
        if (adapter != null) {
            adapter.setSelectionMode(false);
        }
        updateUIForNormalMode();
    }

    /**
     * 进入选择模式
     */
    private void enterSelectionMode() {
        if (adapter != null) {
            adapter.setSelectionMode(true);
        }
        updateUIForSelectionMode();
    }

    /**
     * 更新UI为正常模式
     */
    private void updateUIForNormalMode() {
        if (!isAdded() || getActivity() == null) return;

        requireActivity().runOnUiThread(() -> {
            try {
                if (selectionModeToolbar != null) {
                    selectionModeToolbar.setVisibility(View.GONE);
                }
                if (normalModeToolbar != null) {
                    normalModeToolbar.setVisibility(View.VISIBLE);
                }
                if (titleText != null) {
                    titleText.setText("相册");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating UI for normal mode", e);
            }
        });
    }

    /**
     * 更新UI为选择模式
     */
    private void updateUIForSelectionMode() {
        if (!isAdded()) return;

        requireActivity().runOnUiThread(() -> {
            try {
                if (selectionModeToolbar != null) {
                    selectionModeToolbar.setVisibility(View.VISIBLE);
                }
                if (normalModeToolbar != null) {
                    normalModeToolbar.setVisibility(View.GONE);
                }
                updateSelectionCount();
            } catch (Exception e) {
                Log.e(TAG, "Error updating UI for selection mode", e);
            }
        });
    }

    /**
     * 更新选择计数
     */
    private void updateSelectionCount() {
        if (adapter != null && selectionCountText != null) {
            try {
                int count = adapter.getSelectedCount();
                selectionCountText.setText("已选择 " + count + " 项");
            } catch (Exception e) {
                Log.e(TAG, "Error updating selection count", e);
            }
        }
    }

    private void showEmptyState(boolean show) {
        if (emptyText != null && recyclerView != null) {
            emptyText.setVisibility(show ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    private void openMediaDetail(MediaFile mediaFile) {
        if (mediaFile == null || mediaFile.getFilePath() == null) {
            Toast.makeText(getContext(), "媒体文件不可用", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            MediaPreviewFragment preview = MediaPreviewFragment.newInstance(mediaFile.getFilePath(), mediaFile.getFileType());
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, preview)
                    .addToBackStack("preview")
                    .commit();
        } catch (Exception e) {
            Toast.makeText(getContext(), "无法打开媒体文件", Toast.LENGTH_SHORT).show();
        }
    }

    private void goBackToCamera() {
        requireActivity().getSupportFragmentManager().popBackStack();
    }

    /**
     * 选择模式变化回调
     */
    @Override
    public void onSelectionModeChanged(boolean isSelectionMode) {
        Log.d(TAG, "onSelectionModeChanged: " + isSelectionMode);
        if (isSelectionMode) {
            enterSelectionMode();
        } else {
            updateSelectionCount();
        }
    }

    /**
     * 选择数量变化回调
     */
    @Override
    public void onSelectionChanged(int selectedCount) {
        Log.d(TAG, "onSelectionChanged: " + selectedCount + " items selected");
        updateSelectionCount();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 退出选择模式
        if (adapter != null && adapter.isSelectionMode()) {
            exitSelectionMode();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView: Cleaning up gallery resources");

        // 清理资源
        if (adapter != null) {
            adapter.setSelectionMode(false);
            adapter.clearSelection();
        }
    }
}