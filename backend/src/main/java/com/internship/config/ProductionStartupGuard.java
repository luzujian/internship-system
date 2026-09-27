package com.internship.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Reject missing production essentials before application services connect to them. */
@Component
public final class ProductionStartupGuard implements BeanFactoryPostProcessor, EnvironmentAware {
    private static final String DEVELOPMENT_JWT_SECRET =
            "DevOnlyJwtSecret2025DeepInternLocalDev!NotForProduction!!";

    private Environment environment;

    public ProductionStartupGuard() {
    }

    public ProductionStartupGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        validate();
    }

    void validate() {
        boolean preproductionRequested = environment.acceptsProfiles(Profiles.of("preprod"))
                || environment.getProperty("PREPROD_ISOLATION_REQUIRED", Boolean.class, false);
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            if (preproductionRequested) {
                throw new IllegalStateException("预发布环境必须同时启用 prod profile");
            }
            return;
        }

        List<String> invalid = new ArrayList<>();
        requireHost("DB_HOST", invalid);
        requireValue("DB_PASSWORD", invalid);
        requireHost("REDIS_HOST", invalid);
        requireValue("REDIS_PASSWORD", invalid);
        requireValue("MAIL_USERNAME", invalid);
        requireValue("MAIL_PASSWORD", invalid);
        requireValue("ALIYUN_OSS_BUCKET_NAME", invalid);
        requireValue("ALIYUN_OSS_ACCESS_KEY_ID", invalid);
        requireValue("ALIYUN_OSS_ACCESS_KEY_SECRET", invalid);
        if (!"false".equalsIgnoreCase(environment.getProperty("agent.enabled", "system").trim())) {
            requireValue("DEEPSEEK_API_KEY", invalid);
        }
        if (environment.getProperty("sms.enabled", Boolean.class, false)) {
            requireValue("SMS_ACCESS_KEY_ID", invalid);
            requireValue("SMS_ACCESS_KEY_SECRET", invalid);
        }
        if (environment.getProperty("security.allow-legacy-plaintext-passwords", Boolean.class, false)) {
            invalid.add("security.allow-legacy-plaintext-passwords (生产环境必须为 false)");
        }

        if (preproductionRequested) {
            requirePreproductionIsolation(invalid);
        }

        String jwtSecret = value("JWT_SECRET");
        if (!configured(jwtSecret)
                || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32
                || DEVELOPMENT_JWT_SECRET.equals(jwtSecret)
                || "defaultSecretChangeInProduction_ReplaceThisWithRandomSecret1234567890==".equals(jwtSecret)
                || "aXRoZWltYQ==".equals(jwtSecret)) {
            invalid.add("JWT_SECRET (至少 32 字节且不能使用开发默认值)");
        }

        if (!invalid.isEmpty()) {
            throw new IllegalStateException("生产环境配置缺失或无效: " + String.join(", ", invalid));
        }
    }

    private void requirePreproductionIsolation(List<String> invalid) {
        String dbHost = value("DB_HOST");
        String expectedDbHost = value("PREPROD_EXPECTED_DB_HOST");
        if (!configured(dbHost) || !configured(expectedDbHost)
                || !dbHost.equalsIgnoreCase(expectedDbHost)) {
            invalid.add("DB_HOST/PREPROD_EXPECTED_DB_HOST");
        }
        String redisHost = value("REDIS_HOST");
        String expectedRedisHost = value("PREPROD_EXPECTED_REDIS_HOST");
        if (!configured(redisHost) || !configured(expectedRedisHost)
                || !redisHost.equalsIgnoreCase(expectedRedisHost)) {
            invalid.add("REDIS_HOST/PREPROD_EXPECTED_REDIS_HOST");
        }
        String dbName = value("DB_NAME");
        String expectedDbName = value("PREPROD_EXPECTED_DB_NAME");
        String dbUsername = value("DB_USERNAME");
        if (!configured(dbName) || !configured(expectedDbName)
                || !dbName.equals(expectedDbName) || "internship".equalsIgnoreCase(dbName)) {
            invalid.add("DB_NAME/PREPROD_EXPECTED_DB_NAME");
        }
        if (!configured(dbUsername) || "user".equalsIgnoreCase(dbUsername)) {
            invalid.add("DB_USERNAME");
        }

        String bucket = value("ALIYUN_OSS_BUCKET_NAME");
        String expectedBucket = value("PREPROD_EXPECTED_OSS_BUCKET");
        if (!configured(bucket) || !configured(expectedBucket)
                || !bucket.equals(expectedBucket) || "lzj-java-ai".equalsIgnoreCase(bucket)) {
            invalid.add("ALIYUN_OSS_BUCKET_NAME/PREPROD_EXPECTED_OSS_BUCKET");
        }
        if (!"mailpit".equalsIgnoreCase(value("MAIL_HOST"))) {
            invalid.add("MAIL_HOST");
        }
        if (!"false".equalsIgnoreCase(value("AGENT_ENABLED"))
                || environment.getProperty("sms.enabled", Boolean.class, false)) {
            invalid.add("AGENT_ENABLED/SMS_ENABLED");
        }
    }

    private void requireHost(String name, List<String> invalid) {
        String host = value(name);
        if (!configured(host) || host.contains("/") || host.contains("?") || host.contains("#")
                || host.contains("@") || host.chars().anyMatch(Character::isWhitespace)) {
            invalid.add(name);
        }
    }

    private void requireValue(String name, List<String> invalid) {
        if (!configured(value(name))) {
            invalid.add(name);
        }
    }

    private String value(String name) {
        try {
            String value = environment.getProperty(name);
            return value == null ? null : value.trim();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean configured(String value) {
        return value != null && !value.isBlank() && !value.contains("${");
    }
}
