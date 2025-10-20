package com.smartprints.rknn_vision_lab.streaming;

import static com.smartprints.rknn_vision_lab.streaming.Constants.FRAME_HEIGHT;
import static com.smartprints.rknn_vision_lab.streaming.Constants.FRAME_WIDTH;

import android.graphics.Bitmap;

import com.elvishew.xlog.XLog;

import org.bytedeco.javacv.*;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class StreamManager implements VideoSourceManager {
    private static final String TAG = "StreamManager";
    private final VideoSourceListener videoSourceListener;
    private final FFmpegFrameGrabber grabber;
    private final FFmpegFrameFilter filter;
    private final OpenCVFrameConverter<Mat> matConverter = new OpenCVFrameConverter.ToOrgOpenCvCoreMat();
    private final AndroidFrameConverter bitmapConverter = new AndroidFrameConverter();
    private boolean running = false;
    private boolean enableFaultTolerance = false;
    private static final int FRAME_GRABBING_RETRIES = 5;
    private static final int TIMEOUT_BEFORE_FRAME_GRABBING = 2; // seconds

    public StreamManager(VideoSourceListener videoSourceListener, String rtspUrl) {
        this.videoSourceListener = videoSourceListener;
        FrameGrabberManager manager = FrameGrabberManager.getInstance(rtspUrl);
        this.grabber = manager.getGrabber();
        this.filter = manager.getFilter();


    }
    
    @Override
    public void start() {
        XLog.d(TAG, "StreamingLoop: ------------------------- Starting -------------------------");
        running = true;
        int frameGrabTries = 0;
        int reconnectionTries = 0;

        try {
            XLog.i(TAG, "Attempting to restart grabber and filter...");
            grabber.restart();
            XLog.i(TAG, "Grabber restarted successfully");
            
            filter.restart();
            XLog.i(TAG, "Filter restarted successfully");

            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    Frame frame = grabber.grabImage();

                    if (frame == null || frame.image == null) {
                        XLog.w(TAG, "Invalid frame encountered.");
//                        if (!enableFaultTolerance) break;

                        if (frameGrabTries++ > FRAME_GRABBING_RETRIES) {
                            XLog.e(TAG, "Exceeded max retries. Stopping stream.");
                            break;
                        }

                        Thread.sleep(TIMEOUT_BEFORE_FRAME_GRABBING * 1000L);
                        continue;
                    }

                    // Reset retry count on valid frame
                    frameGrabTries = 0;

//                    filter.push(frame);
//                    Frame filtered = filter.pull();
//                    if (filtered == null) continue;
                    Mat originalMat = matConverter.convertToOrgOpenCvCoreMat(frame);
                    Mat resized = new Mat();
                    Imgproc.resize(originalMat, resized, new Size(FRAME_WIDTH, FRAME_HEIGHT));
                    Bitmap bitmap = bitmapConverter.convert(matConverter.convert(resized));
                    videoSourceListener.onFrameReady(bitmap);

                    originalMat.release();
                    resized.release();
                } catch (Exception e) {
                    XLog.e(TAG, "Frame processing error: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            XLog.e(TAG, "Critical streaming error: " + e.getMessage());
            
            // Provide specific error messages based on error codes
            String errorMessage = "Streaming failed: " + e.getMessage();
            if (e.getMessage().contains("-99")) {
                errorMessage = "Network connection failed. Please check:\n" +
                              "1. Internet connectivity\n" +
                              "2. RTSP server availability\n" +
                              "3. Firewall settings\n" +
                              "4. RTSP URL correctness";
            } else if (e.getMessage().contains("-858797304")) {
                errorMessage = "Authentication or access denied. Please check:\n" +
                              "1. RTSP credentials (username:password@)\n" +
                              "2. Server permissions\n" +
                              "3. Stream availability";
            }
            
            videoSourceListener.onStreamError(errorMessage);
        } finally {
            stop();
            XLog.d(TAG, "StreamingLoop: ------------------------- Ended -------------------------");
        }
    }

    @Override
    public void stop() {
        running = false;
        cleanup();
    }

    private void cleanup() {
        FrameGrabberManager manager = FrameGrabberManager.getInstance();
        if (manager != null) manager.cleanup();
    }
}
