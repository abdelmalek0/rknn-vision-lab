package com.smartprints.rknn_vision_lab.video;

import static com.smartprints.rknn_vision_lab.cam.CameraUtils.yuv420ToArgb;
import static org.opencv.android.Utils.matToBitmap;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
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
import androidx.core.content.ContextCompat;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

import java.io.File;
import android.util.Log;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.smartprints.rknn_vision_lab.cam.CameraAPI;
import com.smartprints.rknn_vision_lab.cam.CameraUtils;
import com.smartprints.rknn_vision_lab.R;
import com.smartprints.rknn_vision_lab.streaming.RtspStreamingService;

import java.util.List;
import android.media.MediaPlayer;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaCodec;
import java.nio.ByteBuffer;

public class VideoDemoActivity extends AppCompatActivity {

    private static final String TAG = "VideoDemoActivity";

    private static final int REQUEST_CODE_PERMISSIONS = 101;
    private static final int REQUEST_CODE_STORAGE = 102;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET
    };
    private static final String[] STORAGE_PERMISSIONS = new String[]{
            Manifest.permission.READ_EXTERNAL_STORAGE
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
    // Native MediaPlayer for reliable playback of content URIs / files
    private MediaPlayer mediaPlayer = null;
    // MediaCodec/MediaExtractor for frame-by-frame playback
    private MediaExtractor mediaExtractor;
    private MediaCodec mediaCodec;
    private ImageReader fileImageReader;
    
    // RTSP streaming
    private RtspStreamingService rtspStreamingService;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_demo);
        previewView = findViewById(R.id.preview_view);
        surfaceHolder = previewView.getHolder();
        
        // Initialize RTSP streaming service
        rtspStreamingService = new RtspStreamingService(surfaceHolder);
        playVideo = findViewById(R.id.playVideo);
        pickVideoLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                Log.d(TAG, "Video selected: " + uri);
                Log.d(TAG, "URI scheme: " + uri.getScheme());
                Log.d(TAG, "URI authority: " + uri.getAuthority());
                
                // Take persistable permission for content URIs
                try {
                    if ("content".equalsIgnoreCase(uri.getScheme())) {
                        getContentResolver().takePersistableUriPermission(uri, 
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        Log.d(TAG, "Persistable URI permission granted");
                    }
                } catch (SecurityException e) {
                    Log.w(TAG, "Could not take persistable permission (this is OK for some URIs)", e);
                }
                
                // Ensure camera is fully stopped before starting playback
                stopCameraPipeline();
                startFilePlayback(uri);
            } else {
                Log.w(TAG, "No video selected (URI is null)");
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
                if (checkedId == R.id.camera) {
                    controlsGroup.setVisibility(View.GONE);
                    cameraSwitch.setVisibility(View.VISIBLE);
                    // If switching to camera, ensure any file or player playback is stopped/hidden
                    stopFilePlayback();
                    stopRtspStreaming();
                    // Start camera immediately when switching to Camera
                    if (!surfaceCallbackAdded) {
                        startCameraPreview();
                    } else if (!cameraInitialized) {
                        int w = previewView.getWidth();
                        int h = previewView.getHeight();
                        if (w > 0 && h > 0) setupCameraOnSurface(surfaceHolder, w, h);
                    }
                } else if (checkedId == R.id.file) {
                    controlsGroup.setVisibility(View.GONE);
                    // Stop camera and check permissions before opening picker
                    stopCameraPipeline();
                    stopFilePlayback();
                    stopRtspStreaming();
                    
                    // Check storage permissions
                    if (!storagePermissionsGranted()) {
                        Log.w(TAG, "Storage permissions not granted, requesting...");
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
                // Check permissions before launching picker
                if (!storagePermissionsGranted()) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO}, REQUEST_CODE_STORAGE);
                    } else {
                        requestPermissions(STORAGE_PERMISSIONS, REQUEST_CODE_STORAGE);
                    }
                    return;
                }
                
                // Launch picker; playback will auto-start on selection
                stopCameraPipeline();
                stopFilePlayback();
                pickVideoLauncher.launch("video/*");
            } else if (checkedId == R.id.stream) {
                // Start RTSP streaming
                String rtspUrl = rtspInput.getText().toString().trim();
                if (rtspUrl.isEmpty()) {
                    Toast.makeText(this, "Please enter RTSP URL", Toast.LENGTH_SHORT).show();
                    return;
                }
                stopCameraPipeline();
                stopFilePlayback();
                startRtspStreaming(rtspUrl);
            }
        });

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
                            Rect dest = new Rect(0, (canvas.getHeight() - image.getWidth()) / 2, image.getHeight(), image.getWidth() + (canvas.getHeight() - image.getWidth()) / 2);
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
    
    private boolean storagePermissionsGranted() {
        // For Android 13+ (API 33+), check READ_MEDIA_VIDEO
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) 
                    == PackageManager.PERMISSION_GRANTED;
        }
        // For Android 10-12, check READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Stop file playback (includes MediaPlayer cleanup)
        stopFilePlayback();
        
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
        
        // Clean up RTSP streaming
        stopRtspStreaming();
    }

    private void playLocalUri(@NonNull Uri uri) {
        // Stop camera preview if running
        if (cameraAPI != null) {
            cameraAPI.stopCamera();
        }
    }
    // Remove the duplicate startFilePlayback() methods and add these:

