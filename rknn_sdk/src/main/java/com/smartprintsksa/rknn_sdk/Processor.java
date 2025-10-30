package com.smartprintsksa.rknn_sdk;

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.graphics.Bitmap;
import android.graphics.Rect;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Processor {
    public final static int CHANNELS = 3;
    public final static int BYTES_SIZE = 4;

    public final static int YOLO_INPUT = 320;
    public final static int OBJ_NUMB_MAX_SIZE = 64;

    public final static int CLASSIFIER_INPUT = 224;

    /**
     * Converts a Bitmap to a byte array.
     *
     * @param bitmap The Bitmap to convert.
     * @return A byte array representing the Bitmap.
     */
    public static byte[] convertBitmapToByteArray(Bitmap bitmap) {
        int byteCount = bitmap.getByteCount();
        ByteBuffer buffer = ByteBuffer.allocate(byteCount);
        bitmap.copyPixelsToBuffer(buffer);
        return buffer.array();
    }

    private static ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap, Boolean quant, int inputSize) {
        ByteBuffer byteBuffer;

        if (quant) {
            byteBuffer = ByteBuffer.allocateDirect(inputSize * inputSize * Processor.CHANNELS);
        } else {
            byteBuffer = ByteBuffer.allocateDirect(BYTES_SIZE * inputSize * inputSize * Processor.CHANNELS);
        }

        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[inputSize * inputSize];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int pixel = 0;
        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                final int val = intValues[pixel++];
                if (quant) {
                    byteBuffer.put((byte) ((val >> 16) & 0xFF));
                    byteBuffer.put((byte) ((val >> 8) & 0xFF));
                    byteBuffer.put((byte) (val & 0xFF));
                } else {

                    byteBuffer.putFloat((((val >> 16) & 0xFF)) * 1.0f );
                    byteBuffer.putFloat((((val >> 8) & 0xFF))* 1.0f );
                    byteBuffer.putFloat((((val) & 0xFF))* 1.0f );

                }
            }
        }
        return byteBuffer;
    }

    /**
     * Extracts a bounding box from a Bitmap and resizes it to the specified target size.
     *
     * @param bitmap The Bitmap to extract the bounding box from.
     * @param rect   The bounding box defined by a Rect object.
     * @param targetSize The size to which the extracted bounding box should be resized.
     * @return A Bitmap containing the extracted and resized bounding box.
     */
    public static Bitmap boundingBoxExtractor(Bitmap bitmap, Rect rect, int targetSize) {
        if (rect.bottom > rect.top && rect.right > rect.left ) {
            double x = max(0, rect.left);
            double y = max(0, rect.top);
            bitmap = Bitmap.createBitmap(bitmap,
                    (int) x, (int)  y,
                    (int) (min(bitmap.getWidth(), rect.right )-   x),
                    (int) (min(bitmap.getHeight(), rect.bottom )- y));
        }
        return Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true);
    }

    public static void scaleBoundingBox(Rect rect, float scaleX, float scaleY) {
        rect.top = (int) (scaleY * rect.top);
        rect.left = (int) (scaleX * rect.left);
        rect.bottom = (int) (scaleY * rect.bottom);
        rect.right = (int) (scaleX * rect.right);
    }

    public static void scaleBoundingBoxToUI(Rect rect, float startX, float startY, float scaleX, float scaleY) {
        rect.top = (int) (startY + scaleY * rect.top);
        rect.left = (int) (startX + scaleX * rect.left);
        rect.bottom = (int) (startY + scaleY * rect.bottom);
        rect.right = (int) (startX + scaleX * rect.right);
    }
}
