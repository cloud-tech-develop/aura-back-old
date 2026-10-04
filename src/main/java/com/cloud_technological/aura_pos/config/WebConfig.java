package com.cloud_technological.aura_pos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final long maxAge = 3600;

    @org.springframework.beans.factory.annotation.Autowired
    private PermisoInterceptor permisoInterceptor;

    /** Permisos por perfil en cada petición al API (docs/PLAN_PERMISOS.md, fase P3). */
    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(permisoInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // Aplica a todos los endpoints
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*") // Importante si manejas autenticación con cookies o tokens
                .maxAge(maxAge);
    }
}