private void startFilePlayback(@NonNull Uri uri) {
    // Decode and render video frames onto the same SurfaceView
    stopFilePlayback();
    
    // CRITICAL: Force surface recreation to disconnect any existing consumers
    // This prevents "already connected" errors
    surfaceHolder.setFixedSize(1, 1);
    surfaceHolder.setFixedSize(0, 0); // Reset to default
    
    clearSurfaceCanvas();
    
    // Small delay to ensure surface is fully disconnected
    new Handler(getMainLooper()).postDelayed(() -> {
        // Ensure surface is ready before starting playback
        if (surfaceHolder.getSurface() == null || !surfaceHolder.getSurface().isValid()) {
            Log.w(TAG, "Surface not ready, waiting for surface creation");
            // Add a one-time callback to start playback when surface is ready
            surfaceHolder.addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(@NonNull SurfaceHolder holder) {
                    Log.d(TAG, "Surface created, starting playback");
                    surfaceHolder.removeCallback(this);
                    startFilePlaybackInternal(uri);
                }

                @Override
                public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {}

                @Override
                public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                    surfaceHolder.removeCallback(this);
                }
            });
            return;
        }
        
        startFilePlaybackInternal(uri);
    }, 100); // 100ms delay to ensure surface cleanup
}

private void startFilePlaybackInternal(@NonNull Uri uri) {
        String scheme = uri.getScheme();
        
        // For content:// and file:// URIs, use MediaCodec for frame-by-frame processing
        if ("content".equalsIgnoreCase(scheme) || "file".equalsIgnoreCase(scheme)) {
            Log.d(TAG, "URI detected, using MediaCodec for playback");
            startFilePlaybackWithMediaCodec(uri);
            return;
        }
        
        // Unknown scheme
        Log.e(TAG, "Unsupported URI scheme: " + scheme);
        runOnUiThread(() -> Toast.makeText(this, 
            "Unsupported file type", Toast.LENGTH_SHORT).show());
    }
    
    private void startFilePlaybackWithMediaCodec(@NonNull Uri uri) {
        synchronized (filePlaybackLock) {
            stopFilePlayback(); // Ensure everything is clean before starting
            clearSurfaceCanvas();
            filePlaybackRunning = true;

            filePlaybackThread = new Thread(() -> {
                try {
                    // 1. Setup Extractor
                    mediaExtractor = new MediaExtractor();
                    mediaExtractor.setDataSource(this, uri, null);

                    int trackIndex = -1;
                    MediaFormat format = null;
                    for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
                        MediaFormat f = mediaExtractor.getTrackFormat(i);
                        String mime = f.getString(MediaFormat.KEY_MIME);
                        if (mime != null && mime.startsWith("video/")) {
                            trackIndex = i;
                            format = f;
                            break;
                        }
                    }

                    if (trackIndex == -1 || format == null) {
                        throw new RuntimeException("No video track found in " + uri);
                    }
                    
                    mediaExtractor.selectTrack(trackIndex);
                    
                    // Get video properties
                    int width = format.getInteger(MediaFormat.KEY_WIDTH);
                    int height = format.getInteger(MediaFormat.KEY_HEIGHT);
                    float frameRate = format.containsKey(MediaFormat.KEY_FRAME_RATE) ? 
                        format.getInteger(MediaFormat.KEY_FRAME_RATE) : 30f;
                    long frameDelayMs = (long) (1000f / frameRate);
                    
                    Log.d(TAG, "Video: " + width + "x" + height + " @ " + frameRate + " fps");

                    // 2. Setup Decoder WITHOUT Surface (buffer mode for CPU-accessible frames)
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    mediaCodec = MediaCodec.createDecoderByType(mime);
                    
                    // CRITICAL: Configure without a Surface to get CPU-accessible buffers
                    // Set color format to YUV420 for direct access
                    format.setInteger(MediaFormat.KEY_COLOR_FORMAT, 
                        android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
                    
                    mediaCodec.configure(format, null, null, 0); // null Surface = buffer mode
                    mediaCodec.start();

                    // 3. Run the decoding loop with frame timing
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    long timeoutUs = 10000; // 10ms
                    long lastFrameTime = System.currentTimeMillis();

                    while (filePlaybackRunning) {
                        // Feed input to decoder
                        int inputBufferId = mediaCodec.dequeueInputBuffer(timeoutUs);
                        if (inputBufferId >= 0) {
                            ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferId);
                            int sampleSize = mediaExtractor.readSampleData(inputBuffer, 0);

                            if (sampleSize < 0) {
                                // End of stream
                                mediaCodec.queueInputBuffer(inputBufferId, 0, 0, 0, 
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            } else {
                                mediaCodec.queueInputBuffer(inputBufferId, 0, sampleSize, 
                                    mediaExtractor.getSampleTime(), 0);
                                mediaExtractor.advance();
                            }
                        }

                        // Get output from decoder (CPU-accessible buffer)
                        int outputBufferId = mediaCodec.dequeueOutputBuffer(bufferInfo, timeoutUs);
                        if (outputBufferId >= 0) {
                            // Get the decoded frame as an Image
                            Image image = mediaCodec.getOutputImage(outputBufferId);
                            
                            if (image != null) {
                                try {
                                    // Recreate bitmap if dimensions changed
                                    if (reusableBitmap == null || 
                                        reusableBitmap.getWidth() != image.getWidth() || 
                                        reusableBitmap.getHeight() != image.getHeight()) {
                                        if (reusableBitmap != null) reusableBitmap.recycle();
                                        reusableBitmap = Bitmap.createBitmap(
                                            image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
                                        pixelBuffer = new int[image.getWidth() * image.getHeight()];
                                    }
                                    
                                    // Convert YUV to RGB
                                    yuv420ToArgb(image, reusableBitmap, pixelBuffer);

                                    // Draw to canvas
                                    Canvas canvas = null;
                                    try {
                                        canvas = surfaceHolder.lockCanvas();
                                        if (canvas != null) {
                                            canvas.drawColor(android.graphics.Color.BLACK);
                                            drawBitmapWithAspectRatio(canvas, reusableBitmap);
                                        }
                                    } finally {
                                        if (canvas != null) surfaceHolder.unlockCanvasAndPost(canvas);
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error processing video frame", e);
                                } finally {
                                    try { image.close(); } catch (Exception ignore) {}
                                }
                            }
                            
                            // Release output buffer
                            mediaCodec.releaseOutputBuffer(outputBufferId, false);
                            
                            // Frame rate control - maintain original video speed
                            long now = System.currentTimeMillis();
                            long elapsed = now - lastFrameTime;
                            long sleepTime = frameDelayMs - elapsed;
                            if (sleepTime > 0) {
                                try { 
                                    Thread.sleep(sleepTime); 
                                } catch (InterruptedException e) { 
                                    break; 
                                }
                            }
                            lastFrameTime = System.currentTimeMillis();
                        } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            MediaFormat newFormat = mediaCodec.getOutputFormat();
                            Log.d(TAG, "Output format changed: " + newFormat);
                        }

                        // Handle end of stream for looping
                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            Log.d(TAG, "Video ended, looping.");
                            mediaExtractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                            mediaCodec.flush(); // CRITICAL: Reset decoder state for looping
                            lastFrameTime = System.currentTimeMillis(); // Reset timing
                        }
                    }
                } catch (Exception e) {
                    if (filePlaybackRunning) { // Don't show error if stopped manually
                        Log.e(TAG, "Error during MediaCodec playback", e);
                        runOnUiThread(() -> Toast.makeText(VideoDemoActivity.this, 
                            "Failed to play video: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                } finally {
                    releaseVideoResources();
                }
            }, "VideoPlaybackThread");
            filePlaybackThread.start();
        }
    }
    
    private void drawBitmapWithAspectRatio(Canvas canvas, Bitmap bitmap) {
        int canvasWidth = canvas.getWidth();
        int canvasHeight = canvas.getHeight();
        float bitmapRatio = (float) bitmap.getWidth() / bitmap.getHeight();
        float canvasRatio = (float) canvasWidth / canvasHeight;
        int destWidth, destHeight;

        if (bitmapRatio > canvasRatio) {
            // Fit by width
            destWidth = canvasWidth;
            destHeight = (int) (canvasWidth / bitmapRatio);
        } else {
            // Fit by height
            destHeight = canvasHeight;
            destWidth = (int) (canvasHeight * bitmapRatio);
        }
        
        int left = (canvasWidth - destWidth) / 2;
        int top = (canvasHeight - destHeight) / 2;
        Rect dest = new Rect(left, top, left + destWidth, top + destHeight);
        canvas.drawBitmap(bitmap, null, dest, null);
    }
    
    private void releaseVideoResources() {
        try {
            if (mediaCodec != null) {
                try {
                    mediaCodec.stop();
                } catch (Exception e) {
                    Log.w(TAG, "Error stopping mediaCodec", e);
                }
                try {
                    mediaCodec.release();
                } catch (Exception e) {
                    Log.w(TAG, "Error releasing mediaCodec", e);
                }
                mediaCodec = null;
            }
            if (mediaExtractor != null) {
                try {
                    mediaExtractor.release();
                } catch (Exception e) {
                    Log.w(TAG, "Error releasing mediaExtractor", e);
                }
                mediaExtractor = null;
            }
            // Note: fileImageReader is no longer used (buffer mode instead of surface mode)
        } catch (Exception e) {
            Log.e(TAG, "Error releasing video resources", e);
        }
    }

    private void startFilePlaybackWithMediaPlayer(@NonNull Uri uri) {
        // This method is now unused, but kept for reference or future fallback.
        // The primary method is startFilePlaybackWithMediaCodec
    }

    // Extracted OpenCV-based file playback into its own method (uses cached file path if necessary)
    private void startFilePlaybackWithOpenCV(@NonNull Uri uri) {
        // OpenCV CANNOT handle content:// URIs or scoped storage paths
        // Only use for direct file:// paths
        String scheme = uri.getScheme();
        if ("content".equalsIgnoreCase(scheme)) {
            Log.e(TAG, "Cannot use OpenCV for content URIs - scoped storage not supported");
            runOnUiThread(() -> Toast.makeText(this, 
                "Video format not supported. Please try a different file.", Toast.LENGTH_LONG).show());
            return;
        }
        
        // Resolve content Uri to a readable path; if not directly readable, copy to cache
        String path = UriUtils.resolveToPathOrCopyToCache(this, uri);
        if (path == null) {
            runOnUiThread(() -> Toast.makeText(this, "Unable to open selected file", Toast.LENGTH_SHORT).show());
            return;
        }
        
        // Additional safety check - avoid scoped storage paths
        if (path.contains(".transforms/synthetic") || path.contains("picker_get_content")) {
            Log.e(TAG, "Scoped storage path detected, OpenCV cannot access: " + path);
            runOnUiThread(() -> Toast.makeText(this, 
                "Cannot access file from this location. MediaPlayer required but failed.", Toast.LENGTH_LONG).show());
            return;
        }

        synchronized (filePlaybackLock) {
            // Ensure black canvas before starting
            clearSurfaceCanvas();
            filePlaybackRunning = true;
            filePlaybackThread = new Thread(() -> {
                // Resolve file and check availability
                File videoFile = new File(path);
                if (!videoFile.exists() || !videoFile.canRead()) {
                    runOnUiThread(() -> Toast.makeText(VideoDemoActivity.this, "Selected file not readable: " + path, Toast.LENGTH_SHORT).show());
                    filePlaybackRunning = false;
                    return;
                }
                
                Log.d(TAG, "Starting OpenCV playback for file: " + path);

                VideoCapture capture = new VideoCapture();
                Mat frame = new Mat();
                Bitmap bitmap = null;

                try {
                    boolean opened = false;
                    try {
                        opened = capture.open(path);
                    } catch (Throwable t) {
                        Log.e(TAG, "VideoCapture.open threw exception", t);
                        opened = false;
                    }

                    if (!opened || !capture.isOpened()) {
                        runOnUiThread(() -> Toast.makeText(VideoDemoActivity.this, "Failed to open video with OpenCV", Toast.LENGTH_SHORT).show());
                        filePlaybackRunning = false;
                        return;
                    }
                    
                    // Get video properties
                    double fps = capture.get(org.opencv.videoio.Videoio.CAP_PROP_FPS);
                    if (fps <= 0 || fps > 120) fps = 30; // default to 30 fps if invalid
                    long frameDelayMs = (long)(1000.0 / fps);
                    Log.d(TAG, "Video FPS: " + fps + ", frame delay: " + frameDelayMs + "ms");

                    int consecutiveNullFrames = 0;
                    final int MAX_NULL_FRAMES = 10;
                    long lastFrameTime = System.currentTimeMillis();

                    while (filePlaybackRunning) {
                        boolean readOk = false;
                        try {
                            readOk = capture.read(frame);
                        } catch (Throwable t) {
                            Log.e(TAG, "VideoCapture.read threw exception", t);
                            break;
                        }
                        
                        if (!readOk || frame.empty()) {
                            consecutiveNullFrames++;
                            if (consecutiveNullFrames > MAX_NULL_FRAMES) {
                                // Reached end of video, loop back to start
                                Log.d(TAG, "Reached end of video, looping...");
                                capture.set(org.opencv.videoio.Videoio.CAP_PROP_POS_FRAMES, 0);
                                consecutiveNullFrames = 0;
                            }
                            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                            continue;
                        }
                        consecutiveNullFrames = 0;
                        
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
                                canvas.drawColor(android.graphics.Color.BLACK);

                                int canvasWidth = canvas.getWidth();
                                int canvasHeight = canvas.getHeight();
                                int bitmapWidth = bitmap.getWidth();
                                int bitmapHeight = bitmap.getHeight();

                                float bitmapRatio = (float) bitmapWidth / bitmapHeight;
                                float canvasRatio = (float) canvasWidth / canvasHeight;

                                int destWidth, destHeight;
                                if (bitmapRatio > canvasRatio) {
                                    // Fit by width
                                    destWidth = canvasWidth;
                                    destHeight = (int) (canvasWidth / bitmapRatio);
                                } else {
                                    // Fit by height
                                    destHeight = canvasHeight;
                                    destWidth = (int) (canvasHeight * bitmapRatio);
                                }

                                int left = (canvasWidth - destWidth) / 2;
                                int top = (canvasHeight - destHeight) / 2;

                                android.graphics.Rect dest = new android.graphics.Rect(
                                        left,
                                        top,
                                        left + destWidth,
                                        top + destHeight
                                );

                                canvas.drawBitmap(bitmap, null, dest, null);
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Error drawing frame", e);
                        } finally {
                            if (canvas != null) surfaceHolder.unlockCanvasAndPost(canvas);
                        }
                        
                        // Frame rate control
                        long now = System.currentTimeMillis();
                        long elapsed = now - lastFrameTime;
                        long sleepTime = frameDelayMs - elapsed;
                        if (sleepTime > 0) {
                            try { Thread.sleep(sleepTime); } catch (InterruptedException ignored) {}
                        }
                        lastFrameTime = System.currentTimeMillis();
                    }

                } catch (Throwable t) {
                    Log.e(TAG, "Unhandled error during file playback", t);
                    runOnUiThread(() -> Toast.makeText(VideoDemoActivity.this, "Playback error: " + t.getMessage(), Toast.LENGTH_SHORT).show());
                } finally {
                    try { if (bitmap != null) bitmap.recycle(); } catch (Throwable ignored) {}
                    try { frame.release(); } catch (Throwable ignored) {}
                    try { if (capture != null) capture.release(); } catch (Throwable ignored) {}
                    filePlaybackRunning = false;
                    Log.d(TAG, "OpenCV playback stopped");
                }
            }, "FilePlaybackThread");
            filePlaybackThread.start();
        }
    }

    private void stopFilePlayback() {
        synchronized (filePlaybackLock) {
            if (!filePlaybackRunning && filePlaybackThread == null) {
                return; // Already stopped
            }
            
            filePlaybackRunning = false;
            
            if (filePlaybackThread != null) {
                filePlaybackThread.interrupt();
                try {
                    filePlaybackThread.join(500); // Wait for thread to finish
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.w(TAG, "Interrupted while waiting for playback thread to stop");
                }
                filePlaybackThread = null;
            }

            // Release all video resources
            releaseVideoResources();

            // Stop MediaPlayer if it was somehow used (legacy fallback)
            try {
                if (mediaPlayer != null) {
                    try { mediaPlayer.stop(); } catch (Exception ignore) {}
                    try { mediaPlayer.reset(); } catch (Exception ignore) {}
                    try { mediaPlayer.release(); } catch (Exception ignore) {}
                    mediaPlayer = null;
                }
            } catch (Exception ignore) {}
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
        stopRtspStreaming();
        clearSurfaceCanvas();
    }
    
    private void startRtspStreaming(String rtspUrl) {
        if (rtspStreamingService != null) {
            // Test network connectivity first
            rtspStreamingService.testNetworkConnectivity(rtspUrl);
            // Test connection
            rtspStreamingService.testConnection(rtspUrl);
            // Start streaming
            rtspStreamingService.startStreaming(rtspUrl);
        }
    }
    
    private void stopRtspStreaming() {
        if (rtspStreamingService != null) {
            rtspStreamingService.stopStreaming();
        }
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
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
