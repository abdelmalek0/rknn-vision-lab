package com.smartprints.rknn_vision_lab.core;

import android.os.Handler;
import android.os.HandlerThread;

import com.elvishew.xlog.XLog;

public abstract class VideoSource {
    private static final String TAG = "VideoSource";
    protected Handler backgroundHandler;
    protected HandlerThread backgroundThread;
    public abstract void start();
    public abstract void stop();
    public abstract boolean isRunning();

    protected void startBackgroundThread() {
        if (backgroundThread != null && backgroundThread.isAlive()) return;
        backgroundThread = new HandlerThread("CameraBackground");
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
