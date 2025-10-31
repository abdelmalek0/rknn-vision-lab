package com.smartprints.rknn_vision_lab.inference;

import static android.provider.Settings.System.FONT_SCALE;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import com.elvishew.xlog.XLog;
import com.smartprintsksa.rknn_sdk.YoloDetector;
import com.smartprintsksa.rknn_sdk.structs.DetectedObject;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// Minimal adapter for NPU/RKNN inference; replace internals with your actual model code
public class RknnInferenceEngine implements InferenceEngine {

    private Scalar textColor = new Scalar(0, 0, 255);
    private static final float FONT_SCALE = 1.0f;
    public static final int THICKNESS = 2;
    public static final int FONT_THICKNESS = 6;
    public RknnInferenceEngine(Context context) {
        // TODO: initialize RKNN model/resources here
        YoloDetector.setup(context);
    }

    @Override
    public Bitmap process(Bitmap bitmap) {
//        List<DetectedObject> detections = YoloDetector.detect(bitmap, 0, 0, 1, 1);
//        XLog.d("startInference", "Total time of detection: " + YoloDetector.getPipelineExecutionTime() + " seconds");
//
//        // Scale detections back to original frame size
////        float widthScaleFactor = (float) frameRGB.width() / DETECTOR_INPUT_SIZE;
////        float heightScaleFactor = (float) frameRGB.height() / DETECTOR_INPUT_SIZE;
//
//        // Filter based on the cropped area
//        List<Rect> validBoundingBoxes = detections.stream()
//                .map(detection -> new Rect(
//                        detection.getBoundingBox().left,
//                        detection.getBoundingBox().top,
//                        (detection.getBoundingBox().right - detection.getBoundingBox().left),
//                        (detection.getBoundingBox().bottom - detection.getBoundingBox().top)
//                ))
//                .collect(Collectors.toList());
//        drawBoundingBoxes(bitmap, validBoundingBoxes);
        // TODO: run inference and draw overlays onto a mutable bitmap
        return bitmap; // Pass-through placeholder
    }


    public void drawBoundingBoxes(Bitmap bitmap, List<Rect> validBoundingBoxes) {
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(THICKNESS);

        for (Rect rect : validBoundingBoxes) {
            // Convert OpenCV Rect (x, y, width, height) to Android Rect (left, top, right, bottom)
            RectF androidRect = new RectF(
                    (int) rect.x,
                    (int) rect.y,
                    (int) (rect.x + rect.width),
                    (int) (rect.y + rect.height)
            );
            canvas.drawRect(androidRect, paint);
        }
    }

    @Override
    public void close() {
        // TODO: release RKNN resources here
    }
}
