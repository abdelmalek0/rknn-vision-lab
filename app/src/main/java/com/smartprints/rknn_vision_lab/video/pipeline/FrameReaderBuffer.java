package com.smartprints.rknn_vision_lab.video.pipeline;

import java.util.ArrayDeque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class FrameReaderBuffer<T> implements FrameBuffer<T> {
    private final ArrayDeque<T> deque = new ArrayDeque<>();
    private final int capacity;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();

    public FrameReaderBuffer(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    @Override
    public boolean offer(T item) {
        lock.lock();
        try {
            if (deque.size() == capacity) {
                // Drop oldest to keep latency small
                deque.removeFirst();
            }
            deque.addLast(item);
            notEmpty.signal();
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public T take() throws InterruptedException {
        lock.lock();
        try {
            while (deque.isEmpty()) {
                notEmpty.await();
            }
            return deque.removeFirst();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            deque.clear();
        } finally {
            lock.unlock();
        }
    }
}
