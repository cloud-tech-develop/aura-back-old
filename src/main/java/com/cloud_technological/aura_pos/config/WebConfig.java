package com.cloud_technological.aura_pos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final long maxAge = 3600;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // Aplica a todos los endpoints
                .allowedOrigins("http://localhost:4200", "https://aura-post.vercel.app/", "https://www.aura-pos.tech/","https://aura-pos.tech/") // Permite
                                                                                                                        // el
                                                                                                                        // frontend
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true) // Importante si manejas autenticación con cookies o tokens
                .maxAge(maxAge);
    }
}
