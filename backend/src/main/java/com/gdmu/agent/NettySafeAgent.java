package com.gdmu.agent;

import java.lang.instrument.Instrumentation;

public class NettySafeAgent {
    
    public static void premain(String agentArgs, Instrumentation inst) {
        disableNettyUnsafe();
    }
    
    public static void agentmain(String agentArgs, Instrumentation inst) {
        disableNettyUnsafe();
    }
    
    private static void disableNettyUnsafe() {
        System.setProperty("io.netty.noUnsafe", "true");
        System.setProperty("io.netty.tryReflectionSetAccessible", "false");
        System.setProperty("io.netty.transport.noNative", "true");
        System.setProperty("io.netty.leakDetection.level", "DISABLED");
        System.setProperty("io.netty.allocator.type", "unpooled");
        System.setProperty("io.netty.recycler.maxCapacityPerThread", "0");
        System.setProperty("io.netty.machineId", "00:00:00:00:00:00");
        
        long pid = getProcessId();
        System.setProperty("io.netty.processId", String.valueOf(pid));
        
        System.setProperty("jdk.internal.misc.Unsafe.ALLOWED", "false");
    }
    
    private static long getProcessId() {
        try {
            return ProcessHandle.current().pid();
        } catch (Exception e) {
            return 1;
        }
    }
}
