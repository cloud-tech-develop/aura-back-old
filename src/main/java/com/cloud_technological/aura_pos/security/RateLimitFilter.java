package com.cloud_technological.aura_pos.security;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Límite de peticiones para {@code /api/auth/**} (fase 0 del plan de seguridad).
 *
 * <p>Sin él, {@code /api/auth/login} acepta intentos ilimitados: fuerza bruta de
 * contraseñas sin ruido, y {@code /forgot-password} permite inundar de correos a
 * un usuario.
 *
 * <p>Ventana fija en memoria, sin dependencias externas. Eso implica dos límites
 * conocidos:
 * <ul>
 *   <li><b>Por instancia.</b> Con varias réplicas del backend el límite efectivo
 *       se multiplica por el número de réplicas. Para un límite real y compartido
 *       hay que mover el contador a Redis (fase 2).</li>
 *   <li><b>Ventana fija, no deslizante.</b> En el borde entre dos ventanas caben
 *       hasta 2×máximo intentos. Suficiente para frenar fuerza bruta; no es un
 *       control antifraude.</li>
 * </ul>
 *
 * <p>El filtro se registra <b>solo</b> dentro de la cadena de Spring Security
 * (ver {@code SecurityConfig}); su registro automático como filtro de servlet
 * está desactivado para que no cuente dos veces la misma petición.
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    /** Rutas protegidas. Todo lo demás pasa sin contar. */
    private static final String RUTA_PROTEGIDA = "/api/auth/";

    /** Techo de IPs distintas en memoria; al superarlo se fuerza la limpieza. */
    private static final int MAX_ENTRADAS = 10_000;

    private final boolean habilitado;
    private final int maxIntentos;
    private final long ventanaSegundos;
    private final boolean confiarEnProxy;

    private final Map<String, Contador> contadores = new ConcurrentHashMap<>();
    private final AtomicLong ultimaLimpieza = new AtomicLong(Instant.now().getEpochSecond());

    public RateLimitFilter(
            @Value("${app.security.rate-limit.enabled:true}") boolean habilitado,
            @Value("${app.security.rate-limit.max-attempts:5}") int maxIntentos,
            @Value("${app.security.rate-limit.window-seconds:60}") long ventanaSegundos,
            @Value("${app.security.rate-limit.trust-forwarded-header:true}") boolean confiarEnProxy) {
        this.habilitado = habilitado;
        this.maxIntentos = maxIntentos;
        this.ventanaSegundos = ventanaSegundos;
        this.confiarEnProxy = confiarEnProxy;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!habilitado || !request.getRequestURI().startsWith(RUTA_PROTEGIDA)) {
            filterChain.doFilter(request, response);
            return;
        }

        long ahora = Instant.now().getEpochSecond();
        limpiarSiHaceFalta(ahora);

        String clave = ip(request) + "|" + request.getRequestURI();
        Contador contador = contadores.compute(clave, (k, actual) -> {
            if (actual == null || ahora - actual.inicioVentana >= ventanaSegundos) {
                return new Contador(ahora);
            }
            return actual;
        });

        int intentos = contador.intentos.incrementAndGet();
        if (intentos > maxIntentos) {
            long esperar = ventanaSegundos - (ahora - contador.inicioVentana);
            rechazar(response, Math.max(esperar, 1));
            log.warn("[RateLimit] {} bloqueado en {} ({} intentos en {}s)",
                    ip(request), request.getRequestURI(), intentos, ventanaSegundos);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void rechazar(HttpServletResponse response, long esperarSegundos) throws IOException {
        response.setStatus(429);   // Too Many Requests
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", String.valueOf(esperarSegundos));
        response.getWriter().write(
                "{\"status\":429,\"error\":true,\"message\":\"Demasiados intentos. "
                        + "Espere " + esperarSegundos + " segundos e intente de nuevo.\"}");
    }

    /**
     * IP del cliente. Detrás de un proxy la IP del socket es la del proxy, así
     * que todos los usuarios compartirían contador; por eso se lee el primer
     * salto de {@code X-Forwarded-For} cuando la propiedad lo permite.
     */
    private String ip(HttpServletRequest request) {
        if (confiarEnProxy) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * Purga ventanas vencidas. Sin esto el mapa crece con cada IP distinta y se
     * vuelve una fuga de memoria explotable a propósito.
     */
    private void limpiarSiHaceFalta(long ahora) {
        long anterior = ultimaLimpieza.get();
        boolean toca = ahora - anterior >= ventanaSegundos || contadores.size() > MAX_ENTRADAS;
        if (!toca || !ultimaLimpieza.compareAndSet(anterior, ahora)) {
            return;
        }
        contadores.entrySet().removeIf(e -> ahora - e.getValue().inicioVentana >= ventanaSegundos);
    }

    /** Ventana fija: momento en que empezó y cuántos intentos lleva. */
    private static final class Contador {
        final long inicioVentana;
        final AtomicInteger intentos = new AtomicInteger(0);

        Contador(long inicioVentana) {
            this.inicioVentana = inicioVentana;
        }
    }
}
