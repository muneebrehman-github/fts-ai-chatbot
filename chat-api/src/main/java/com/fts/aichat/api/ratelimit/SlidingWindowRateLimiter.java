package com.fts.aichat.api.ratelimit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory sliding-window rate limiter (per process). Suitable for a single instance.
 */
public class SlidingWindowRateLimiter {

    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();
    private final long windowMillis;

    public SlidingWindowRateLimiter(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    public boolean tryAcquire(String key, int maxPerWindow) {
        if (maxPerWindow <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        Deque<Long> deque = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && now - deque.peekFirst() >= windowMillis) {
                deque.removeFirst();
            }
            if (deque.size() >= maxPerWindow) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }
}
