package com.smartprintsksa.rknn_sdk.structs;

/**
 * Detected objects, returned from native yolo_post_process
 */
public class YoloDetections {
    /**
     * detected objects count.
     */
    public int count = 0;

    /**
     * id for each detected object.
     */
    public int[] ids;

    /**
     * score for each detected object.
     */
    public float[] scores;

    /**
     * box for each detected object.
     */
    public float[] boxes;
}
