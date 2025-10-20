package com.smartprints.rknn_vision_lab.streaming;

public class Constants {
    // Frame dimensions for RTSP streaming
    public static final int FRAME_WIDTH = 640;
    public static final int FRAME_HEIGHT = 480;
    
    // Frames per second for RTSP streaming
    public static final int FPS = 30;
    
    // RTSP connection timeout (in milliseconds)
    public static final int RTSP_TIMEOUT = 10000;
    
    // Maximum retry attempts for RTSP connection
    public static final int MAX_RETRY_ATTEMPTS = 3;
}
