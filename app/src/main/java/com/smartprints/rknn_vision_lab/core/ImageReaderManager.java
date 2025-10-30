package com.smartprints.rknn_vision_lab.core;

import static com.smartprints.rknn_vision_lab.core.CameraUtils.yuv420ToArgb;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import com.elvishew.xlog.XLog;

public class ImageReaderManager implements ImageReader.OnImageAvailableListener {
    private final String TAG = "ImageReaderManager";
    private VideoSourceListener videoSourceListener;
    private ImageReader imageReader;
    private float frameRotationDegrees = 0;
    private Bitmap reusableBitmap;
    private int[] pixelBuffer;
    protected Handler backgroundHandler;
    protected HandlerThread backgroundThread;
    public ImageReaderManager(VideoSourceListener videoSourceListener, int frameWidth, int frameHeight, float frameRotationDegrees){
        startBackgroundThread();
        this.frameRotationDegrees = frameRotationDegrees;
        this.videoSourceListener = videoSourceListener;
        imageReader = ImageReader.newInstance(frameWidth, frameHeight, ImageFormat.YUV_420_888, 2);
        imageReader.setOnImageAvailableListener(this, this.backgroundHandler);
    }

    public Surface getSurface(){
        return imageReader.getSurface();
    }

    @Override
    public void onImageAvailable(ImageReader imageReader) {
        Image image = imageReader.acquireLatestImage();

        try (image) {
            try {
                if (image == null) return;

                int width = image.getWidth();
                int height = image.getHeight();
                if (width > 2500) {
                    android.graphics.Rect crop = image.getCropRect();
                    width = crop.width();
                    height = crop.height();
                }

                if (reusableBitmap == null
                        || reusableBitmap.getWidth() != width
                        || reusableBitmap.getHeight() != height) {
                    reusableBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    pixelBuffer = new int[width * height];
                }

                yuv420ToArgb(image, reusableBitmap, pixelBuffer);

                if (frameRotationDegrees != 0) {
                    Matrix matrix = new Matrix();
                    matrix.postRotate(frameRotationDegrees);
                    Bitmap rotated = Bitmap.createBitmap(
                            reusableBitmap, 0, 0, width, height, matrix, false
                    );
                    videoSourceListener.onFrameReady(rotated);
                } else {
                    videoSourceListener.onFrameReady(reusableBitmap);
                }

            } catch (Exception e) {
                XLog.e(TAG, "Frame conversion error: " + e.getMessage());
                videoSourceListener.onStreamError(e.getMessage());
            }
        } catch (Exception ignore) {}
    }

    public void close(){
        if ( imageReader != null ) {
            imageReader.close();
            imageReader = null;
        }
        stopBackgroundThread();
    }

    protected void startBackgroundThread() {
        if (backgroundThread != null && backgroundThread.isAlive()) return;
        backgroundThread = new HandlerThread("ImageReaderBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
        if (backgroundThread == null) return;
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
        } catch (InterruptedException e) {
            XLog.e(TAG, e.getMessage());
        } finally {
            backgroundThread = null;
            backgroundHandler = null;
        }
    }
}
