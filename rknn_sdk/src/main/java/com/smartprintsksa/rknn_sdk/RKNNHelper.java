package com.smartprintsksa.rknn_sdk;

import java.lang.reflect.Method;

public class RKNNHelper {
    private static final String TAG = "RKNNHelper";
    public static String getPlatform()
    {
        String platform = null;
        try {
            Class<?> classType = Class.forName("android.os.SystemProperties");
            Method getMethod = classType.getDeclaredMethod("get", new Class<?>[]{String.class});
            platform = (String) getMethod.invoke(classType, new Object[]{"ro.board.platform"});
            Logger.debug(TAG, "Platform: " + platform);
        } catch (Exception e) {
            Logger.error(TAG, "Error getting platform. " + e.getMessage());
        }
        return platform;
    }
}
