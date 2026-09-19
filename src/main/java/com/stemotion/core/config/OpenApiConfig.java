package com.stemotion.core.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI stemotionOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("STEMotion API")
                .description("API documentation for STEMotion — AI-Assisted STEM Video Production Platform")
                .version("0.1.0")
                .contact(new Contact()
                    .name("STEMotion Team")
                    .email("stemotion@fpt.edu.vn"))
                .license(new License()
                    .name("MIT")))
            .servers(List.of(
                new Server()
                    .url("http://localhost:8080")
                    .description("Local Development")
            ));
    }
}
