package com.smartprints.rknn_vision_lab;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;
import com.smartprints.rknn_vision_lab.video.VideoDemoActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        MaterialCardView videoCard = findViewById(R.id.videoCard);
        videoCard.setOnClickListener(v -> {
            startActivity(new Intent(this, VideoDemoActivity.class));
            finish();
        });

        MaterialCardView imageCard = findViewById(R.id.imageCard);
        imageCard.setOnClickListener(v -> {
//            startActivity(new Intent(this, ImageDemoActivity.class));
//            finish();
        });

        MaterialCardView settingsCard = findViewById(R.id.settingsCard);
        settingsCard.setOnClickListener(v -> {
//            startActivity(new Intent(this, SettingsActivity.class));
//            finish();
        });

    }
    static {
        if (!org.opencv.android.OpenCVLoader.initDebug()) {
            System.out.println("OpenCV not loaded");
        } else {
            System.out.println("OpenCV loaded successfully");
        }
    }

}