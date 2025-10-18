package com.smartprints.rknn_vision_lab;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.Image;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class CameraUtils {

    public static List<String> getAvailableCameras(@NonNull Context context) {
        List<String> cameraList = new ArrayList<>();
        try {
            CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            String[] cameraIds = cameraManager.getCameraIdList();

            for (String id : cameraIds) {
                CameraCharacteristics chars = cameraManager.getCameraCharacteristics(id);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);

                // Some external cameras may not have LENS_FACING property
                if (facing == null) {
                    cameraList.add(id);
                } else if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraList.add(id);
                } else if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    cameraList.add(id);
                } else if (chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) != null) {
                    int[] caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                    assert caps != null;
                    for (int cap : caps) {
                        if (cap == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                            cameraList.add(id);
                            break;
                        }
                    }
                }
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        // Remove duplicates
        return new ArrayList<>(new java.util.LinkedHashSet<>(cameraList));
    }

    /**
     * Convert a YUV_420_888 Image to ARGB and write into provided Bitmap using pixelBuffer.
     * This implementation reads Y, U, V planes and performs a per-pixel conversion.
     * It's not the absolute fastest (native/libyuv would be faster) but avoids
     * compressToJpeg allocations and should reduce GC pressure.
     */
    public static void yuv420ToArgb(Image image, Bitmap targetBitmap, int[] pixelBuffer) {
        if (image == null) return;
        final int width = image.getWidth();
        final int height = image.getHeight();

        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuf = planes[0].getBuffer();
        ByteBuffer uBuf = planes[1].getBuffer();
        ByteBuffer vBuf = planes[2].getBuffer();

        final int yRowStride = planes[0].getRowStride();
        final int uvRowStride = planes[1].getRowStride();
        final int uvPixelStride = planes[1].getPixelStride();

        // Copy Y values into a temp array for faster indexed access
        byte[] yBytes = new byte[yBuf.remaining()];
        yBuf.get(yBytes);

        byte[] uBytes = new byte[uBuf.remaining()];
        uBuf.get(uBytes);

        byte[] vBytes = new byte[vBuf.remaining()];
        vBuf.get(vBytes);

        int index = 0;
        for (int row = 0; row < height; row++) {
            int yRowStart = row * yRowStride;
            int uvRowStart = (row / 2) * uvRowStride;
            for (int col = 0; col < width; col++) {
                int y = yBytes[yRowStart + col] & 0xff;

                int uvOffset = uvRowStart + (col / 2) * uvPixelStride;
                int u = uBytes[uvOffset] & 0xff;
                int v = vBytes[uvOffset] & 0xff;

                // YUV to RGB (BT.601)
                int c = y - 16;
                int d = u - 128;
                int e = v - 128;

                int r = (298 * c + 409 * e + 128) >> 8;
                int g = (298 * c - 100 * d - 208 * e + 128) >> 8;
                int b = (298 * c + 516 * d + 128) >> 8;

                r = r < 0 ? 0 : (r > 255 ? 255 : r);
                g = g < 0 ? 0 : (g > 255 ? 255 : g);
                b = b < 0 ? 0 : (b > 255 ? 255 : b);

                pixelBuffer[index++] = 0xff000000 | (r << 16) | (g << 8) | b;
            }
        }

        targetBitmap.setPixels(pixelBuffer, 0, width, 0, 0, width, height);
    }

}
