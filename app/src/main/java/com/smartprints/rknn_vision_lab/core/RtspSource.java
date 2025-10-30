package com.smartprints.rknn_vision_lab.core;

import static com.smartprints.rknn_vision_lab.core.Constants.FPS;
import static com.smartprints.rknn_vision_lab.core.Constants.FRAME_HEIGHT;
import static com.smartprints.rknn_vision_lab.core.Constants.FRAME_WIDTH;

import android.graphics.Bitmap;

import com.elvishew.xlog.XLog;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.AndroidFrameConverter;
import org.bytedeco.javacv.FFmpegFrameFilter;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.concurrent.atomic.AtomicBoolean;

public class RtspSource extends VideoSource{
    private static final String TAG = "RtspSource";
    private final VideoSourceListener videoSourceListener;
    private FFmpegFrameGrabber grabber = null;
    private FFmpegFrameFilter filter = null;
    private final OpenCVFrameConverter<Mat> matConverter = new OpenCVFrameConverter.ToOrgOpenCvCoreMat();
    private final AndroidFrameConverter bitmapConverter = new AndroidFrameConverter();
    private static final int FRAME_GRABBING_RETRIES = 5;
    private static final int TIMEOUT_BEFORE_FRAME_GRABBING = 2; // seconds
    private AtomicBoolean isRunning = new AtomicBoolean(false);

    public RtspSource(VideoSourceListener videoSourceListener, String rtspUrl) {
        this.videoSourceListener = videoSourceListener;
        grabber = createFrameGrabber(rtspUrl);
        if (grabber != null) filter = initializeFrameFilter(grabber);
    }
    @Override
    public void start() {
        XLog.d(TAG, "StreamingLoop: ------------------------- Starting -------------------------");
        isRunning.set(true);
        startBackgroundThread();

        backgroundHandler.post(() -> {
            int frameGrabTries = 0;

            try {
                grabber.restart();
                filter.restart();

                while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
                    try {
                        Frame frame = grabber.grabImage();

                        if (frame == null || frame.image == null) {
                            XLog.w(TAG, "Invalid frame encountered.");
                            if (frameGrabTries++ > FRAME_GRABBING_RETRIES) {
                                XLog.e(TAG, "Exceeded max retries. Stopping stream.");
                                break;
                            }
                            Thread.sleep(TIMEOUT_BEFORE_FRAME_GRABBING * 1000L);
                            continue;
                        }

                        frameGrabTries = 0;

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
                videoSourceListener.onStreamError("Streaming failed: " + e.getMessage());
            } finally {
                stop();
                XLog.d(TAG, "StreamingLoop: ------------------------- Ended -------------------------");
            }
        });
    }


    @Override
    public void stop() {
        isRunning.set(false);
        cleanup();
        stopBackgroundThread();
    }

    @Override
    public boolean isRunning() {
        return isRunning.get();
    }

    private FFmpegFrameGrabber createFrameGrabber(String rtspURL){
        XLog.d(TAG, "FrameGrabberManager: Creating FrameGrabber with URL: " + rtspURL);
        try {
            FFmpegFrameGrabber grabber = FFmpegFrameGrabber.createDefault(rtspURL);
            grabber.setImageHeight(FRAME_HEIGHT);
            grabber.setImageWidth(FRAME_WIDTH);
            grabber.setOption("error_resilient", "0");
            grabber.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            grabber.setOption("rtsp_transport", "udp");
            grabber.setAudioChannels(0); // disable audio
            return grabber;
        } catch (Exception e) {
            XLog.e(TAG, "FrameGrabberManager: Failed to create FrameGrabber: " + e);
            return null;
        }
    }

    private FFmpegFrameFilter initializeFrameFilter(FFmpegFrameGrabber grabber) {
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
}
