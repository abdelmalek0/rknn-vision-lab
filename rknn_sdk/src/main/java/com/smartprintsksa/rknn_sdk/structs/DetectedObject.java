package com.smartprintsksa.rknn_sdk.structs;

import android.graphics.Bitmap;
import android.graphics.Rect;

public class DetectedObject {
    private Bitmap bitmap = null;
    private Rect boundingBox;
    private float boundingBoxConfidence;
    private int classID;
    private float[] classConfidence = null;

    public DetectedObject(Rect boundingBox, float boundingBoxConfidence, int classID) {
        this.boundingBox = boundingBox;
        this.boundingBoxConfidence = boundingBoxConfidence;
        this.classID = classID;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }
    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public Rect getBoundingBox() {
        return boundingBox;
    }
    public void setBoundingBox(Rect boundingBox) {this.boundingBox = boundingBox;}

    public float getBoundingBoxConfidence() {
        return boundingBoxConfidence;
    }
    public void setBoundingBoxConfidence(float boundingBoxConfidence) {
        this.boundingBoxConfidence = boundingBoxConfidence;
    }

    public int getClassIndex() {
        return classID;
    }
    public void setClassIndex(int id) {
        this.classID = id;
    }

    public float[] getClassConfidence() {
        return classConfidence;
    }

    public void setClassConfidence(float[] classConfidence) {
        this.classConfidence = classConfidence;
    }

}
