package com.smartprints.rknn_vision_lab.cam;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.elvishew.xlog.XLog;

import java.util.Collections;

public class CameraAPI {

    private static final String TAG = "CameraAPI";

    private final Context context;
    private final ImageReader imageReader;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;

    private Integer sensorOrientationDegrees;
    private Integer lensFacing;
    private int frameRotationDegrees = 0;
    private int sessionGeneration = 0;

    public CameraAPI(Context context, ImageReader imageReader) {
        this.context = context;
        this.imageReader = imageReader;
    }

    public void openCamera(String cameraId) {
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            // If a camera is already open, close only the device/session (keep background thread)
            if (cameraDevice != null || captureSession != null) {
                closeCameraOnly();
            }
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);

            sensorOrientationDegrees = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);

            frameRotationDegrees = computeFrameRotationDegrees();

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(context, "Camera permission missing", Toast.LENGTH_SHORT).show();
                return;
            }

            if (backgroundHandler == null) startBackgroundThread();

            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    startCameraSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    closeCamera();
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    closeCamera();
                    Toast.makeText(context, "Camera error: " + error, Toast.LENGTH_SHORT).show();
                }
            }, backgroundHandler);

        } catch (CameraAccessException e) {
            XLog.e(TAG, "Failed to open camera: " + e.getMessage());
            stopBackgroundThread();
        }
    }

    private void startCameraSession() {
        try {
            Surface surface = imageReader.getSurface();
            final CaptureRequest.Builder previewRequestBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);
            final int currentGeneration = ++sessionGeneration;

            cameraDevice.createCaptureSession(Collections.singletonList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            // Ignore if camera was closed or superseded
                            if (cameraDevice == null || backgroundHandler == null || currentGeneration != sessionGeneration) {
                                try { session.close(); } catch (Exception ignore) {}
                                return;
                            }
                            captureSession = session;
                            try {
                                previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                                session.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
                                XLog.d("Camera capture session has started");
                            } catch (CameraAccessException e) {
                                XLog.e(TAG, "Failed to start camera preview: " + e.getMessage());
                            } catch (IllegalStateException ise) {
                                // Session became invalid
                                XLog.e(TAG, "Session invalid when starting preview: " + ise.getMessage());
                                try { session.close(); } catch (Exception ignore) {}
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Toast.makeText(context, "Camera configuration failed", Toast.LENGTH_SHORT).show();
                        }
                    }, backgroundHandler);

        } catch (CameraAccessException e) {
            XLog.e(TAG, "Failed to start session: " + e.getMessage());
        }
    }

    /** Correctly compute frame rotation based on sensor and display orientation **/
    private int computeFrameRotationDegrees() {
        int deviceRotationDegrees = getDisplayRotationDegrees();
        int sensor = sensorOrientationDegrees != null ? sensorOrientationDegrees : 0;
        int facing = lensFacing != null ? lensFacing : CameraCharacteristics.LENS_FACING_BACK;

        if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
            // Front camera rotates in the same direction as display
            return (sensor + deviceRotationDegrees) % 360;
        } else {
            // Back camera rotates opposite to display
            return (sensor - deviceRotationDegrees + 360) % 360;
        }
    }

    private int getDisplayRotationDegrees() {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null || wm.getDefaultDisplay() == null) return 0;
        int rotation = wm.getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_90: return 90;
            case Surface.ROTATION_180: return 180;
            case Surface.ROTATION_270: return 270;
            case Surface.ROTATION_0:
            default: return 0;
        }
    }

    public int getFrameRotationDegrees() {
        return frameRotationDegrees;
    }

    public void stopCamera() {
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
            sessionGeneration++;
        } catch (Exception e) {
            XLog.e(TAG, "Error closing camera", e);
        }
    }

    /** Close only the current camera device and session without stopping background thread */
    private void closeCameraOnly() {
        try {
            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            sessionGeneration++;
        } catch (Exception e) {
            XLog.e(TAG, "Error closing camera (device only)", e);
        }
    }

    /** Switch to a different cameraId without tearing down the background thread */
    public void reopenCamera(String cameraId) {
        // Ensure background thread is running
        if (backgroundHandler == null) startBackgroundThread();
        closeCameraOnly();
        openCamera(cameraId);
    }

    public void startBackgroundThread() {
        if (backgroundThread != null && backgroundThread.isAlive()) return;
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
            } catch (InterruptedException e) {
                XLog.e(e.getMessage());
            } finally {
                backgroundThread = null;
                backgroundHandler = null;
            }
        }
    }

    public Handler getBackgroundHandler() {
        return backgroundHandler;
    }
}
