package com.smartprints.rknn_vision_lab.core;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.view.SurfaceHolder;

public class SurfaceRenderer {
    private final SurfaceHolder holder;

    public SurfaceRenderer(SurfaceHolder holder) {
        this.holder = holder;
    }

    public void drawFrame(Bitmap bitmap) {
        if (bitmap == null) return;
        Canvas canvas = holder.lockCanvas();
        if (canvas == null) return;
        try {
            canvas.drawColor(Color.BLACK);
            Matrix m = fitCenter(bitmap.getWidth(), bitmap.getHeight(),
                    canvas.getWidth(), canvas.getHeight());
            canvas.drawBitmap(bitmap, m, null);
        } finally {
            holder.unlockCanvasAndPost(canvas);
        }
    }

    public void clear() {
        Canvas canvas = holder.lockCanvas();
        if (canvas == null) return;
        try {
            canvas.drawColor(Color.BLACK);
        } finally {
            holder.unlockCanvasAndPost(canvas);
        }
    }

    private static Matrix fitCenter(int sw, int sh, int dw, int dh) {
        float s = Math.min((float) dw / sw, (float) dh / sh);
        float tx = (dw - sw * s) / 2f;
        float ty = (dh - sh * s) / 2f;
        Matrix m = new Matrix();
        m.postScale(s, s);
        m.postTranslate(tx, ty);
        return m;
    }
}