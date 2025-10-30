package com.smartprintsksa.rknn_sdk;

import static com.smartprintsksa.rknn_sdk.Encryption.decrypt;
import static com.smartprintsksa.rknn_sdk.Encryption.em;
import static com.smartprintsksa.rknn_sdk.Encryption.pw;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import com.smartprintsksa.rknn_sdk.structs.DetectedObject;
import com.smartprintsksa.rknn_sdk.structs.YoloRawOutput;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Objects;

public class YoloDetectorHelper {
    private static final String TAG = "YOLO DETECTOR HELPER";

    private static String fileDirPath;
    private static String mYoloModelName = "model.rknn";
    private static final boolean ENCRYPTED = false;
    private static String tmFormat = "yyyyMMddHHmmss";
    protected static float confidenceThreshold = 0.1f;
    protected static float classThreshold = 0.5f;
    protected static float nmsThreshold = 0.5f;
    private static long durationInference = 0;

    private static long preprocessingTime = 0;
    private static long detectionTime = 0;
    private static long proprocessingTime = 0;

    private static InferenceWrapper mInferenceWrapper;
    private static InferenceResult mInferenceResult;  // detection result

    public static boolean setup(Context context) {
        String platform = RKNNHelper.getPlatform();
        Logger.debug(TAG, "SOC platform: " + platform);
        if (!"rk3588".equals(platform)) {
            return false;
        }

        fileDirPath = context.getCacheDir().getAbsolutePath();

        mInferenceWrapper = new InferenceWrapper();
        initializeObjectDetectionModel(context, mYoloModelName, ENCRYPTED);

        mInferenceResult = new InferenceResult();
        try {
            mInferenceResult.init();
        } catch (IOException e) {
            Logger.error(TAG, "Failure: " + Objects.requireNonNull(e.getMessage()));
            return false;
        }

        return true;
    }

    public static ArrayList<DetectedObject> detect(Bitmap bitmap, float startX, float startY, float ivScaleX, float ivScaleY) {
        long startTime = System.currentTimeMillis();
        float imgScaleX = (float) bitmap.getWidth() / Processor.YOLO_INPUT;
        float imgScaleY = (float) bitmap.getHeight() / Processor.YOLO_INPUT;

        // Resize and prepare the input
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, Processor.YOLO_INPUT, Processor.YOLO_INPUT, true);
        byte[] input = Processor.convertBitmapToByteArray(resizedBitmap);

        // Run inference
        YoloRawOutput outputs = mInferenceWrapper.run(input);
        mInferenceResult.setResult(outputs);

        detectionTime = System.currentTimeMillis() - startTime;
        Logger.debug(TAG, "Detection time: " + detectionTime / 1_000.0f + " seconds");
        startTime = System.currentTimeMillis();

        // Process results
        ArrayList<DetectedObject> recognitions = mInferenceResult.getResult(mInferenceWrapper);
        for (DetectedObject recognition : recognitions) {
            Rect detection = recognition.getBoundingBox();

            // Scale detection box back to original bitmap size
            Processor.scaleBoundingBox(detection, imgScaleX, imgScaleY);

            // Extract the bounding box bitmap
            Bitmap bboxBitmap = Processor.boundingBoxExtractor(bitmap, detection, Processor.CLASSIFIER_INPUT);
            recognition.setBitmap(bboxBitmap);

            // Scale detection box to UI coordinates
            Processor.scaleBoundingBoxToUI(detection, startX, startY, ivScaleX, ivScaleY);
            recognition.setBoundingBox(detection);
        }

        proprocessingTime = System.currentTimeMillis() - startTime;
        Logger.debug(TAG, "Post-processing time: " + proprocessingTime / 1_000.0f + " seconds");

        return recognitions;
    }

    private static void initializeObjectDetectionModel(Context context, String mYoloModelName, boolean encrypted){
        createTempFile(context, mYoloModelName, encrypted);
        try {
            mInferenceWrapper.initYolo(Processor.YOLO_INPUT, Processor.YOLO_INPUT,
                    Processor.CHANNELS, fileDirPath + "/" + mYoloModelName);
        } catch (Exception e) {
            Log.e("Model Init", Objects.requireNonNull(e.getMessage()));
        }
    }

    private static void createTempFile(Context context, String fileName, boolean encrypted) {
        String filePath = fileDirPath + "/" + fileName;
        try {
            File dir = new File(fileDirPath);

            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = new File(filePath);

            if (!file.exists() || isFirstRun(context)) {

                InputStream ins = context.getAssets().open(fileName);
                FileOutputStream fos = new FileOutputStream(file);

                byte[] targetArray = new byte[ins.available()];
                ins.read(targetArray);
                InputStream input = encrypted ? new ByteArrayInputStream(decrypt(targetArray, pw, em)) :
                        new ByteArrayInputStream(targetArray);
                ins.close();

                byte[] buffer = new byte[8192];
                int count = 0;

                while ((count = input.read(buffer)) > 0) {
                    fos.write(buffer, 0, count);
                }

                fos.close();
                input.close();

                Log.d("createFile", "Create " + filePath);
            }
        } catch (Exception e) {
            Log.e("Model file manipulation", Objects.requireNonNull(e.getMessage()));
        }
    }

    private static boolean isFirstRun(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("setting",
                Context.MODE_PRIVATE);
        boolean isFirstRun = sharedPreferences.getBoolean("isFirstRun", true);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (isFirstRun) {
            editor.putBoolean("isFirstRun", false);
            editor.apply();
        }

        return isFirstRun;
    }

    public static float getPipelineExecutionTime(){
        return (preprocessingTime + detectionTime + proprocessingTime) / 1_000.0f;
    }

}
