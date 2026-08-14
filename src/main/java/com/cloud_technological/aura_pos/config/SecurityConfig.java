package com.cloud_technological.aura_pos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.cloud_technological.aura_pos.security.JwtAuthenticationFilter;
import com.cloud_technological.aura_pos.security.RateLimitFilter;
import com.cloud_technological.aura_pos.services.implementations.CustomUserDetailsService;

import lombok.RequiredArgsConstructor;




@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final CustomUserDetailsService customUserDetailsService;

    /** Swagger publica el catálogo completo de endpoints: solo en desarrollo. */
    @Value("${app.swagger.enabled:false}")
    private boolean swaggerHabilitado;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {})
            .authorizeHttpRequests(auth -> {
                auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers("/api/auth/login").permitAll()
                    .requestMatchers("/api/auth/forgot-password").permitAll()
                    .requestMatchers("/api/auth/reset-password").permitAll()
                    .requestMatchers("/api/municipios/**").permitAll()
                    .requestMatchers("/api/platform/**").hasAuthority("PLATFORM_ADMIN")
                    .requestMatchers("/api/auth/register").hasAuthority("PLATFORM_ADMIN");

                if (swaggerHabilitado) {
                    auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll();
                }

                auth.anyRequest().authenticated();
            })

            // Configuración Stateless (No crear sesiones de servidor)
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Proveedor de autenticación (BD + PasswordEncoder)
            .authenticationProvider(authenticationProvider())

            // Añadir nuestro filtro JWT antes del filtro estándar de usuario/pass
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

            // El límite de peticiones va antes de todo: frena la fuerza bruta
            // sin gastar ciclos validando credenciales.
            .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Spring Boot registra automáticamente en la cadena de servlets cualquier
     * bean de tipo Filter. Sin esto, RateLimitFilter correría dos veces por
     * petición y consumiría el doble de intentos.
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> desactivarRegistroAutomatico(RateLimitFilter filtro) {
        FilterRegistrationBean<RateLimitFilter> registro = new FilterRegistrationBean<>(filtro);
        registro.setEnabled(false);
        return registro;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
