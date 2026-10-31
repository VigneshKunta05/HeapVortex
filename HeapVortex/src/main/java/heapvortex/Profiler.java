package heapvortex;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;
import javax.management.*;
import javax.management.remote.*;
import java.lang.management.*;
import java.util.*;
import java.util.regex.*;

/** Samples the heap class histogram via JMX and flags classes with sustained growth. */
@Component
public class Profiler {
    record ClassStat(String name, long instances, long bytes, double slope, int streak, boolean leak, int severity) {
        ClassStat withSeverity(int s) { return new ClassStat(name, instances, bytes, slope, streak, leak, s); }
    }

    static final int WINDOW = 30;          // samples kept per class
    static final int MIN_STREAK = 5;       // consecutive increases needed
    static final double MIN_SLOPE = 10_000; // bytes/sample growth needed
    private static final Pattern LINE = Pattern.compile("^\\s*\\d+:\\s+(\\d+)\\s+(\\d+)\\s+(.+)$");

    private final MBeanServerConnection conn;
    private final MemoryMXBean mem;
    private final ObjectName dcmd;
    private final Map<String, Deque<Long>> history = new HashMap<>();
    private volatile String json = "{\"classes\":[],\"heapUsed\":0,\"heapMax\":0}";

    private final Store store;
    private volatile int intervalSec = 2;

    public void setInterval(int s) { intervalSec = Math.max(1, s); }

    public Profiler(@Value("${heapvortex.jmx:}") String hostPort,
                    @Value("${heapvortex.interval-seconds:2}") int interval, Store store) throws Exception {
        this.store = store;
        this.intervalSec = Math.max(1, interval);
        if (hostPort == null || hostPort.isBlank()) {
            conn = ManagementFactory.getPlatformMBeanServer();
        } else {
            JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + hostPort + "/jmxrmi");
            conn = JMXConnectorFactory.connect(url).getMBeanServerConnection();
        }
        mem = ManagementFactory.newPlatformMXBeanProxy(conn, ManagementFactory.MEMORY_MXBEAN_NAME, MemoryMXBean.class);
        dcmd = new ObjectName("com.sun.management:type=DiagnosticCommand");
    }

    public String json() { return json; }

    @Scheduled(fixedDelayString = "${heapvortex.interval-seconds:2}", timeUnit = TimeUnit.SECONDS)
    public void sample() {
        try {
            String out = (String) conn.invoke(dcmd, "gcClassHistogram",
                    new Object[]{new String[0]}, new String[]{String[].class.getName()});
            MemoryUsage used = mem.getHeapMemoryUsage();
            List<ClassStat> all = new ArrayList<>();
            for (String line : out.split("\n")) {
                Matcher m = LINE.matcher(line);
                if (!m.matches()) continue;
                long inst = Long.parseLong(m.group(1)), bytes = Long.parseLong(m.group(2));
                String name = m.group(3).trim().replaceAll("\\s*\\(.*\\)$", "");
                Deque<Long> h = history.computeIfAbsent(name, k -> new ArrayDeque<>());
                h.addLast(bytes);
                if (h.size() > WINDOW) h.removeFirst();
                ClassStat a = analyze(name, inst, bytes, h);
                all.add(a.leak() ? a.withSeverity(severity(a.slope(), a.bytes(), used.getUsed(), used.getMax(), a.streak(), intervalSec)) : a);
            }
            all.sort(Comparator.comparingLong(ClassStat::bytes).reversed());
            List<ClassStat> top = new ArrayList<>(all.subList(0, Math.min(40, all.size())));
            for (ClassStat c : all) if (c.leak() && !top.contains(c)) top.add(c);
            json = toJson(top);
            if (store != null) store.save(System.currentTimeMillis(), top);
        } catch (Exception e) {
            System.err.println("Sampling failed: " + e);
        }
    }

    static ClassStat analyze(String name, long inst, long bytes, Deque<Long> h) {
        Long[] v = h.toArray(new Long[0]);
        int streak = 0;
        for (int i = v.length - 1; i > 0 && v[i] > v[i - 1]; i--) streak++;
        double slope = 0;
        int n = v.length;
        if (n >= 2) { // least-squares slope of bytes vs. sample index
            double sx = 0, sy = 0, sxy = 0, sxx = 0;
            for (int i = 0; i < n; i++) { sx += i; sy += v[i]; sxy += i * (double) v[i]; sxx += (double) i * i; }
            double d = n * sxx - sx * sx;
            slope = d == 0 ? 0 : (n * sxy - sx * sy) / d;
        }
        return new ClassStat(name, inst, bytes, slope, streak, streak >= MIN_STREAK && slope >= MIN_SLOPE, 0);
    }

    /** 0-100: 50% urgency (time to heap exhaustion), 30% share of heap, 20% growth persistence. */
    static int severity(double slopePerSample, long bytes, long used, long max, int streak, int intervalSec) {
        double perSec = slopePerSample / Math.max(1, intervalSec);
        double headroom = Math.max(0, (max > 0 ? max : used * 4) - used);
        double secs = perSec > 0 ? headroom / perSec : Double.MAX_VALUE;
        double urgency = secs <= 300 ? 1 : secs >= 7200 ? 0 : 1 - (secs - 300) / 6900;
        double share = Math.min(1, (double) bytes / Math.max(1, used) * 4);
        double persistence = Math.min(1, streak / (double) WINDOW);
        return (int) Math.round(100 * (0.5 * urgency + 0.3 * share + 0.2 * persistence));
    }

    static String level(int s) { return s >= 70 ? "CRITICAL" : s >= 40 ? "HIGH" : s >= 20 ? "MEDIUM" : "LOW"; }

    private String toJson(List<ClassStat> list) {
        MemoryUsage u = mem.getHeapMemoryUsage();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"ts\":").append(System.currentTimeMillis())
          .append(",\"heapUsed\":").append(u.getUsed())
          .append(",\"heapMax\":").append(u.getMax())
          .append(",\"interval\":").append(intervalSec)
          .append(",\"classes\":[");
        for (int i = 0; i < list.size(); i++) {
            ClassStat c = list.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"name\":\"").append(c.name().replace("\\", "\\\\").replace("\"", "\\\"")).append("\"")
              .append(",\"instances\":").append(c.instances())
              .append(",\"bytes\":").append(c.bytes())
              .append(",\"slope\":").append(Math.round(c.slope()))
              .append(",\"streak\":").append(c.streak())
              .append(",\"severity\":").append(c.severity())
              .append(",\"leak\":").append(c.leak()).append('}');
        }
        return sb.append("]}").toString();
    }
}
