package com.gdmu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

public class NettySafeSpringApplication extends SpringApplication {

    public NettySafeSpringApplication(Class<?>... primarySources) {
        super(primarySources);
        configureNettyBeforeInit();
    }

    private void configureNettyBeforeInit() {
        System.setProperty("io.netty.noUnsafe", "true");
        System.setProperty("io.netty.tryReflectionSetAccessible", "false");
        System.setProperty("io.netty.transport.noNative", "true");
        System.setProperty("io.netty.leakDetection.level", "DISABLED");
        System.setProperty("io.netty.allocator.type", "unpooled");
        System.setProperty("io.netty.recycler.maxCapacityPerThread", "0");
        System.setProperty("io.netty.machineId", "00:00:00:00:00:00");

        long pid = getProcessId();
        System.setProperty("io.netty.processId", String.valueOf(pid));
    }

    private long getProcessId() {
        try {
            return ProcessHandle.current().pid();
        } catch (Exception e) {
            return 1;
        }
    }

    public static ConfigurableApplicationContext run(Class<?> primarySource, String... args) {
        return new NettySafeSpringApplication(primarySource).run(args);
    }
}