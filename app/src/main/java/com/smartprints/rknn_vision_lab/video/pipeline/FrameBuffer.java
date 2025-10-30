package com.smartprints.rknn_vision_lab.video.pipeline;

public interface FrameBuffer<T> {
    // Non-blocking; may drop according to buffer policy
    boolean offer(T item);

    // Blocking take; waits until an item is available
    T take() throws InterruptedException;

    void clear();
}
