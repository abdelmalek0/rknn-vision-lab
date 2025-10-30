package com.smartprints.rknn_vision_lab.inference;

import android.graphics.Bitmap;

// Minimal adapter for NPU/RKNN inference; replace internals with your actual model code
public class RknnInferenceEngine implements InferenceEngine {

    public RknnInferenceEngine() {
        // TODO: initialize RKNN model/resources here
    }

    @Override
    public Bitmap process(Bitmap input) {
        // TODO: run inference and draw overlays onto a mutable bitmap
        return input; // Pass-through placeholder
    }

    @Override
    public void close() {
        // TODO: release RKNN resources here
    }
}
