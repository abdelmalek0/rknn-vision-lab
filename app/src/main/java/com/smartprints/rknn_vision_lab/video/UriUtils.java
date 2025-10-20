package com.smartprints.rknn_vision_lab.video;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class UriUtils {
    private static final String TAG = "UriUtils";

    public static String resolveToPathOrCopyToCache(Context context, Uri uri) {
        if (uri == null) return null;
        
        String scheme = uri.getScheme();
        
        // Handle file:// URIs directly without copying
        if ("file".equalsIgnoreCase(scheme)) {
            String path = uri.getPath();
            if (path != null) {
                File file = new File(path);
                if (file.exists() && file.canRead()) {
                    Log.d(TAG, "Using file URI directly: " + path);
                    return path;
                }
            }
        }
        
        // For content:// URIs, try to get the actual file path first
        if ("content".equalsIgnoreCase(scheme)) {
            String path = getPathFromContentUri(context, uri);
            if (path != null) {
                File file = new File(path);
                if (file.exists() && file.canRead()) {
                    Log.d(TAG, "Resolved content URI to path: " + path);
                    return path;
                }
            }
        }
        
        // Fallback: copy to cache for content:// URIs that can't be resolved
        Log.d(TAG, "Copying URI to cache: " + uri);
        String name = queryDisplayName(context, uri);
        if (name == null) name = "video_" + System.currentTimeMillis() + ".mp4";

        File cacheFile = new File(context.getCacheDir(), name);
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(cacheFile)) {
            if (in == null) {
                Log.e(TAG, "Failed to open input stream for URI");
                return null;
            }
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.flush();
            Log.d(TAG, "Copied to cache: " + cacheFile.getAbsolutePath());
            return cacheFile.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy URI to cache", e);
            return null;
        }
    }
    
    private static String getPathFromContentUri(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            String[] projection = {"_data"};
            cursor = context.getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow("_data");
                return cursor.getString(columnIndex);
            }
        } catch (Exception e) {
            Log.d(TAG, "Could not get path from content URI", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    private static String queryDisplayName(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return cursor.getString(idx);
            }
        } catch (Exception ignore) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }
}
