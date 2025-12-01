
package com.example.simplecamera.ui.camera;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.simplecamera.R;

import java.io.File;

public class MediaPreviewFragment extends Fragment {
    private static final String ARG_FILE_PATH = "arg_file_path";
    private static final String ARG_FILE_TYPE = "arg_file_type"; // 0 image, 1 video

    public static MediaPreviewFragment newInstance(String filePath, int fileType) {
        MediaPreviewFragment f = new MediaPreviewFragment();
        Bundle args = new Bundle();
        args.putString(ARG_FILE_PATH, filePath);
        args.putInt(ARG_FILE_TYPE, fileType);
        f.setArguments(args);
        return f;
    }

    private String filePath;
    private int fileType;

    private ImageView imageView;
    private VideoView videoView;
    private ImageButton backButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_media_preview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        imageView = view.findViewById(R.id.previewImage);
        videoView = view.findViewById(R.id.previewVideo);
        backButton = view.findViewById(R.id.previewBackButton);

        if (getArguments() != null) {
            filePath = getArguments().getString(ARG_FILE_PATH);
            fileType = getArguments().getInt(ARG_FILE_TYPE, 0);
        }

        backButton.setOnClickListener(v -> {
            if (getActivity() != null) getActivity().getSupportFragmentManager().popBackStack();
        });

        if (filePath == null) {
            Toast.makeText(requireContext(), "文件路径为空", Toast.LENGTH_SHORT).show();
            return;
        }

        if (fileType == 0) {
            // 显示图片
            videoView.setVisibility(View.GONE);
            imageView.setVisibility(View.VISIBLE);
            Uri uri = parseToUri(filePath);
            Glide.with(this)
                    .load(uri)
                    .placeholder(R.drawable.ic_placeholder)
                    .error(R.drawable.ic_error)
                    .into(imageView);
            // 点击图片可退出预览
            imageView.setOnClickListener(v -> {
                if (getActivity() != null) getActivity().getSupportFragmentManager().popBackStack();
            });
        } else {
            // 播放视频
            imageView.setVisibility(View.GONE);
            videoView.setVisibility(View.VISIBLE);
            try {
                Uri uri = parseToUri(filePath);
                MediaController mc = new MediaController(requireContext());
                mc.setAnchorView(videoView);
                videoView.setMediaController(mc);
                videoView.setVideoURI(uri);
                videoView.requestFocus();
                videoView.start();
                // 点击 VideoView 切换播放/暂停
                videoView.setOnClickListener(v -> {
                    if (videoView.isPlaying()) videoView.pause();
                    else videoView.start();
                });
            } catch (Exception e) {
                Toast.makeText(requireContext(), "无法播放视频", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (videoView != null && videoView.isPlaying()) {
            videoView.pause();
        }
    }

    private Uri parseToUri(String path) {
        if (path.startsWith("content://")) {
            return Uri.parse(path);
        } else if (path.startsWith("file://")) {
            return Uri.parse(path);
        } else {
            File f = new File(path);
            return Uri.fromFile(f);
        }
    }
}
