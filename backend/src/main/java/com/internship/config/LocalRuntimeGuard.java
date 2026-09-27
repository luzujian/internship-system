package com.internship.config;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Local acceptance isolates outbound mail and automatic jobs; interactive file access uses normal permissions. */
@Component
public class LocalRuntimeGuard {
    private final Environment environment;
    public LocalRuntimeGuard(Environment environment) { this.environment = environment; }
    public boolean isLocal() { return environment.acceptsProfiles(Profiles.of("local")); }
    public void requireExternalService(String service) {
        if (isLocal()) throw new IllegalStateException("本地验收环境已禁用外部" + service + "，未执行操作。");
    }
}
