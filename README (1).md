# HeapVortex: 3D JVM Memory Leak Profiler

Samples a JVM's heap class histogram over JMX, flags classes with sustained growth
(>= 5 consecutive increases and >= 10 KB/sample least-squares slope), and renders
them as a live 3D vortex in the browser (three.js).

## Requirements
Java 17+. No build tool or dependencies needed.

## Build and run
    javac -d out src/heapvortex/*.java

    # Demo: profile itself while a simulated leak runs
    java -cp out heapvortex.Main --simulate-leak

    # Profile another JVM (start it with:
    #   -Dcom.sun.management.jmxremote.port=9010 -Dcom.sun.management.jmxremote.authenticate=false
    #   -Dcom.sun.management.jmxremote.ssl=false)
    java -cp out heapvortex.Main --jmx localhost:9010

Open http://localhost:8080 (run from the project root so `web/index.html` is found).
Options: `--port N`, `--interval SECONDS`.

## Architecture
- `Profiler`: JMX `DiagnosticCommand.gcClassHistogram` + `MemoryMXBean`, per-class history, leak scoring, JSON snapshot
- `Main`: scheduler + embedded `com.sun.net.httpserver` (`/` and `/api/snapshot`)
- `LeakSimulator`: static list retaining 8 KB `SessionData` objects, used for demos
- `web/index.html`: 3D visualization and leak-suspect panel

## Limitations / next steps
Class-level only (no reference-path/dominator analysis); a heap dump + retained-size
analysis, a database for storing history, and unit tests for the detector are the natural extensions.
