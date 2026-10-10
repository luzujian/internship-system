package com.internship.config;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.util.FileSize;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosticLogConfigurationTest {
    @TempDir Path directory;

    @Test
    void productionConfigurationStartsWithBoundedDiagnosticFiles() throws Exception {
        assertConfiguration(null, "1GB");
    }

    @Test
    void deploymentCanOverrideDiagnosticFileBudget() throws Exception {
        assertConfiguration("256MB", "256MB");
    }

    private void assertConfiguration(String override, String expectedSize) throws Exception {
        try (var source = getClass().getResourceAsStream("/logback.xml")) {
            assertNotNull(source);
            // Load the real configuration while confining all file output to the test directory.
            String configuration = new String(source.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("./logs/", directory.toString().replace('\\', '/') + "/");
            LoggerContext context = new LoggerContext();
            try {
                if (override != null) context.putProperty("LOG_TOTAL_SIZE_CAP", override);
                JoranConfigurator configurator = new JoranConfigurator();
                configurator.setContext(context);
                configurator.doConfigure(new ByteArrayInputStream(configuration.getBytes(StandardCharsets.UTF_8)));

                var appender = (RollingFileAppender<?>) context.getLogger("ROOT").getAppender("FILE");
                assertNotNull(appender);
                assertTrue(appender.isStarted());
                var policy = (SizeAndTimeBasedRollingPolicy<?>) appender.getRollingPolicy();
                assertTrue(policy.isStarted());
                assertEquals(30, policy.getMaxHistory());
                assertEquals(FileSize.valueOf(expectedSize).getSize(),
                        ((FileSize) ReflectionTestUtils.getField(policy, "totalSizeCap")).getSize());
                assertFalse(context.getStatusManager().getCopyOfStatusList().stream()
                        .anyMatch(status -> status.getLevel() == Status.ERROR));
            } finally {
                context.stop();
            }
        }
    }
}
