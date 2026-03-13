package com.gdmu.config;

import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
public class NettyConfig {
    
    @PostConstruct
    public void init() {
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
}
