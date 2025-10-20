package com.smartprints.rknn_vision_lab.streaming;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.SurfaceHolder;
import com.elvishew.xlog.XLog;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.util.concurrent.atomic.AtomicBoolean;

public class RtspStreamingService implements VideoSourceListener {
    private static final String TAG = "RtspStreamingService";
    
    private StreamManager streamManager;
    private Thread streamingThread;
    private final AtomicBoolean isStreaming = new AtomicBoolean(false);
    private SurfaceHolder surfaceHolder;
    private Java2DFrameConverter converter;
    private Bitmap reusableBitmap;
    
    public RtspStreamingService(SurfaceHolder surfaceHolder) {
        this.surfaceHolder = surfaceHolder;
        this.converter = new Java2DFrameConverter();
    }
    
    public void startStreaming(String rtspUrl) {
        if (isStreaming.get()) {
            XLog.w(TAG, "Streaming is already active");
            return;
        }
        
        if (rtspUrl == null || rtspUrl.trim().isEmpty()) {
            XLog.e(TAG, "RTSP URL is null or empty");
            return;
        }
        
        if (!rtspUrl.toLowerCase().startsWith("rtsp://")) {
            XLog.e(TAG, "Invalid RTSP URL format: " + rtspUrl);
            return;
        }
        
        try {
            XLog.i(TAG, "Starting RTSP streaming from: " + rtspUrl);
            
            // Parse authentication from URL if present
            String[] authInfo = parseAuthFromUrl(rtspUrl);
            String username = authInfo[0];
            String password = authInfo[1];
            String cleanUrl = authInfo[2];
            
            // Reset any existing instance to ensure clean state
            FrameGrabberManager.resetInstance();
            
            // Create StreamManager with the URL
            streamManager = new StreamManager(this, cleanUrl);
            
            isStreaming.set(true);
            streamingThread = new Thread(() -> {
                try {
                    streamManager.start();
                } catch (Exception e) {
                    XLog.e(TAG, "Error during streaming: " + e.getMessage());
                } finally {
                    stopStreaming();
                }
            });
            streamingThread.start();
            
            XLog.i(TAG, "RTSP streaming started successfully");
            
        } catch (Exception e) {
            XLog.e(TAG, "Failed to start RTSP streaming: " + e.getMessage());
            isStreaming.set(false);
        }
    }
    
    @Override
    public void onFrameReady(Bitmap bitmap) {
        drawBitmapToSurface(bitmap);
    }
    
    @Override
    public void onStreamError(String error) {
        XLog.e(TAG, "Stream error: " + error);
        stopStreaming();
    }

