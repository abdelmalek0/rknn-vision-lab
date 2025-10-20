package com.smartprints.rknn_vision_lab.streaming;

import static com.smartprints.rknn_vision_lab.streaming.Constants.FPS;
import static com.smartprints.rknn_vision_lab.streaming.Constants.FRAME_HEIGHT;
import static com.smartprints.rknn_vision_lab.streaming.Constants.FRAME_WIDTH;
import com.elvishew.xlog.XLog;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameFilter;
import org.bytedeco.javacv.FFmpegFrameGrabber;

public class FrameGrabberManager {
    private static final String TAG = "FrameGrabberManager";
    private static boolean loggingInitialized = false;

    private static FrameGrabberManager instance;
    private FFmpegFrameGrabber grabber;
    private FFmpegFrameFilter filter;

    private FrameGrabberManager(String rtspURL) {
        initializeLogging();
        initialize(rtspURL);
    }
    
    private static void initializeLogging() {
        if (!loggingInitialized) {
            try {
//                FFmpegLogCallback.set(new FFmpegLogCallback() {
//                    @Override
//                    public void call(int level, String msg) {
//                        if (level <= avutil.AV_LOG_WARNING) {
//                            XLog.warning(TAG, "FFmpeg: " + msg);
//                        } else {
//                            XLog.debug(TAG, "FFmpeg: " + msg);
//                        }
//                    }
//                });
                loggingInitialized = true;
                XLog.i(TAG, "FFmpeg logging initialized");
            } catch (Exception e) {
                XLog.e(TAG, "Failed to initialize FFmpeg logging: " + e.getMessage());
            }
        }
    }

    public static synchronized FrameGrabberManager getInstance(String rtspURL) {
        if (instance == null) {
            instance = new FrameGrabberManager(rtspURL);
        }
        return instance;
    }
    
    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.cleanup();
            instance = null;
        }
    }

    public static synchronized FrameGrabberManager getInstance() {
        if (instance == null) {
            return null;
        }
        return instance;
    }

    private void initialize(String rtspURL) {
        try {
            grabber = createAndConfigureFrameGrabber(rtspURL);
            // Start the grabber and frame filter here so callers can immediately use them
            grabber.start();
            filter = initializeFrameFilter(grabber);
            filter.start();
            XLog.d(TAG,"FrameGrabberManager: FrameGrabber and FrameFilter have been initialized.");
        } catch (Exception e) {
            XLog.d(TAG,"FrameGrabberManager: Failed to initialize: " + e);
            // Ensure partial resources are cleaned up on failure
            try { if (filter != null) { filter.close(); filter = null; } } catch (Exception ignored) {}
            try { if (grabber != null) { grabber.stop(); grabber.close(); grabber = null; } } catch (Exception ignored) {}
        }
    }

    private FFmpegFrameGrabber createAndConfigureFrameGrabber(String rtspURL) throws Exception {
        FFmpegFrameGrabber grabber = FFmpegFrameGrabber.createDefault(rtspURL);
        grabber.setImageHeight(FRAME_HEIGHT);
        grabber.setImageWidth(FRAME_WIDTH);
        grabber.setOption("error_resilient", "0");
        grabber.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        
        // Try UDP first, then fallback to TCP if needed
        // Use UDP as a default transport for lower latency; typo fixed (was "tdp")
        grabber.setOption("rtsp_transport", "udp"); // Start with UDP
        grabber.setAudioChannels(0); // disable audio
        
        // Additional RTSP options for better compatibility
        grabber.setOption("stimeout", "10000000"); // 10 second timeout
        grabber.setOption("max_delay", "500000"); // Max delay in microseconds
        
        return grabber;
    }

    private FFmpegFrameFilter initializeFrameFilter(FFmpegFrameGrabber grabber) throws Exception {
        return new FFmpegFrameFilter("fps=" + FPS, grabber.getImageWidth(), grabber.getImageHeight());
    }

    public void cleanup() {
        try {
            if (filter != null) {
                filter.flush();
                filter.close();
                XLog.d(TAG, "FrameGrabberManager: FrameFilter has been closed.");
            }
        } catch (Exception e) {
            XLog.d(TAG, "FrameGrabberManager: Failed to close FrameFilter: " + e);
        }

        try {
            if (grabber != null) {
                grabber.stop();
                grabber.close();
                XLog.d(TAG, "FrameGrabberManager: FrameGrabber has been stopped and closed.");
            }
        } catch (Exception e) {
            XLog.d(TAG, "FrameGrabberManager: Failed to close FrameGrabber: " + e);
        }
    }

    public FFmpegFrameGrabber getGrabber() {
        return grabber;
    }

    public FFmpegFrameFilter getFilter() {
        return filter;
    }
    
    public void restart() {
        try {
            if (grabber != null) {
                grabber.restart();
                XLog.d(TAG, "FrameGrabber restarted successfully");
            }
            if (filter != null) {
                filter.restart();
                XLog.d(TAG, "FrameFilter restarted successfully");
            }
        } catch (Exception e) {
            XLog.e(TAG, "Failed to restart grabber/filter: " + e.getMessage());
        }
    }
    
    public static FFmpegFrameGrabber createGrabberWithAuth(String rtspURL, String username, String password) {
        try {
            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(rtspURL);
            
            // Set format explicitly
            grabber.setFormat("rtsp");
            
            // Configure RTSP transport options
            // Use UDP by default for lower latency; allow explicit override in URL if needed
            grabber.setOption("rtsp_transport", "udp");
            grabber.setOption("stimeout", "10000000");
            grabber.setOption("max_delay", "500000");
            grabber.setOption("user_agent", "RKNN_Vision_Lab/1.0");
            // Do not set contradictory rtsp_flags here; let the transport option drive behavior
            
            // Authentication
            if (username != null && !username.isEmpty()) {
                grabber.setOption("username", username);
            }
            if (password != null && !password.isEmpty()) {
                grabber.setOption("password", password);
            }
            
            // Additional RTSP options for better compatibility
            grabber.setOption("allowed_media_types", "video");
            grabber.setOption("fflags", "+genpts+igndts");
            grabber.setOption("flags", "+low_delay");
            grabber.setOption("strict", "experimental");
            
            // Avoid forcing conflicting lower transport or listen flags which can break many RTSP servers
            
            // Video configuration
            grabber.setImageHeight(FRAME_HEIGHT);
            grabber.setImageWidth(FRAME_WIDTH);
            grabber.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            
            // Audio configuration
            grabber.setAudioChannels(0);
            grabber.setAudioCodec(avcodec.AV_CODEC_ID_NONE);
            
            // Buffer and error handling
            grabber.setOption("buffer_size", "1024000");
            grabber.setOption("max_ts_probe", "1000000");
            grabber.setOption("analyzeduration", "1000000");
            grabber.setOption("probesize", "1000000");
            
            return grabber;
        } catch (Exception e) {
            XLog.e(TAG, "Failed to create grabber with auth: " + e.getMessage());
            return null;
        }
    }
}
