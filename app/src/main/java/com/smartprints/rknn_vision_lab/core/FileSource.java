package com.smartprints.rknn_vision_lab.core;

import static com.smartprints.rknn_vision_lab.core.CameraUtils.yuv420ToArgb;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;

import com.elvishew.xlog.XLog;

import org.bytedeco.javacv.AndroidFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.opencv.core.Mat;

import java.nio.ByteBuffer;

public class FileSource extends VideoSource{
    private static final String TAG = "VideoFileManager";
    private final VideoSourceListener videoSourceListener;
    private boolean running = false;
    private Uri uri;
    private Context context;

    public FileSource(Context context, VideoSourceListener videoSourceListener, Uri videoFileURI) {
        this.videoSourceListener = videoSourceListener;
        this.uri = videoFileURI;
        this.context = context;
    }

    @Override
    public void start() {
        new Thread(() -> {
            running = true;
            MediaExtractor extractor = new MediaExtractor();
            MediaCodec codec = null;

            try {
                extractor.setDataSource(context, uri, null);

                // Select video track
                int videoTrackIndex = -1;
                MediaFormat format = null;
                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    MediaFormat f = extractor.getTrackFormat(i);
                    String mime = f.getString(MediaFormat.KEY_MIME);
                    if (mime != null && mime.startsWith("video/")) {
                        videoTrackIndex = i;
                        format = f;
                        break;
                    }
                }
                if (videoTrackIndex < 0 || format == null) {
                    XLog.e(TAG, "No video track found");
                    videoSourceListener.onStreamError("No video track found");
                    return;
                }
                extractor.selectTrack(videoTrackIndex);

                String mime = format.getString(MediaFormat.KEY_MIME);
                codec = MediaCodec.createDecoderByType(mime);
                codec.configure(format, null, null, 0);
                codec.start();

                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                boolean inputEOS = false;
                final long timeoutUs = 10_000;

                // Lazily allocated from image crop size
                Bitmap bitmap = null;
                int[] pixelBuffer = null;
                int lastW = -1, lastH = -1;

                while (running) {
                    // Feed input
                    if (!inputEOS) {
                        int inIndex = codec.dequeueInputBuffer(timeoutUs);
                        if (inIndex >= 0) {
                            ByteBuffer inBuf = codec.getInputBuffer(inIndex);
                            if (inBuf != null) {
                                int size = extractor.readSampleData(inBuf, 0);
                                if (size < 0) {
                                    codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                    inputEOS = true;
                                } else {
                                    long ptsUs = extractor.getSampleTime();
                                    codec.queueInputBuffer(inIndex, 0, size, ptsUs, 0);
                                    extractor.advance();
                                }
                            }
                        }
                    }

                    int outIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs);
                    if (outIndex >= 0) {
                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            codec.releaseOutputBuffer(outIndex, false);
                            break;
                        }

                        Image image = codec.getOutputImage(outIndex);
                        if (image != null) {
                            // Use the visible crop for true frame width/height
                            android.graphics.Rect crop = image.getCropRect();
                            int w = crop.width();
                            int h = crop.height();

//                            if (bitmap == null || w != lastW || h != lastH) {
                                bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                                pixelBuffer = new int[w * h];
                                lastW = w;
                                lastH = h;
//                            }

                            // Convert without changing dimensions (no scaling)
                            yuv420ToArgb(image, bitmap, pixelBuffer);

                            if ( w > 2500 ) {
                                // Resize
                                int newWidth = 1280;
                                int newHeight = 720;
                                bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                            }

                            // Deliver as-is
                            videoSourceListener.onFrameReady(bitmap);

                            image.close();
                        }
                        codec.releaseOutputBuffer(outIndex, false);
                    }

                }

                videoSourceListener.onStreamFinished();

            } catch (Exception e) {
                XLog.e(TAG, "Error decoding video", e);
                videoSourceListener.onStreamError("Playback failed: " + e.getMessage());
            } finally {
                try { extractor.release(); } catch (Throwable ignored) {}
                if (codec != null) {
                    try { codec.stop(); } catch (Throwable ignored) {}
                    try { codec.release(); } catch (Throwable ignored) {}
                }
                running = false;
            }
        }, "FileSourceDecoder").start();
    }

    @Override public void stop() { running = false; }
    @Override public boolean isRunning() { return running; }
}