package com.smartprints.rknn_vision_lab.core;

import android.graphics.Bitmap;
import android.view.SurfaceHolder;

import com.elvishew.xlog.XLog;
import com.smartprints.rknn_vision_lab.inference.InferenceEngine;
import com.smartprints.rknn_vision_lab.inference.RknnInferenceEngine;
import com.smartprints.rknn_vision_lab.video.pipeline.FrameBuffer;
import com.smartprints.rknn_vision_lab.video.pipeline.FrameReaderBuffer;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoPipeline implements VideoSourceListener{
    private final FrameBuffer<Bitmap> frameBuffer = new FrameReaderBuffer<>(2);
    private final FrameBuffer<Bitmap> displayBuffer = new FrameReaderBuffer<>(30);

    private final ExecutorService ingestExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "ingest-exec"));
    private final ExecutorService inferenceExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "inference-exec"));
    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "render-exec"));

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final InferenceEngine engine;
    private final SurfaceRenderer renderer;

    public VideoPipeline(SurfaceRenderer renderer, InferenceEngine engine) {
        this.renderer = renderer;
        this.engine = (engine != null) ? engine : new RknnInferenceEngine();
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;

        // Inference loop
        inferenceExecutor.execute(() -> {
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    Bitmap raw = frameBuffer.take();
                    Bitmap processed = engine.process(raw);
                    displayBuffer.offer(processed);
//                    raw.recycle();
                } catch (InterruptedException ie) {
//                    Thread.currentThread().interrupt();
                } catch (Throwable t) {
                    // TODO: log
                }
            }
        });

        // Render loop
        renderExecutor.execute(() -> {
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    Bitmap frame = displayBuffer.take();
                    renderer.drawFrame(frame);
//                    frame.recycle();
                } catch (InterruptedException ie) {
//                    Thread.currentThread().interrupt();
                } catch (Throwable t) {
                    // TODO: log
                }
            }
        });
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) return;

        ingestExecutor.shutdownNow();
        inferenceExecutor.shutdownNow();
        renderExecutor.shutdownNow();

        frameBuffer.clear();
        displayBuffer.clear();
        renderer.clear();
        engine.close();
    }

    @Override
    public void onFrameReady(Bitmap frame) {
        if (!running.get() || frame == null) return;
        ingestExecutor.execute(() -> frameBuffer.offer(frame));
    }

    @Override
    public void onStreamError(String error) {
        XLog.e(error);
    }

    @Override
    public void onStreamFinished() {
        XLog.d("Stream finished");
    }
}
