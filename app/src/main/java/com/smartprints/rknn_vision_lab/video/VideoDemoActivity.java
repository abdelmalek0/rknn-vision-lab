package com.smartprints.rknn_vision_lab.video;

import static com.smartprints.rknn_vision_lab.CameraUtils.yuv420ToArgb;
import static org.opencv.android.Utils.matToBitmap;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.graphics.Bitmap;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.common.util.concurrent.ListenableFuture;
import com.smartprints.rknn_vision_lab.CameraAPI;
import com.smartprints.rknn_vision_lab.CameraUtils;
import com.smartprints.rknn_vision_lab.R;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class VideoDemoActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PERMISSIONS = 101;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET
    };
    private ImageView opencvImage;
    private Button opencvRtspBtn;
    private View cameraContainer;
    private SurfaceView previewView;
    private ProgressBar progressBar;
    private EditText rtspInput;
    private Button playVideo, startCameraBtn, pickFileBtn;
    ImageReader imageReader;

    // OpenCV RTSP
    private volatile boolean opencvRunning = false;
    private Thread opencvThread;
    public SurfaceHolder surfaceHolder;

    private ActivityResultLauncher<String> pickVideoLauncher;
    Spinner cameraSwitch;
    List<String> cameras;
    // File playback via OpenCV
    private Thread filePlaybackThread;
    private volatile boolean filePlaybackRunning = false;
    private final Object filePlaybackLock = new Object();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_demo);
        previewView = findViewById(R.id.preview_view);
        surfaceHolder = previewView.getHolder();
        playVideo = findViewById(R.id.playVideo);
        pickVideoLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                // Ensure camera is fully stopped before starting playback
                stopCameraPipeline();
                startFilePlayback(uri);
            }
        });

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
                if (cameras == null || cameras.isEmpty()) return;
                // If camera pipeline already running, switch immediately
                if (cameraInitialized && cameraAPI != null) {
                    cameraAPI.reopenCamera(cameras.get(position));
                }
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
                controlsGroup.setVisibility(View.VISIBLE);
                if (checkedId == R.id.camera) {
                    cameraSwitch.setVisibility(View.VISIBLE);
                    rtspInput.setVisibility(View.GONE);
                    // If switching to camera, ensure any file or player playback is stopped/hidden
                    stopFilePlayback();
                    // Start camera immediately when switching to Camera
                    if (!surfaceCallbackAdded) {
                        startCameraPreview();
                    } else if (!cameraInitialized) {
                        int w = previewView.getWidth();
                        int h = previewView.getHeight();
                        if (w > 0 && h > 0) setupCameraOnSurface(surfaceHolder, w, h);
                    }
                } else if (checkedId == R.id.file) {
                    cameraSwitch.setVisibility(View.GONE);
                    rtspInput.setVisibility(View.GONE);
                    // Stop camera and immediately open picker
                    stopCameraPipeline();
                    stopFilePlayback();
                    pickVideoLauncher.launch("video/*");
                } else if (checkedId == R.id.stream) {
                    cameraSwitch.setVisibility(View.GONE);
                    rtspInput.setVisibility(View.VISIBLE);
                } else {
                    cameraSwitch.setVisibility(View.GONE);
                    rtspInput.setVisibility(View.GONE);
                    controlsGroup.setVisibility(View.GONE);
                }
//                String selectedText = chip.getText().toString();
//                Toast.makeText(VideoDemoActivity.this, "Selected: " + selectedText, Toast.LENGTH_SHORT).show();
            }
        });


        playVideo.setOnClickListener(v -> {
            int checkedId = chipGroup.getCheckedChipId();
            if (checkedId == R.id.camera) {
                // Ensure player hidden and stopped when starting camera
                stopFilePlayback();
                if (!allPermissionsGranted()) {
                    requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
                    return;
                }
                if (!surfaceCallbackAdded) {
                    startCameraPreview();
                } else if (cameraAPI != null) {
                    int selectedPosition = cameraSwitch.getSelectedItemPosition();
                    if (selectedPosition >= 0 && selectedPosition < cameras.size()) {
                        cameraAPI.openCamera(cameras.get(selectedPosition));
                    }
                }
            } else if (checkedId == R.id.file) {
                // Launch picker; playback will auto-start on selection
                stopCameraPipeline();
                stopFilePlayback();
                pickVideoLauncher.launch("video/*");
            }
        });

