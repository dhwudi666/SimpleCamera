package com.example.simplecamera.ui.camera;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.simplecamera.R;
import com.example.simplecamera.adapter.MediaAdapter;
import com.example.simplecamera.database.entity.MediaFile;
import com.example.simplecamera.viewmodel.GalleryViewModel;
import com.example.simplecamera.ui.camera.MediaPreviewFragment;

import java.util.ArrayList;

public class GalleryFragment extends Fragment {
    private RecyclerView recyclerView;
    private MediaAdapter adapter;
    private GalleryViewModel viewModel;
    private TextView titleText;
    private ImageButton backButton;
    private TextView emptyText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_gallery, container, false);
        initViews(view);
        setupRecyclerView();
        setupViewModel();
        setupClickListeners();
        return view;
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recyclerView);
        titleText = view.findViewById(R.id.titleText);
        backButton = view.findViewById(R.id.backButton);
        emptyText = view.findViewById(R.id.emptyText);

    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3));

        adapter = new MediaAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        // 设置点击监听
        adapter.setOnItemClickListener(new MediaAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(MediaFile mediaFile) {
                // 点击图片后的操作，比如查看大图
                openMediaDetail(mediaFile);
            }
        });
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
    }

    private void setupClickListeners() {
        if (backButton != null) {
            backButton.setOnClickListener(v -> goBackToCamera());
        }
    }

    private void showEmptyState(boolean show) {
        if (emptyText != null) {
            emptyText.setVisibility(show ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    private void openMediaDetail(MediaFile mediaFile) {
        if (mediaFile == null || mediaFile.getFilePath() == null) {
            Toast.makeText(getContext(), "媒体文件不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        // 使用 MediaPreviewFragment 进行显示/播放
        MediaPreviewFragment preview = MediaPreviewFragment.newInstance(mediaFile.getFilePath(), mediaFile.getFileType());
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, preview)
                .addToBackStack("preview")
                .commit();
    }

    private void goBackToCamera() {
        // 返回相机界面
        requireActivity().getSupportFragmentManager().popBackStack();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 刷新数据
        if (viewModel != null) {
            // 可以在这里触发数据刷新
        }
    }
}