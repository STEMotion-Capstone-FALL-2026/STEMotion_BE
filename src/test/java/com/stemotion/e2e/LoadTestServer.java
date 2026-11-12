package com.stemotion.e2e;

import com.stemotion.StemotionApplication;
import com.stemotion.perf.LoadTestSeeder;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Dedicated server runner for RF-02 load testing baseline.
 * Boots real backend on throwaway embedded PostgreSQL with isolated mock integrations
 * and seeds baseline test data (200 users, 3 workspaces, 150 projects, 1500 scenes).
 * Listens on 0.0.0.0:18080 to allow Dockerized k6 containers to benchmark via host.docker.internal.
 */
public class LoadTestServer {

    public static void main(String[] args) {
        System.setProperty("spring.devtools.restart.enabled", "false");
        String port = System.getenv().getOrDefault("LOAD_TEST_PORT", "18080");
        String[] customArgs = new String[]{
            "--server.port=" + port,
            "--server.address=0.0.0.0",
            "--app.auth-v2.rate-limit-attempts=100000",
        };
        String[] finalArgs = E2eServer.launchArguments(customArgs);
        SpringApplication.from(StemotionApplication::main)
            .with(E2eServer.Config.class, Config.class)
            .run(finalArgs);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import(LoadTestSeeder.class)
    static class Config {

        @Bean
        ApplicationRunner triggerSeeder(LoadTestSeeder seeder) {
            return args -> {
                seeder.seed();
                System.out.println("[LoadTestServer] ========================================================");
                System.out.println("[LoadTestServer] READY FOR K6 LOAD TESTING");
                System.out.println("[LoadTestServer] Target URL: http://host.docker.internal:18080/api/v1");
                System.out.println("[LoadTestServer] ========================================================");
            };
        }
    }
}
