package com.smartprintsksa.rknn_sdk;

import java.io.IOException;
import java.util.ArrayList;
import static java.lang.System.arraycopy;

import com.smartprintsksa.rknn_sdk.structs.DetectedObject;
import com.smartprintsksa.rknn_sdk.structs.YoloRawOutput;

public class InferenceResult {

    public YoloRawOutput mYoloRawOutput;
    public ArrayList<DetectedObject> recognitions = null;
    private boolean mIsVaild = false;

    public void init() throws IOException {
        mYoloRawOutput = new YoloRawOutput();
    }

    public void reset() {
        if (recognitions != null) {
            recognitions.clear();
            mIsVaild = true;
        }
    }
    public synchronized void setResult(YoloRawOutput outputs) {
        if (mYoloRawOutput.mGrid0Out == null) {
            mYoloRawOutput.mGrid0Out = outputs.mGrid0Out.clone();
            mYoloRawOutput.mGrid1Out = outputs.mGrid1Out.clone();
            mYoloRawOutput.mGrid2Out = outputs.mGrid2Out.clone();
        } else {
            arraycopy(outputs.mGrid0Out, 0, mYoloRawOutput.mGrid0Out, 0,
                    outputs.mGrid0Out.length);
            arraycopy(outputs.mGrid1Out, 0, mYoloRawOutput.mGrid1Out, 0,
                    outputs.mGrid1Out.length);
            arraycopy(outputs.mGrid2Out, 0, mYoloRawOutput.mGrid2Out, 0,
                    outputs.mGrid2Out.length);
        }
        mIsVaild = false;
    }

    public synchronized ArrayList<DetectedObject> getResult(InferenceWrapper mInferenceWrapper) {
        if (!mIsVaild) {
            mIsVaild = true;

            recognitions = mInferenceWrapper.postProcess(mYoloRawOutput);
        }
        return recognitions;
    }
}
