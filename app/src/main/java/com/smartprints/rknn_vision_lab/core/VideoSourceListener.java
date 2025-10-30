package com.smartprints.rknn_vision_lab.core;

import android.graphics.Bitmap;

public interface VideoSourceListener {
    void onFrameReady(Bitmap frame);
    void onStreamError(String error);
    void onStreamFinished();
}
