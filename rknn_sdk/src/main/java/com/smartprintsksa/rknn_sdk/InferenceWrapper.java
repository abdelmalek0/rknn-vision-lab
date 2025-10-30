package com.smartprintsksa.rknn_sdk;

import static com.smartprintsksa.rknn_sdk.Processor.OBJ_NUMB_MAX_SIZE;

import android.graphics.Rect;

import com.smartprintsksa.rknn_sdk.structs.DetectedObject;
import com.smartprintsksa.rknn_sdk.structs.YoloDetections;
import com.smartprintsksa.rknn_sdk.structs.YoloRawOutput;

import java.io.IOException;
import java.util.ArrayList;


public class InferenceWrapper {
    private final String TAG = "INFERENCE WRAPPER";

    static {
        System.loadLibrary("rknn4j");
    }

    private YoloRawOutput mOutputs;
    private YoloDetections mDetections;

    public void initYolo(int imageHeight, int imageWidth, int numChannels, String modelPath) throws Exception {
        mOutputs = new YoloRawOutput();
        mOutputs.mGrid0Out = new byte[255 * 80 * 80 * 4];
        mOutputs.mGrid1Out = new byte[255 * 40 * 40 * 4];
        mOutputs.mGrid2Out = new byte[255 * 20 * 20 * 4];
        if (native_init_yolo(imageHeight, imageWidth, numChannels, modelPath) != 0) {
            Logger.error(TAG, "rknn init fail!");
            throw new IOException("rknn init fail!");
        }
    }

    public void deinit() {
        native_de_init_yolo();
        mOutputs.mGrid0Out = null;
        mOutputs.mGrid1Out = null;
        mOutputs.mGrid2Out = null;
        mOutputs = null;
    }

    public YoloRawOutput run(byte[] inData) {
        native_run_yolo(inData, mOutputs.mGrid0Out, mOutputs.mGrid1Out, mOutputs.mGrid2Out);
        return  mOutputs;
    }

    public ArrayList<DetectedObject> postProcess(YoloRawOutput outputs) {
        ArrayList<DetectedObject> recognitions = new ArrayList<DetectedObject>();

        mDetections = new YoloDetections();
        mDetections.count = 0;
        mDetections.ids = new int[OBJ_NUMB_MAX_SIZE];
        mDetections.scores = new float[OBJ_NUMB_MAX_SIZE];
        mDetections.boxes = new float[4 * OBJ_NUMB_MAX_SIZE];

        if (outputs == null || outputs.mGrid0Out == null || outputs.mGrid1Out == null || outputs.mGrid2Out == null) {
            return recognitions;
        }

        int count = native_post_process_yolo(outputs.mGrid0Out, outputs.mGrid1Out, outputs.mGrid2Out,
                mDetections.ids, mDetections.scores, mDetections.boxes);
        if (count < 0) {
            Logger.info(TAG, "post_process may fail.");
            mDetections.count = 0;
        } else {
            mDetections.count = count;
        }

        for (int i = 0; i < count; ++i) {
            Rect rect = new Rect();
            rect.left = (int) mDetections.boxes[i * 4];
            rect.top = (int) mDetections.boxes[i*4+1];
            rect.right = (int) mDetections.boxes[i*4+2];
            rect.bottom = (int) mDetections.boxes[i*4+3];

            DetectedObject recognition = new DetectedObject(rect, mDetections.scores[i], mDetections.ids[i]);
            recognitions.add(recognition);
        }

        return recognitions;
    }

    private native int native_init_yolo(int im_height, int im_width, int im_channel, String modelPath);
    private native void native_de_init_yolo();
    private native int native_run_yolo(byte[] inData, byte[] grid0Out, byte[] grid1Out, byte[] grid2Out);
    private native int native_post_process_yolo(byte[] grid0Out, byte[] grid1Out, byte[] grid2Out,
                                                int[] ids, float[] scores, float[] boxes);
}