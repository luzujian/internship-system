package com.gdmu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

@ServletComponentScan
@MapperScan("com.gdmu.mapper")
@SpringBootApplication
@ComponentScan(basePackages = {"com.gdmu", "com.gdmu.auth.config"})
@EnableAsync
@EnableCaching
public class InternshipApplication {

    public static void main(String[] args) {
        NettySafeSpringApplication.run(InternshipApplication.class, args);
    }
    
    // 密码加密器和AuthenticationManager的Bean定义已移至SecurityConfig.java
    // 不再在此类中定义这些Bean，避免配置冲突

}