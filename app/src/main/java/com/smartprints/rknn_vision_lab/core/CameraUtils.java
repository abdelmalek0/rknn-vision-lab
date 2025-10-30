package com.smartprints.rknn_vision_lab.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.Image;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.elvishew.xlog.XLog;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class CameraUtils {

    public static void yuv420ToArgb(Image image, Bitmap bitmap, int[] pixelBuffer) {
        if (image.getFormat() != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException("Image must be in YUV_420_888 format");
        }

        int width = image.getWidth();
        int height = image.getHeight();

        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        yBuffer.position(0);
        uBuffer.position(0);
        vBuffer.position(0);

        int yRowStride = planes[0].getRowStride();
        int yPixelStride = planes[0].getPixelStride();
        int uvRowStride = planes[1].getRowStride();
        int uvPixelStride = planes[1].getPixelStride();

        int offset = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int yVal = (yBuffer.get(y * yRowStride + x * yPixelStride) & 0xFF);

                int uvX = x / 2;
                int uvY = y / 2;
                int uvIndex = uvY * uvRowStride + uvX * uvPixelStride;

                int uVal = (uBuffer.get(uvIndex) & 0xFF) - 128;
                int vVal = (vBuffer.get(uvIndex) & 0xFF) - 128;

                float rFloat = yVal + 1.402f * vVal;
                float gFloat = yVal - 0.34414f * uVal - 0.71414f * vVal;
                float bFloat = yVal + 1.772f * uVal;

                int r = (int) Math.max(0, Math.min(255, rFloat));
                int g = (int) Math.max(0, Math.min(255, gFloat));
                int b = (int) Math.max(0, Math.min(255, bFloat));

                pixelBuffer[offset++] = (0xFF << 24) | (r << 16) | (g << 8) | b;
            }
        }

        bitmap.setPixels(pixelBuffer, 0, width, 0, 0, width, height);
    }
    public static List<String> getAvailableCameras(@NonNull Context context) {
        List<String> cameraList = new ArrayList<>();
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) return cameraList;

            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics chars = manager.getCameraCharacteristics(id);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);

                if (facing == null ||
                        facing == CameraCharacteristics.LENS_FACING_BACK ||
                        facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    cameraList.add(id);
                    continue;
                }

                int[] caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                if (caps != null) {
                    for (int cap : caps) {
                        if (cap == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                            cameraList.add(id);
                            break;
                        }
                    }
                }
            }
        } catch (CameraAccessException e) {
            XLog.e("Camera access error: " + e.getMessage());
        }

        return new ArrayList<>(new LinkedHashSet<>(cameraList));
    }



    public static int computeFrameRotationDegrees(Context context, CameraManager manager, String cameraId) {
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            int sensor = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            int displayRotation = getDisplayRotationDegrees(context);

            return (facing == CameraCharacteristics.LENS_FACING_FRONT)
                    ? (sensor + displayRotation) % 360
                    : (sensor - displayRotation + 360) % 360;
        } catch (Exception e) {
            XLog.e("Rotation compute error: " + e.getMessage());
            return 0;
        }
    }

    private static int getDisplayRotationDegrees(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null || wm.getDefaultDisplay() == null) return 0;

        switch (wm.getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_90:  return 90;
            case Surface.ROTATION_180: return 180;
            case Surface.ROTATION_270: return 270;
            default:                   return 0;
        }
    }
}
