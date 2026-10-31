package heapvortex;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.sql.*;
import java.util.*;

/** SQLite persistence for class snapshots. */
@Component
public class Store {
    private final Connection c;

    public Store(@Value("${heapvortex.db:heapvortex.db}") String path) throws SQLException {
        c = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS snapshots(ts INTEGER, class TEXT, instances INTEGER, bytes INTEGER, leak INTEGER)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_class_ts ON snapshots(class, ts)");
        }
    }

    public synchronized void save(long ts, List<Profiler.ClassStat> list) {
        try (PreparedStatement p = c.prepareStatement("INSERT INTO snapshots VALUES(?,?,?,?,?)")) {
            c.setAutoCommit(false);
            for (Profiler.ClassStat s : list) {
                p.setLong(1, ts); p.setString(2, s.name()); p.setLong(3, s.instances());
                p.setLong(4, s.bytes()); p.setInt(5, s.leak() ? 1 : 0); p.addBatch();
            }
            p.executeBatch(); c.commit(); c.setAutoCommit(true);
        } catch (SQLException e) { System.err.println("DB save failed: " + e); }
    }

    /** Returns [[ts,bytes],...] oldest first, newest {@code limit} points. */
    public synchronized String historyJson(String cls, int limit) {
        List<String> rows = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement("SELECT ts,bytes FROM snapshots WHERE class=? ORDER BY ts DESC LIMIT ?")) {
            p.setString(1, cls); p.setInt(2, limit);
            try (ResultSet r = p.executeQuery()) { while (r.next()) rows.add("[" + r.getLong(1) + "," + r.getLong(2) + "]"); }
        } catch (SQLException e) { System.err.println("DB read failed: " + e); }
        Collections.reverse(rows);
        return "[" + String.join(",", rows) + "]";
    }
}
