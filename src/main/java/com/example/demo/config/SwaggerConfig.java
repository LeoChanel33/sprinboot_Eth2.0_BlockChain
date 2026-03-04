package com.example.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI eth20OpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ETH 2.0 Blockchain Backend API")
                        .description("此 API 專為全端整合設計，支援 Nuxt 3 錢包交互與 AWS EKS 節點部署管理")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Leo - Project Lead")
                                .email("leo@example.com")));
    }
}