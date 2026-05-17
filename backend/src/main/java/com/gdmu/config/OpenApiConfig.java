package com.gdmu.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("实习管理系统 API")
                        .version("1.0")
                        .description("实习管理系统接口文档")
                        .contact(new Contact()
                                .name("开发团队")
                                .email("dev@gdmu.com")));
    }
}