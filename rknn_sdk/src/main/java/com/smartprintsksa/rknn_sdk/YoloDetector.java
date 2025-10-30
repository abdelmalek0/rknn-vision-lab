package com.smartprintsksa.rknn_sdk;

import android.content.Context;
import android.graphics.Bitmap;

import com.smartprintsksa.rknn_sdk.structs.DetectedObject;

import java.util.ArrayList;

/**
 * A class that performs object detection to find batteries and non-batteries
 * and calculates the time needed to finish that operation.
 */

public class YoloDetector {

    /**
     * validates the verification code and initializes the deep learning models. Once the verification code is verified,
     * the function will unlock access to the deep learning models, allowing you to use them in your projects.
     *
     * @param context The Context object used to access system resources and services
     * @return A boolean value indicating the success of the license validation
     */
    public static boolean setup(Context context){
        return YoloDetectorHelper.setup(context);
    }

    /**
     * Performs object detection on a Bitmap image and returns a list of predictions.
     *
     * @param mBitmap The Bitmap image to perform object detection on
     * @return A list of prediction objects, each representing a detected object in the image
     */


    public static ArrayList<DetectedObject> detect(Bitmap mBitmap, float mStartX, float mStartY, float mIvScaleX, float mIvScaleY){
        return YoloDetectorHelper.detect(mBitmap, mStartX, mStartY, mIvScaleX, mIvScaleY);
    }

    /**
     * Returns the total time taken to do the whole process of prediction, from reading the bitmap
     * until te predictions are returned.
     *
     * @return The total time taken for the prediction, in milliseconds
     */
    public static float getPipelineExecutionTime(){
        return YoloDetectorHelper.getPipelineExecutionTime();
    }

}
