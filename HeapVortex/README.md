# HeapVortex v2.0: 3D JVM Memory Leak Profiler (Spring Boot)

Samples a JVM's heap class histogram over JMX, flags classes with sustained growth, ranks them by severity (0-100),
stores snapshots in SQLite, and shows a live 3D vortex in the browser (three.js) with a downloadable leak report.

## Run
IntelliJ: open `pom.xml` as a project (JDK 21), run `HeapVortexApplication`, open http://localhost:8080.
Command line: `mvn test` then `mvn spring-boot:run`.

Demo result: `LeakSimulator$SessionData` turns red after ~15 s; the bounded `CacheEntry` stays blue.

## Configuration (`src/main/resources/application.properties`)
| Property | Meaning |
|---|---|
| `heapvortex.simulate-leak` | start the demo leak (set false for real use) |
| `heapvortex.interval-seconds` | sampling interval |
| `heapvortex.db` | SQLite file |
| `heapvortex.jmx` | `host:port` of another JVM (start it with `-Dcom.sun.management.jmxremote.port=9010 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false`) |

## Structure
- `HeapVortexApplication`: Spring Boot entry point, scheduling, optional leak simulator
- `Profiler` (`@Component`, `@Scheduled`): JMX histogram, leak detection, severity scoring
- `Store` (`@Component`): SQLite persistence
- `ApiController`: `GET /api/snapshot`, `GET /api/history?class=NAME`
- `LeakSimulator`: leaking class plus a bounded-cache control
- `static/index.html`: 3D view, history chart, pause, report download
- `ProfilerTest`: JUnit 5 tests for detection and severity

## Limitations
Class-level detection only; JMX without authentication is for local use only.
