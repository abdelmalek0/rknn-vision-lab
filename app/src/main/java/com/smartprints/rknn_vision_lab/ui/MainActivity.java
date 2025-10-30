package com.smartprints.rknn_vision_lab.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.elvishew.xlog.XLog;
import com.google.android.material.card.MaterialCardView;
import com.smartprints.rknn_vision_lab.R;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

public class MainActivity extends AppCompatActivity {
    private BaseLoaderCallback openCVLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    XLog.d("OpenCV loaded successfully");
                    // OpenCV is ready to use
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, openCVLoaderCallback);
        } else {
            openCVLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialCardView videoCard = findViewById(R.id.videoCard);
        videoCard.setOnClickListener(v -> {
            startActivity(new Intent(this, VideoActivity.class));
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
}