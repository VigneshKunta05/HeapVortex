package heapvortex;

import java.util.*;

/** Demo workload: SessionData leaks (retained forever); CacheEntry is a bounded cache (must NOT be flagged). */
public class LeakSimulator {
    static final List<Object> LEAK = new ArrayList<>();
    static final ArrayDeque<CacheEntry> CACHE = new ArrayDeque<>();

    static class SessionData { final byte[] payload = new byte[8 * 1024]; final String id = UUID.randomUUID().toString(); }
    static class CacheEntry { final byte[] payload = new byte[2 * 1024]; }

    public static void start() {
        Thread t = new Thread(() -> {
            while (true) {
                LEAK.add(new SessionData());
                CACHE.addLast(new CacheEntry());
                if (CACHE.size() > 100) CACHE.pollFirst();   // bounded -> plateaus
                byte[] garbage = new byte[4096];
                try { Thread.sleep(50); } catch (InterruptedException e) { return; }
            }
        }, "leak-simulator");
        t.setDaemon(true);
        t.start();
    }
}
