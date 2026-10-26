package com.internship.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionStartupGuardTest {
    private static class ServiceThatMustNotStart {
        static boolean started;

        ServiceThatMustNotStart() {
            started = true;
        }
    }

    @Test
    void nonProductionProfilesDoNotRequireProductionSecrets() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        assertDoesNotThrow(() -> new ProductionStartupGuard(environment).validate());
    }

    @Test
    void preproductionCannotBypassGuardByOmittingProductionProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("preprod");
        assertTrue(assertThrows(IllegalStateException.class,
                () -> new ProductionStartupGuard(environment).validate()).getMessage().contains("prod profile"));
        environment.setActiveProfiles("local");
        environment.setProperty("PREPROD_ISOLATION_REQUIRED", "true");
        assertTrue(assertThrows(IllegalStateException.class,
                () -> new ProductionStartupGuard(environment).validate()).getMessage().contains("prod profile"));
    }

    @Test
    void productionYamlAllowsPreproductionToDisableSms() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("SMS_ENABLED", "false");
        new YamlPropertySourceLoader().load("application-prod",
                new ClassPathResource("application-prod.yml"))
                .forEach(environment.getPropertySources()::addLast);
        assertFalse(environment.getProperty("sms.enabled", Boolean.class));
    }

    @Test
    void productionRejectsMissingEssentialsWithoutPrintingSecretValues() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        String message = assertThrows(IllegalStateException.class,
                () -> new ProductionStartupGuard(environment).validate()).getMessage();
        for (String name : new String[]{"DB_HOST", "DB_PASSWORD", "REDIS_HOST", "REDIS_PASSWORD",
                "JWT_SECRET", "MAIL_USERNAME", "MAIL_PASSWORD", "ALIYUN_OSS_BUCKET_NAME",
                "ALIYUN_OSS_ACCESS_KEY_ID", "ALIYUN_OSS_ACCESS_KEY_SECRET", "DEEPSEEK_API_KEY"}) {
            assertTrue(message.contains(name));
        }
    }

    @Test
    void productionGuardStopsContextBeforeOtherServicesStart() {
        ServiceThatMustNotStart.started = false;
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(new MockEnvironment());
            context.getEnvironment().setActiveProfiles("prod");
            context.register(ProductionStartupGuard.class, ServiceThatMustNotStart.class);
            RuntimeException failure = assertThrows(RuntimeException.class, context::refresh);
            assertTrue(failure.getMessage().contains("DB_HOST"));
            assertFalse(ServiceThatMustNotStart.started);
        }
    }

    @Test
    void productionRejectsPlaceholdersAndWeakJwtSecrets() {
        MockEnvironment environment = validProduction();
        environment.setProperty("DB_HOST", "${MISSING_DB_HOST}");
        environment.setProperty("REDIS_HOST", "redis.internal/path");
        environment.setProperty("JWT_SECRET", "short-secret");
        String message = assertThrows(IllegalStateException.class,
                () -> new ProductionStartupGuard(environment).validate()).getMessage();
        assertTrue(message.contains("DB_HOST"));
        assertTrue(message.contains("REDIS_HOST"));
        assertTrue(message.contains("JWT_SECRET"));
        assertFalse(message.contains("short-secret"));
    }

    @Test
    void productionRejectsDevelopmentJwtFallbackDespiteItsLength() {
        MockEnvironment environment = validProduction();
        environment.setProperty("JWT_SECRET", "DevOnlyJwtSecret2025DeepInternLocalDev!NotForProduction!!");
        assertThrows(IllegalStateException.class,
                () -> new ProductionStartupGuard(environment).validate());
    }

    @Test
    void productionAllowsExplicitLoopbackServicesAndStrongSecret() {
        MockEnvironment environment = validProduction();
        environment.setProperty("DB_HOST", "127.0.0.1");
        environment.setProperty("REDIS_HOST", "127.0.0.1");
        assertDoesNotThrow(() -> new ProductionStartupGuard(environment).postProcessBeanFactory(null));
    }

    @Test
    void productionChecksSmsCredentialsOnlyWhenSmsIsEnabled() {
        MockEnvironment environment = validProduction();
        assertDoesNotThrow(() -> new ProductionStartupGuard(environment).validate());
        environment.setProperty("sms.enabled", "true");
        String message = assertThrows(IllegalStateException.class,
                () -> new ProductionStartupGuard(environment).validate()).getMessage();
        assertTrue(message.contains("SMS_ACCESS_KEY_ID"));
        assertTrue(message.contains("SMS_ACCESS_KEY_SECRET"));
        environment.setProperty("SMS_ACCESS_KEY_ID", "sms-test-id");
        environment.setProperty("SMS_ACCESS_KEY_SECRET", "sms-test-secret");
        assertDoesNotThrow(() -> new ProductionStartupGuard(environment).validate());
    }

    @Test
    void productionAllowsDisabledAgentWithoutModelKey() {
        MockEnvironment environment = validProduction();
        environment.setProperty("agent.enabled", "false");
        environment.setProperty("DEEPSEEK_API_KEY", "");
        assertDoesNotThrow(() -> new ProductionStartupGuard(environment).validate());
        environment.setProperty("agent.enabled", "system");
        String message = assertThrows(IllegalStateException.class,
                () -> new ProductionStartupGuard(environment).validate()).getMessage();
        assertTrue(message.contains("DEEPSEEK_API_KEY"));
    }

    @Test
    void productionRejectsMissingMailAndOssCredentials() {
        MockEnvironment environment = validProduction();
        environment.setProperty("MAIL_PASSWORD", "");
        environment.setProperty("ALIYUN_OSS_ACCESS_KEY_SECRET", "${MISSING_OSS_SECRET}");
        String message = assertThrows(IllegalStateException.class,
                () -> new ProductionStartupGuard(environment).validate()).getMessage();
        assertTrue(message.contains("MAIL_PASSWORD"));
        assertTrue(message.contains("ALIYUN_OSS_ACCESS_KEY_SECRET"));
        assertFalse(message.contains("MISSING_OSS_SECRET"));
    }

    @Test
    void productionRejectsLegacyPlaintextPasswordCompatibility() {
        MockEnvironment environment = validProduction();
        environment.setProperty("security.allow-legacy-plaintext-passwords", "true");
        String message = assertThrows(IllegalStateException.class,
                () -> new ProductionStartupGuard(environment).validate()).getMessage();
        assertTrue(message.contains("security.allow-legacy-plaintext-passwords"));
    }

    @Test
    void preproductionRejectsSharedDatabaseBucketAndLiveNotificationRoutes() {
        MockEnvironment environment = validProduction();
        environment.setProperty("PREPROD_ISOLATION_REQUIRED", "true");
        environment.setProperty("PREPROD_EXPECTED_DB_HOST", "internai-db");
        environment.setProperty("PREPROD_EXPECTED_REDIS_HOST", "internai-cache");
        environment.setProperty("DB_NAME", "internship");
        environment.setProperty("PREPROD_EXPECTED_DB_NAME", "internship");
        environment.setProperty("DB_USERNAME", "user");
        environment.setProperty("ALIYUN_OSS_BUCKET_NAME", "lzj-java-ai");
        environment.setProperty("PREPROD_EXPECTED_OSS_BUCKET", "lzj-java-ai");
        environment.setProperty("MAIL_HOST", "smtp.example.test");
        environment.setProperty("AGENT_ENABLED", "system");
        String message = assertThrows(IllegalStateException.class,
                () -> new ProductionStartupGuard(environment).validate()).getMessage();
        for (String name : new String[]{"DB_HOST", "REDIS_HOST", "DB_NAME", "DB_USERNAME", "ALIYUN_OSS_BUCKET_NAME",
                "MAIL_HOST", "AGENT_ENABLED"}) {
            assertTrue(message.contains(name));
        }
        assertFalse(message.contains("smtp.example.test"));
    }

    @Test
    void preproductionAcceptsExplicitlySeparatedResources() {
        MockEnvironment environment = validProduction();
        environment.setActiveProfiles("prod", "preprod");
        environment.setProperty("DB_HOST", "internai-db");
        environment.setProperty("PREPROD_EXPECTED_DB_HOST", "internai-db");
        environment.setProperty("REDIS_HOST", "internai-cache");
        environment.setProperty("PREPROD_EXPECTED_REDIS_HOST", "internai-cache");
        environment.setProperty("DB_NAME", "internship_acceptance_test");
        environment.setProperty("PREPROD_EXPECTED_DB_NAME", "internship_acceptance_test");
        environment.setProperty("DB_USERNAME", "preprod_app");
        environment.setProperty("ALIYUN_OSS_BUCKET_NAME", "deepintern-preprod-test");
        environment.setProperty("PREPROD_EXPECTED_OSS_BUCKET", "deepintern-preprod-test");
        environment.setProperty("MAIL_HOST", "mailpit");
        environment.setProperty("AGENT_ENABLED", "false");
        environment.setProperty("agent.enabled", "false");
        environment.setProperty("DEEPSEEK_API_KEY", "");
        assertDoesNotThrow(() -> new ProductionStartupGuard(environment).validate());
    }

    private MockEnvironment validProduction() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("DB_HOST", "db.internal");
        environment.setProperty("DB_PASSWORD", "database-test-password");
        environment.setProperty("REDIS_HOST", "redis.internal");
        environment.setProperty("REDIS_PASSWORD", "redis-test-password");
        environment.setProperty("JWT_SECRET", "test-only-random-jwt-key-with-32-plus-bytes");
        environment.setProperty("MAIL_USERNAME", "mail@example.test");
        environment.setProperty("MAIL_PASSWORD", "mail-test-password");
        environment.setProperty("ALIYUN_OSS_BUCKET_NAME", "test-bucket");
        environment.setProperty("ALIYUN_OSS_ACCESS_KEY_ID", "oss-test-id");
        environment.setProperty("ALIYUN_OSS_ACCESS_KEY_SECRET", "oss-test-secret");
        environment.setProperty("DEEPSEEK_API_KEY", "model-test-key");
        environment.setProperty("sms.enabled", "false");
        return environment;
    }
}
