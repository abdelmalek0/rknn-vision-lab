package com.smartprintsksa.rknn_sdk;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class BaseModuleActivity extends AppCompatActivity {
    private static final String TAG = "BASE MODULE ACTIVITY";
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private Handler mUIHandler;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mUIHandler = new Handler(getMainLooper());
    }

    @Override
    public void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        startBackgroundThread();
    }

    public void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("ModuleActivity");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    @Override
    public void onDestroy() {
        stopBackgroundThread();
        super.onDestroy();
    }

    public void stopBackgroundThread() {
      mBackgroundThread.quitSafely();
      try {
          mBackgroundThread.join();
          mBackgroundThread = null;
          mBackgroundHandler = null;
      } catch (InterruptedException e) {
          Logger.error(TAG, "Error on stopping background thread. " + e.getMessage());
      }
    }
}
