package com.cloud_technological.aura_pos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;


@Configuration
public class FactusConfig {
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // Tiempo máximo para establecer conexión con Factus
        factory.setConnectTimeout(5_000);
        // Tiempo máximo esperando respuesta de Factus
        factory.setReadTimeout(8_000);
        return new RestTemplate(factory);
    }
}
