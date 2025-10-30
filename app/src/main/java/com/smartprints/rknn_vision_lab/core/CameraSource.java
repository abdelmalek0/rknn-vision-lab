package com.smartprints.rknn_vision_lab.core;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.camera2.*;
import android.view.Surface;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.elvishew.xlog.XLog;

import java.util.Collections;

public class CameraSource extends VideoSource {

    private static final String TAG = "CameraSource";

    private final Context context;
    private final VideoSourceListener videoSourceListener;
    private final String cameraId;

    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReaderManager imageReader;
    private boolean isRunning = false;
    private int frameRotationDegrees = 0;
    private final int frameWidth = 1280;
    private final int frameHeight = 720;

    public CameraSource(Context context, VideoSourceListener listener, String cameraId) {
        this.context = context;
        this.videoSourceListener = listener;
        this.cameraId = cameraId;
    }

    @Override
    public void start() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "Camera permission missing", Toast.LENGTH_SHORT).show();
            return;
        }

        startBackgroundThread();

        //ImageReader.newInstance(frameWidth, frameHeight, ImageFormat.YUV_420_888, 2);
//        imageReader.setOnImageAvailableListener(this, backgroundHandler);

        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        frameRotationDegrees = CameraUtils.computeFrameRotationDegrees(context, manager, cameraId);
        imageReader = new ImageReaderManager(videoSourceListener, frameWidth, frameHeight, frameRotationDegrees);

        try {
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    isRunning = true;
                    startCameraSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    closeCamera();
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    XLog.e(TAG, "Camera error: " + error);
                    closeCamera();
                    videoSourceListener.onStreamError("Camera error " + error);
                }
            }, backgroundHandler);

        } catch (CameraAccessException e) {
            XLog.e(TAG, "Failed to open camera: " + e.getMessage());
            videoSourceListener.onStreamError(e.getMessage());
            stopBackgroundThread();
        }
    }

    private void startCameraSession() {
        try {

            Surface surface = imageReader.getSurface();
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(surface);
            builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            cameraDevice.createCaptureSession(Collections.singletonList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (cameraDevice == null) return;
                            captureSession = session;
                            try {
                                session.setRepeatingRequest(builder.build(), null, backgroundHandler);
                                XLog.d(TAG, "Camera session started");
                            } catch (CameraAccessException e) {
                                XLog.e(TAG, "Failed to start preview: " + e.getMessage());
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            XLog.e(TAG, "Camera configuration failed");
                            Toast.makeText(context, "Camera config failed", Toast.LENGTH_SHORT).show();
                        }
                    }, backgroundHandler);

        } catch (CameraAccessException e) {
            XLog.e(TAG, "Failed to start session: " + e.getMessage());
            videoSourceListener.onStreamError(e.getMessage());
        }
    }

//    @Override
//    public void onImageAvailable(ImageReader reader) {
//        Image image = reader.acquireLatestImage();
//
//        try (image) {
//            try {
//                if (image == null) return;
//                int width = image.getWidth();
//                int height = image.getHeight();
//
//                if (reusableBitmap == null
//                        || reusableBitmap.getWidth() != width
//                        || reusableBitmap.getHeight() != height) {
//                    reusableBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
//                    pixelBuffer = new int[width * height];
//                }
//
//                yuv420ToArgb(image, reusableBitmap, pixelBuffer);
//
//                if (frameRotationDegrees != 0) {
//                    Matrix matrix = new Matrix();
//                    matrix.postRotate(frameRotationDegrees);
//                    Bitmap rotated = Bitmap.createBitmap(
//                            reusableBitmap, 0, 0, width, height, matrix, false
//                    );
//                    videoSourceListener.onFrameReady(rotated);
//                } else {
//                    videoSourceListener.onFrameReady(reusableBitmap);
//                }
//
//            } catch (Exception e) {
//                XLog.e(TAG, "Frame conversion error: " + e.getMessage());
//                videoSourceListener.onStreamError(e.getMessage());
//            }
//        } catch (Exception ignore) {
//        }
//    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public void stop() {
        closeCamera();
        stopBackgroundThread();
    }

    private void closeCamera() {
        try {
            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
        } catch (Exception e) {
            XLog.e(TAG, "Error closing camera", e);
        } finally {
            isRunning = false;
        }
    }
}