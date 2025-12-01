package com.example.simplecamera.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.simplecamera.R;
import com.example.simplecamera.ui.camera.CameraFragment;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 加载CameraFragment作为默认界面
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new CameraFragment())
                    .commit();
        }
    }
}