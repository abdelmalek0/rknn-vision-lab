package com.smartprints.rknn_vision_lab.ui;

import static com.smartprints.rknn_vision_lab.core.PermissionsManager.REQUEST_CODE_PERMISSIONS;
import static com.smartprints.rknn_vision_lab.core.PermissionsManager.REQUEST_CODE_STORAGE;
import static com.smartprints.rknn_vision_lab.core.PermissionsManager.REQUIRED_PERMISSIONS;
import static com.smartprints.rknn_vision_lab.core.PermissionsManager.STORAGE_PERMISSIONS;
import static com.smartprints.rknn_vision_lab.core.PermissionsManager.allPermissionsGranted;
import static com.smartprints.rknn_vision_lab.core.PermissionsManager.storagePermissionsGranted;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.elvishew.xlog.XLog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.smartprints.rknn_vision_lab.R;
import com.smartprints.rknn_vision_lab.core.CameraSource;
import com.smartprints.rknn_vision_lab.core.CameraUtils;
import com.smartprints.rknn_vision_lab.core.FileSource;
import com.smartprints.rknn_vision_lab.core.RtspSource;
import com.smartprints.rknn_vision_lab.core.SurfaceRenderer;
import com.smartprints.rknn_vision_lab.core.VideoSource;
import com.smartprints.rknn_vision_lab.inference.InferenceEngine;
import com.smartprints.rknn_vision_lab.inference.RknnInferenceEngine;
import com.smartprints.rknn_vision_lab.core.VideoPipeline;

import java.util.List;

public class VideoActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private final static String TAG = "VideoActivity";
    private SurfaceView surfaceView;
    private VideoPipeline videoPipeline;
    public SurfaceHolder surfaceHolder;

    private ActivityResultLauncher<String> pickVideoLauncher;
    private Spinner cameraSwitch;
    private List<String> cameras;
    private boolean isReady = false;

    private VideoSource currentSource;
    private SurfaceRenderer renderer;
//    private VideoSourceListener videoSourceListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);
        surfaceView = findViewById(R.id.preview_view);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        pickVideoLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::onVideoPicked
        );
        InferenceEngine engine = new RknnInferenceEngine();
        renderer = new SurfaceRenderer(surfaceHolder);
        videoPipeline = new VideoPipeline(renderer, engine);
        videoPipeline.start();

        Button playVideo = findViewById(R.id.playVideo);
        ChipGroup chipGroup = findViewById(R.id.videoSource);
        LinearLayout controlsGroup = findViewById(R.id.controlsGroup);
        cameraSwitch = findViewById(R.id.cameraSwitch);
        TextInputEditText rtspInput = findViewById(R.id.rtspInput);
        cameras = CameraUtils.getAvailableCameras(this);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                cameras
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        cameraSwitch.setAdapter(adapter);

        cameraSwitch.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (cameras == null || cameras.isEmpty() || !isReady) return;
                switchToCamera(cameras.get(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                controlsGroup.setVisibility(View.GONE);
//                Toast.makeText(VideoDemoActivity.this, "No selection", Toast.LENGTH_SHORT).show();
                return;
            }

            // For single-selection ChipGroup, you can get the first checked ID
            int checkedId = checkedIds.get(0);
            Chip chip = group.findViewById(checkedId);
            if (chip != null) {
                if (checkedId == R.id.camera) {
                    controlsGroup.setVisibility(View.GONE);
                    cameraSwitch.setVisibility(View.VISIBLE);

                    if (isReady) {
                        int selectedPosition = cameraSwitch.getSelectedItemPosition();
                        switchToCamera(cameras.get(selectedPosition));
                    }
                } else if (checkedId == R.id.file) {
                    controlsGroup.setVisibility(View.GONE);

                    // Check storage permissions
                    if (!storagePermissionsGranted(this)) {
                        XLog.w(TAG, "Storage permissions not granted, requesting...");
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO}, REQUEST_CODE_STORAGE);
                        } else {
                            requestPermissions(STORAGE_PERMISSIONS, REQUEST_CODE_STORAGE);
                        }
                        return;
                    }

                    pickVideoLauncher.launch("video/*");
                } else if (checkedId == R.id.stream) {
                    controlsGroup.setVisibility(View.VISIBLE);
                    cameraSwitch.setVisibility(View.GONE);
                } else {
                    cameraSwitch.setVisibility(View.GONE);
                    controlsGroup.setVisibility(View.GONE);
                }
            }
        });


        playVideo.setOnClickListener(v -> {
            int checkedId = chipGroup.getCheckedChipId();
            if (checkedId == R.id.camera) {
                if (!allPermissionsGranted(this)) {
                    requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
                    return;
                }
                if (isReady) {
                    int selectedPosition = cameraSwitch.getSelectedItemPosition();
                    switchToCamera(cameras.get(selectedPosition));
                }
            } else if (checkedId == R.id.stream) {
                // Start RTSP streaming
                String rtspUrl = String.valueOf(rtspInput.getText()).trim();
                if (rtspUrl.isEmpty()) {
                    Toast.makeText(this, "Please enter RTSP URL", Toast.LENGTH_SHORT).show();
                    return;
                }
                switchToRtsp(rtspUrl);
            }
        });

        if (!allPermissionsGranted(this)) {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    private void onVideoPicked(Uri uri) {
        switchToFile(uri);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        isReady = true;
    }
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        isReady = false;
        cleanup();
    }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        isReady = true;
    }

    public void switchToCamera(String cameraId) {
        stopCurrent();
        currentSource = new CameraSource(this, videoPipeline, cameraId);
        currentSource.start();
    }

    public void switchToFile(Uri fileUri) {
        stopCurrent();
        currentSource = new FileSource(this, videoPipeline, fileUri);
        currentSource.start();
    }

    public void switchToRtsp(String rtspUrl) {
        stopCurrent();
        currentSource = new RtspSource(videoPipeline, rtspUrl);
        currentSource.start();
    }

    private void stopCurrent() {
        if (currentSource != null && currentSource.isRunning()) {
            currentSource.stop();
        }
        renderer.clear();
    }

    public void cleanup() {
        stopCurrent();
        videoPipeline.stop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted(this)) {
                Toast.makeText(this, "Permissions not granted. Some features may not work.", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_CODE_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Storage permission granted, launching file picker");
                // Permission granted, launch the picker
                pickVideoLauncher.launch("video/*");
            } else {
                Toast.makeText(this, "Storage permission denied. Cannot access video files.", Toast.LENGTH_LONG).show();
            }
        }
    }
}