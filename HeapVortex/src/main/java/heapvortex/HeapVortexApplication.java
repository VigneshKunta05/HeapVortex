package heapvortex;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HeapVortexApplication {
    public static void main(String[] args) {
        SpringApplication.run(HeapVortexApplication.class, args);
    }

    /** Starts the demo leak when heapvortex.simulate-leak=true. */
    @Bean
    ApplicationRunner leakSimulator(@Value("${heapvortex.simulate-leak:false}") boolean on) {
        return args -> { if (on) LeakSimulator.start(); };
    }
}