//        startCameraBtn.setOnClickListener(v -> {
////            stopPlayer();
//            startCameraPreview();
//        });

//        pickFileBtn.setOnClickListener(v -> {
//            cameraAPI.stopCamera();
//            // pick a video file from device
//            pickVideoLauncher.launch("video/*");
//        });
//
//        opencvRtspBtn.setOnClickListener(v -> {
//            String url = rtspInput.getText().toString().trim();
//            if (url.isEmpty()) {
//                Toast.makeText(this, "Enter RTSP URL", Toast.LENGTH_SHORT).show();
//                return;
//            }
////            stopPlayer();
////            startOpenCvRtsp(url);
//        });

        if (!allPermissionsGranted()) {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
        // Do not auto-start camera; wait for user to choose Camera and press Play
    }

    CameraAPI cameraAPI;
    private boolean surfaceCallbackAdded = false;
    private boolean cameraInitialized = false;
    // Dedicated processing thread to convert and draw frames (so camera handler isn't blocked)
    private HandlerThread processingThread;
    private Handler processingHandler;
    private Bitmap reusableBitmap;
    private int[] pixelBuffer;
    private volatile boolean isProcessingFrame = false;

    private void startCameraPreview() {
        // Ensure exclusive access and black canvas before starting camera
        ensureIdleAndBlackCanvas();

        if (!surfaceCallbackAdded) {
            surfaceCallbackAdded = true;
            surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @SuppressLint("ResourceType")
            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                setupCameraOnSurface(holder, width, height);
            }

            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                System.out.println("Surface has been created");
                if (!cameraInitialized) {
                    int w = previewView.getWidth();
                    int h = previewView.getHeight();
                    if (w > 0 && h > 0) {
                        setupCameraOnSurface(holder, w, h);
                    }
                }
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                // Clean up camera and ImageReader when surface destroyed
                System.out.println("Surface destroyed, releasing camera and reader");
                if (cameraAPI != null) {
                    cameraAPI.stopCamera();
                }
                if (imageReader != null) {
                    imageReader.setOnImageAvailableListener(null, null);
                    imageReader.close();
                    imageReader = null;
                }
                // stop processing thread
                if (processingThread != null) {
                    processingThread.quitSafely();
                    processingThread = null;
                    processingHandler = null;
                }
                isProcessingFrame = false;
                // release bitmaps/buffers
                if (reusableBitmap != null) {
                    reusableBitmap.recycle();
                    reusableBitmap = null;
                }
                pixelBuffer = null;
                cameraInitialized = false;
            }
            });
        }

        // If surface already exists and is valid, initialize immediately
        if (surfaceHolder.getSurface() != null && surfaceHolder.getSurface().isValid() && !cameraInitialized) {
            int w = previewView.getWidth();
            int h = previewView.getHeight();
            if (w > 0 && h > 0) {
                setupCameraOnSurface(surfaceHolder, w, h);
            }
        }
    }

    private void setupCameraOnSurface(@NonNull SurfaceHolder holder, int width, int height) {
        if (cameraInitialized) return;
        cameraInitialized = true;

        imageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2);
        cameraAPI = new CameraAPI(VideoDemoActivity.this, imageReader);

        // Ensure CameraAPI background handler exists
        cameraAPI.startBackgroundThread();
        Handler cameraHandler = cameraAPI.getBackgroundHandler();

        // Start processing thread (used for conversion + drawing)
        if (processingThread == null || !processingThread.isAlive()) {
            processingThread = new HandlerThread("FrameProcessing");
            processingThread.start();
            processingHandler = new Handler(processingThread.getLooper());
        }

        // Reuse bitmap and pixel buffer to avoid per-frame allocations
        reusableBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        pixelBuffer = new int[width * height];

        imageReader.setOnImageAvailableListener(reader -> {
            // Backpressure: if a frame is already being processed, drop the latest frame
            if (isProcessingFrame) {
                Image toDrop = reader.acquireLatestImage();
                if (toDrop != null) toDrop.close();
                return;
            }

            final Image image = reader.acquireLatestImage();
            if (image == null) return;
            isProcessingFrame = true;

            processingHandler.post(() -> {
                try {
                    // Ensure processing bitmap/buffer match the frame size
                    if (reusableBitmap == null || reusableBitmap.getWidth() != image.getWidth() || reusableBitmap.getHeight() != image.getHeight()) {
                        reusableBitmap = Bitmap.createBitmap( image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
                        pixelBuffer = new int[image.getWidth() * image.getHeight()];
                    }
                    yuv420ToArgb(image, reusableBitmap, pixelBuffer);
                    Mat mat = new Mat();
                    Utils.bitmapToMat(reusableBitmap, mat);

                    int rotationDegrees = 0;
                    if (cameraAPI != null) rotationDegrees = cameraAPI.getFrameRotationDegrees();

                    if (rotationDegrees == 90) {
                        Core.rotate(mat, mat, Core.ROTATE_90_CLOCKWISE);
                    } else if (rotationDegrees == 180) {
                        Core.rotate(mat, mat, Core.ROTATE_180);
                    } else if (rotationDegrees == 270) {
                        Core.rotate(mat, mat, Core.ROTATE_90_COUNTERCLOCKWISE);
                    }

                    Bitmap rotatedBitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(mat, rotatedBitmap);
                    mat.release();
                    reusableBitmap = rotatedBitmap;

                    Canvas canvas = null;
                    try {
                        canvas = holder.lockCanvas();
                        if (canvas != null) {
                            // Scale to fit the surface dimensions
                            canvas.drawColor(android.graphics.Color.BLACK);
                            android.graphics.Rect dest = new android.graphics.Rect(0, 0, canvas.getWidth(), canvas.getHeight());
                            canvas.drawBitmap(reusableBitmap, null, dest, null);
                        }
                    } finally {
                        if (canvas != null) holder.unlockCanvasAndPost(canvas);
                    }
                } catch (Exception e) {
                    // ignore conversion errors but ensure image closed
                    System.out.println("Error converting image");
                } finally {
                    try { image.close(); } catch (Exception ignore) {}
                    isProcessingFrame = false;
                }
            });
        }, cameraHandler);

        int selectedPosition = cameraSwitch.getSelectedItemPosition();
        if (selectedPosition >= 0 && selectedPosition < cameras.size()) {
            cameraAPI.openCamera(cameras.get(selectedPosition));
        }
    }

    private void stopCameraPipeline() {
        // Detach ImageReader listener to avoid posting to a dead handler
        try {
            if (imageReader != null) {
                imageReader.setOnImageAvailableListener(null, null);
                imageReader.close();
            }
        } catch (Exception ignore) {}
        imageReader = null;

        // Stop processing thread
        if (processingThread != null) {
            processingThread.quitSafely();
            processingThread = null;
            processingHandler = null;
        }
        isProcessingFrame = false;
        if (reusableBitmap != null) {
            reusableBitmap.recycle();
            reusableBitmap = null;
        }
        pixelBuffer = null;

        // Stop camera (also stops background thread)
        if (cameraAPI != null) {
            cameraAPI.stopCamera();
        }
        cameraInitialized = false;
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraAPI != null) {
            cameraAPI.stopCamera();
        }
        if (processingThread != null) {
            processingThread.quitSafely();
            processingThread = null;
            processingHandler = null;
        }
        if (reusableBitmap != null) {
            reusableBitmap.recycle();
            reusableBitmap = null;
        }
        pixelBuffer = null;
    }

    private void playLocalUri(@NonNull Uri uri) {
        // Stop camera preview if running
        if (cameraAPI != null) {
            cameraAPI.stopCamera();
        }
    }
    private void startFilePlayback(@NonNull Uri uri) {
        // Decode and render video frames onto the same SurfaceView using OpenCV
        stopFilePlayback();
        // Hide PlayerView if visible

        // Resolve content Uri to a readable path; if not directly readable, copy to cache
        String path = UriUtils.resolveToPathOrCopyToCache(this, uri);
        if (path == null) {
            Toast.makeText(this, "Unable to open selected file", Toast.LENGTH_SHORT).show();
            return;
        }

        synchronized (filePlaybackLock) {
            // Ensure black canvas before starting
            clearSurfaceCanvas();
            filePlaybackRunning = true;
            filePlaybackThread = new Thread(() -> {
            VideoCapture capture = new VideoCapture(path);
            if (!capture.isOpened()) {
                runOnUiThread(() -> Toast.makeText(VideoDemoActivity.this, "Failed to open video", Toast.LENGTH_SHORT).show());
                filePlaybackRunning = false;
                return;
            }

            Mat frame = new Mat();
            Bitmap bitmap = null;

            while (filePlaybackRunning && capture.read(frame)) {
                if (frame.empty()) continue;
                // Convert Mat to Bitmap
                Bitmap tmp = Bitmap.createBitmap(frame.cols(), frame.rows(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(frame, tmp);
                if (bitmap != null && (bitmap.getWidth() != tmp.getWidth() || bitmap.getHeight() != tmp.getHeight())) {
                    bitmap.recycle();
                    bitmap = null;
                }
                if (bitmap == null) bitmap = Bitmap.createBitmap(tmp.getWidth(), tmp.getHeight(), Bitmap.Config.ARGB_8888);
                // Copy to reusable bitmap
                new Canvas(bitmap).drawBitmap(tmp, 0, 0, null);
                tmp.recycle();

                // Draw to SurfaceView
                Canvas canvas = null;
                try {
                    canvas = surfaceHolder.lockCanvas();
                    if (canvas != null) {
                        // Scale to fit the surface dimensions
                        canvas.drawColor(android.graphics.Color.BLACK);
                        android.graphics.Rect dest = new android.graphics.Rect(0, 0, canvas.getWidth(), canvas.getHeight());
                        canvas.drawBitmap(bitmap, null, dest, null);
                    }
                } catch (Exception ignore) {
                } finally {
                    if (canvas != null) surfaceHolder.unlockCanvasAndPost(canvas);
                }
            }

            // Cleanup
            if (bitmap != null) bitmap.recycle();
            frame.release();
            capture.release();
            filePlaybackRunning = false;
        }, "FilePlaybackThread");
            filePlaybackThread.start();
        }
    }

    private void stopFilePlayback() {
        filePlaybackRunning = false;
        if (filePlaybackThread != null) {
            try {
                filePlaybackThread.join(1000);
            } catch (InterruptedException ignore) {
            }
            filePlaybackThread = null;
        }
        clearSurfaceCanvas();
    }

    private void clearSurfaceCanvas() {
        try {
            Canvas c = surfaceHolder.lockCanvas();
            if (c != null) {
                c.drawColor(android.graphics.Color.BLACK);
            }
            if (c != null) surfaceHolder.unlockCanvasAndPost(c);
        } catch (Exception ignore) {}
    }

    private void ensureIdleAndBlackCanvas() {
        // Stop everything before starting a new source
        stopFilePlayback();
        stopCameraPipeline();
        clearSurfaceCanvas();
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                Toast.makeText(this, "Permissions not granted. Some features may not work.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
