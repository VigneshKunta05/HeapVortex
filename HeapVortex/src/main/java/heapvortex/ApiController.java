package heapvortex;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final Profiler profiler;
    private final Store store;

    public ApiController(Profiler profiler, Store store) {
        this.profiler = profiler;
        this.store = store;
    }

    @GetMapping(value = "/snapshot", produces = "application/json")
    public String snapshot() { return profiler.json(); }

    @GetMapping(value = "/history", produces = "application/json")
    public String history(@RequestParam("class") String cls) { return store.historyJson(cls, 200); }
}
