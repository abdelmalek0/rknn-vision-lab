package com.smartprintsksa.rknn_sdk;

import android.app.Application;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Logger {
    private static final String TAG = "LOGGER";
    private static final String DEFAULT_LOG_FILE_NAME = "detector_logs.txt";
    private static File logFile;

    public static void initialize(Application application) {
        logFile = new File(application.getExternalFilesDir(null), DEFAULT_LOG_FILE_NAME);
    }

    private static boolean isInitialized() {
        return logFile != null;
    }

    public static void info(String tag, String message) {
        Log.i(tag, message);
        writeToFile(tag, "INFO: " + message);
    }

    public static void error(String tag, String message) {
        Log.e(tag, message);
        writeToFile(tag, "ERROR: " + message);
    }

    public static void warn(String tag, String message) {
        Log.w(tag, message);
        writeToFile(tag, "WARNING: " + message);
    }

    public static void debug(String tag, String message) {
        Log.d(tag, message);
        writeToFile(tag, "DEBUG: " + message);
    }

    private static void writeToFile(String tag, String message) {
        if (!isInitialized()) {
            Log.e(TAG, "FileLogger not initialized. Call initialize() first.");
            return;
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String logMessage = timestamp + " [" + tag + "] " + message + "\n";

        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.append(logMessage);
        } catch (IOException e) {
            Log.e(TAG, "Error writing log to file", e);
        }
    }
}