package com.sc1hub.common.security;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 키별 고정 윈도 카운터. 단일 인스턴스 인메모리이며 항목 수에 상한이 있어 임의 키 폭주로 메모리가
 * 늘어나지 않는다(가득 차면 만료 항목을 먼저 비우고, 그래도 가득 차면 새 키는 거부한다).
 */
@Component
public class WriteRateLimiter {

    static final int MAX_ENTRIES = 20_000;

    private final Map<String, Window> windows = new HashMap<>();

    /** 윈도 안에서 {@code limit} 회까지 허용한다. 허용되면 true. */
    public synchronized boolean allow(String key, int limit, long windowMillis) {
        long now = System.currentTimeMillis();
        Window window = windows.get(key);
        if (window != null && now - window.start >= window.length) {
            windows.remove(key);
            window = null;
        }
        if (window == null) {
            if (windows.size() >= MAX_ENTRIES) {
                windows.entrySet().removeIf(entry -> now - entry.getValue().start >= entry.getValue().length);
                if (windows.size() >= MAX_ENTRIES) {
                    return false;
                }
            }
            window = new Window(now, windowMillis);
            windows.put(key, window);
        }
        return ++window.count <= limit;
    }

    /** 현재 윈도의 누적 횟수(만료됐으면 0). */
    public synchronized int count(String key) {
        Window window = windows.get(key);
        if (window == null || System.currentTimeMillis() - window.start >= window.length) {
            return 0;
        }
        return window.count;
    }

    private static final class Window {
        private final long start;
        private final long length;
        private int count;

        private Window(long start, long length) {
            this.start = start;
            this.length = length;
        }
    }
}
