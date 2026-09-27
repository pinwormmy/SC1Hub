package com.sc1hub.common.security;

import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 키별 고정 윈도 카운터. 단일 인스턴스 인메모리이며 항목 수에 상한이 있어 임의 키 폭주로 메모리가
 * 늘어나지 않는다. 가득 차면 만료 항목을 먼저 비우고, 그래도 가득 차면 가장 오래된 윈도부터 일부를
 * 밀어내 새 키를 받는다(새 작성자를 사이트 전체에서 거부하지 않기 위함).
 */
@Component
public class WriteRateLimiter {

    static final int MAX_ENTRIES = 20_000;
    /** 가득 찼을 때 한 번에 밀어내는 항목 수. 매 요청마다 전체를 훑지 않도록 여유를 확보한다. */
    static final int EVICTION_BATCH = MAX_ENTRIES / 100;

    // 삽입 순서 = 윈도 시작 순서(만료된 윈도는 지우고 다시 넣으므로 뒤로 간다).
    private final Map<String, Window> windows = new LinkedHashMap<>();

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
                    evictOldest(windows.size() - MAX_ENTRIES + EVICTION_BATCH);
                }
            }
            window = new Window(now, windowMillis);
            windows.put(key, window);
        }
        return ++window.count <= limit;
    }

    private void evictOldest(int howMany) {
        Iterator<Map.Entry<String, Window>> iterator = windows.entrySet().iterator();
        for (int removed = 0; removed < howMany && iterator.hasNext(); removed++) {
            iterator.next();
            iterator.remove();
        }
    }

    /** 현재 윈도의 누적 횟수(만료됐으면 0). */
    public synchronized int count(String key) {
        Window window = windows.get(key);
        if (window == null || System.currentTimeMillis() - window.start >= window.length) {
            return 0;
        }
        return window.count;
    }

    synchronized int size() {
        return windows.size();
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