    private void drawBitmapToSurface(Bitmap bitmap) {
        try {
            if (bitmap == null) return;
            
            // Reuse bitmap if possible to avoid memory allocation
            if (reusableBitmap == null || 
                reusableBitmap.getWidth() != bitmap.getWidth() || 
                reusableBitmap.getHeight() != bitmap.getHeight()) {
                if (reusableBitmap != null) {
                    reusableBitmap.recycle();
                }
                reusableBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);

            }
            
            // Copy to reusable bitmap
            new Canvas(reusableBitmap).drawBitmap(bitmap, 0, 0, null);
            
            // Draw to surface
            Canvas canvas = surfaceHolder.lockCanvas();
            if (canvas != null) {
                canvas.drawColor(android.graphics.Color.BLACK);

                int canvasWidth = canvas.getWidth();
                int canvasHeight = canvas.getHeight();
                int bitmapWidth = reusableBitmap.getWidth();
                int bitmapHeight = reusableBitmap.getHeight();

                // Compute aspect-ratio-preserving destination rectangle
                float bitmapRatio = (float) bitmapWidth / bitmapHeight;
                float canvasRatio = (float) canvasWidth / canvasHeight;

                int destWidth, destHeight;
                if (bitmapRatio > canvasRatio) {
                    // Fit by width
                    destWidth = canvasWidth;
                    destHeight = (int) (canvasWidth / bitmapRatio);
                } else {
                    // Fit by height
                    destHeight = canvasHeight;
                    destWidth = (int) (canvasHeight * bitmapRatio);
                }

                int left = (canvasWidth - destWidth) / 2;
                int top = (canvasHeight - destHeight) / 2;

                android.graphics.Rect dest = new android.graphics.Rect(
                        left,
                        top,
                        left + destWidth,
                        top + destHeight
                );
                canvas.drawBitmap(reusableBitmap, null, dest, null);
                surfaceHolder.unlockCanvasAndPost(canvas);
            }
            
        } catch (Exception e) {
            XLog.e(TAG, "Error drawing bitmap to surface: " + e.getMessage());
        }
    }
    
    public void stopStreaming() {
        if (!isStreaming.get()) {
            return;
        }
        
        isStreaming.set(false);
        
        // Wait for streaming thread to finish
        if (streamingThread != null) {
            try {
                streamingThread.join(2000); // Wait up to 2 seconds
            } catch (InterruptedException e) {
                XLog.w(TAG, "Interrupted while waiting for streaming thread to finish");
            }
            streamingThread = null;
        }
        
        // Cleanup resources
        if (streamManager != null) {
            streamManager.stop();
            streamManager = null;
        }
        
        // Reset the singleton instance
        FrameGrabberManager.resetInstance();
        
        if (reusableBitmap != null) {
            reusableBitmap.recycle();
            reusableBitmap = null;
        }
        
        XLog.i(TAG, "RTSP streaming stopped");
    }
    
    public boolean isStreaming() {
        return isStreaming.get();
    }
    
    public void testConnection(String rtspUrl) {
        testConnection(rtspUrl, null, null);
    }
    
    public void testConnection(String rtspUrl, String username, String password) {
        if (rtspUrl == null || rtspUrl.trim().isEmpty()) {
            XLog.e(TAG, "RTSP URL is null or empty");
            return;
        }
        
        if (!rtspUrl.toLowerCase().startsWith("rtsp://")) {
            XLog.e(TAG, "Invalid RTSP URL format: " + rtspUrl);
            return;
        }
        
        XLog.i(TAG, "Testing RTSP connection to: " + rtspUrl);
        
        // Parse authentication from URL if not provided
        if (username == null && password == null) {
            String[] authInfo = parseAuthFromUrl(rtspUrl);
            username = authInfo[0];
            password = authInfo[1];
            rtspUrl = authInfo[2];
        }
        
        // Create a temporary grabber just for testing
        try {
            FFmpegFrameGrabber testGrabber = FrameGrabberManager.createGrabberWithAuth(rtspUrl, username, password);
            if (testGrabber == null) {
                XLog.e(TAG, "Failed to create test grabber");
                return;
            }
            
            testGrabber.start();
            XLog.i(TAG, "RTSP connection test successful");
            testGrabber.stop();
            testGrabber.close();
            
        } catch (Exception e) {
            XLog.e(TAG, "RTSP connection test failed: " + e.getMessage());
            // Log additional error details
            if (e.getMessage().contains("-858797304")) {
                XLog.e(TAG, "Error -858797304: This usually indicates authentication failure or server access issues");
                XLog.i(TAG, "Try adding username/password to the RTSP URL or check server permissions");
            }
        }
    }
    
    private String[] parseAuthFromUrl(String rtspUrl) {
        String username = null;
        String password = null;
        String cleanUrl = rtspUrl;
        
        try {
            // Check if URL contains authentication info
            // Format: rtsp://username:password@host:port/path
            if (rtspUrl.contains("@")) {
                String[] parts = rtspUrl.split("@");
                if (parts.length == 2) {
                    String authPart = parts[0].substring(7); // Remove "rtsp://"
                    String[] auth = authPart.split(":");
                    if (auth.length == 2) {
                        username = auth[0];
                        password = auth[1];
                        cleanUrl = "rtsp://" + parts[1];
                    }
                }
            }
        } catch (Exception e) {
            XLog.w(TAG, "Failed to parse authentication from URL: " + e.getMessage());
        }
        
        return new String[]{username, password, cleanUrl};
    }
    
    public void testNetworkConnectivity(String rtspUrl) {
        XLog.i(TAG, "Testing network connectivity to RTSP server...");
        
        try {
            // Extract host and port from RTSP URL
            String host = extractHostFromUrl(rtspUrl);
            int port = extractPortFromUrl(rtspUrl);
            
            if (host != null && port > 0) {
                XLog.i(TAG, "Testing connection to " + host + ":" + port);
                
                // Note: In a real implementation, you might want to use a socket connection test
                // For now, we'll just log the extracted information
                XLog.i(TAG, "Host: " + host + ", Port: " + port);
                
                // You could add actual socket connectivity test here if needed
                // Socket socket = new Socket();
                // socket.connect(new InetSocketAddress(host, port), 5000);
                // socket.close();
                
            } else {
                XLog.w(TAG, "Could not extract host/port from RTSP URL");
            }
            
        } catch (Exception e) {
            XLog.e(TAG, "Network connectivity test failed: " + e.getMessage());
        }
    }
    
    private String extractHostFromUrl(String rtspUrl) {
        try {
            // Remove rtsp:// prefix
            String url = rtspUrl.substring(7);
            // Remove authentication if present
            if (url.contains("@")) {
                url = url.substring(url.indexOf("@") + 1);
            }
            // Extract host (everything before : or /)
            if (url.contains(":")) {
                return url.substring(0, url.indexOf(":"));
            } else if (url.contains("/")) {
                return url.substring(0, url.indexOf("/"));
            }
            return url;
        } catch (Exception e) {
            XLog.e(TAG, "Failed to extract host from URL: " + e.getMessage());
            return null;
        }
    }
    
    private int extractPortFromUrl(String rtspUrl) {
        try {
            // Remove rtsp:// prefix
            String url = rtspUrl.substring(7);
            // Remove authentication if present
            if (url.contains("@")) {
                url = url.substring(url.indexOf("@") + 1);
            }
            // Extract port (between : and /)
            if (url.contains(":")) {
                String portPart = url.substring(url.indexOf(":") + 1);
                if (portPart.contains("/")) {
                    portPart = portPart.substring(0, portPart.indexOf("/"));
                }
                return Integer.parseInt(portPart);
            }
            return 554; // Default RTSP port
        } catch (Exception e) {
            XLog.e(TAG, "Failed to extract port from URL: " + e.getMessage());
            return 554; // Default RTSP port
        }
    }
}
