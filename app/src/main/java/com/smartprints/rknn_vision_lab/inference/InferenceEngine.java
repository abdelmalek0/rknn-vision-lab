package com.smartprints.rknn_vision_lab.inference;

import android.graphics.Bitmap;

public interface InferenceEngine {
    // Process input frame and return a frame for display (can be same instance)
    Bitmap process(Bitmap input);

    void close();
}
