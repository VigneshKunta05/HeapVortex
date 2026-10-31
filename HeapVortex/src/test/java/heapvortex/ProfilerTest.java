package heapvortex;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProfilerTest {
    private static Deque<Long> series(long... v) { Deque<Long> d = new ArrayDeque<>(); for (long x : v) d.add(x); return d; }

    @Test void steadyGrowthIsLeak() {
        Profiler.ClassStat s = Profiler.analyze("A", 10, 2_000_000, series(1_000_000, 1_100_000, 1_200_000, 1_300_000, 1_400_000, 1_500_000, 1_600_000));
        assertTrue(s.leak()); assertEquals(6, s.streak()); assertEquals(100_000, s.slope(), 1);
    }
    @Test void flatUsageIsNotLeak() {
        assertFalse(Profiler.analyze("B", 1, 5, series(5_000, 5_000, 5_000, 5_000, 5_000, 5_000, 5_000)).leak());
    }
    @Test void growthThenDropIsNotLeak() {
        assertFalse(Profiler.analyze("C", 1, 5, series(1_000_000, 1_100_000, 1_200_000, 1_300_000, 1_400_000, 1_500_000, 200_000)).leak());
    }
    @Test void tinyGrowthBelowThresholdIsNotLeak() {
        assertFalse(Profiler.analyze("D", 1, 5, series(1000, 1010, 1020, 1030, 1040, 1050, 1060)).leak());
    }

    @Test void fastGrowthNearHeapLimitIsCritical() {
        int sev = Profiler.severity(1_000_000, 50_000_000, 100_000_000, 200_000_000, 30, 2);
        assertTrue(sev >= 70); assertEquals("CRITICAL", Profiler.level(sev));
    }
    @Test void slowGrowthWithLargeHeadroomIsLow() {
        int sev = Profiler.severity(20_000, 1_000_000, 100_000_000, 1_000_000_000, 5, 2);
        assertEquals("LOW", Profiler.level(sev));
    }
}
