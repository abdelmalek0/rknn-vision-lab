package com.smartprints.rknn_vision_lab.streaming;

import android.graphics.Bitmap;

public interface VideoSourceListener {
    void onFrameReady(Bitmap bitmap);
    void onStreamError(String error);
}
