package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.cloud_technological.aura_pos.dto.factus.FactusTokenResponseDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.utils.FactusNoDisponibleException;
import com.cloud_technological.aura_pos.utils.GlobalException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio EXCLUSIVO para manejo del token Factus.
 *
 * Separado de FactusService para que el @CircuitBreaker y @Retry
 * de cada uno funcionen correctamente vía AOP (proxy de Spring).
 * Si estuvieran en la misma clase, la auto-invocación saltaría el proxy
 * y los interceptores de Resilience4j no se ejecutarían.
 */
@Slf4j
@Service
public class FactusTokenService {

    private static final String FACTUS_TOKEN_URL =
            "https://api.factus.com.co/oauth/token";

    private final RestTemplate             restTemplate;
    private final EmpresaJPARepository     empresaRepository;

    public FactusTokenService(RestTemplate restTemplate,
                               EmpresaJPARepository empresaRepository) {
        this.restTemplate      = restTemplate;
        this.empresaRepository = empresaRepository;
    }

    // ─────────────────────────────────────────────────────────────────
    // Método público con Circuit Breaker + Retry propios
    // ─────────────────────────────────────────────────────────────────

    /**
     * Retorna un access_token válido para la empresa.
     *
     * Estrategia (en orden):
     *  1. Token en caché (BD) con más de 5 min de vida → reutilizar
     *  2. Refresh token disponible                     → renovar sin password
     *  3. Login completo con client_id + client_secret + user/pass
     *
     * Circuit Breaker "factus-token":
     *  - Se abre si Factus rechaza 5 peticiones de token consecutivas
     *  - Permanece abierto 60s y luego pasa a HALF-OPEN
     *  - Fallback: lanza FactusNoDisponibleException
     */
    @CircuitBreaker(name = "factus-token", fallbackMethod = "tokenFallback")
    @Retry(name = "factus-token")
    public String obtenerToken(Integer empresaId) {

        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(
                        HttpStatus.NOT_FOUND, "Empresa no encontrada"));

        if (!empresa.isFacturaElectronica())
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Esta empresa no tiene habilitada la facturación electrónica");

        // 1. ¿Token en caché vigente? (más de 5 min restantes)
        if (tokenVigente(empresa)) {
            log.debug("[Factus Token] Usando token en caché para empresa {}", empresaId);
            return empresa.getFactusAccessToken();
        }

        // 2. ¿Refresh token disponible?
        if (empresa.getFactusRefreshToken() != null) {
            try {
                log.info("[Factus Token] Renovando via refresh_token para empresa {}", empresaId);
                return refrescarToken(empresa);
            } catch (Exception e) {
                log.warn("[Factus Token] Refresh falló, haciendo login completo: {}", e.getMessage());
            }
        }

        // 3. Login completo
        log.info("[Factus Token] Login completo para empresa {}", empresaId);
        return loginCompleto(empresa);
    }

    // ─────────────────────────────────────────────────────────────────
    // Métodos privados de autenticación
    // ─────────────────────────────────────────────────────────────────

    private boolean tokenVigente(EmpresaEntity empresa) {
        return empresa.getFactusAccessToken() != null
                && empresa.getFactusTokenExpiry() != null
                && LocalDateTime.now().isBefore(
                        empresa.getFactusTokenExpiry().minusMinutes(5));
    }

    private String loginCompleto(EmpresaEntity empresa) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type",    "password");
        body.add("client_id",     empresa.getFactusClientId());
        body.add("client_secret", empresa.getFactusClientSecret());
        body.add("username",      empresa.getFactusUsername());
        body.add("password",      empresa.getFactusPassword());
        return ejecutarRequest(empresa, body);
    }

    private String refrescarToken(EmpresaEntity empresa) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type",    "refresh_token");
        body.add("client_id",     empresa.getFactusClientId());
        body.add("client_secret", empresa.getFactusClientSecret());
        body.add("refresh_token", empresa.getFactusRefreshToken());
        return ejecutarRequest(empresa, body);
    }

    private String ejecutarRequest(EmpresaEntity empresa,
                                    MultiValueMap<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<FactusTokenResponseDto> response = restTemplate.postForEntity(
                FACTUS_TOKEN_URL,
                new HttpEntity<>(body, headers),
                FactusTokenResponseDto.class);

        FactusTokenResponseDto token = response.getBody();
        if (token == null || token.getAccessToken() == null)
            throw new GlobalException(HttpStatus.BAD_GATEWAY,
                    "Factus no retornó token de acceso");

        // Persistir en BD para reutilizar en próximas llamadas
        empresa.setFactusAccessToken(token.getAccessToken());
        empresa.setFactusRefreshToken(token.getRefreshToken());
        empresa.setFactusTokenExpiry(
                LocalDateTime.now().plusSeconds(token.getExpiresIn()));
        empresaRepository.save(empresa);

        log.info("[Factus Token] Token guardado para empresa {}, expira en {}s",
                empresa.getId(), token.getExpiresIn());

        return token.getAccessToken();
    }

    // ─────────────────────────────────────────────────────────────────
    // Fallback — Circuit Breaker abierto
    // ─────────────────────────────────────────────────────────────────

    /**
     * Se ejecuta cuando el circuito "factus-token" está ABIERTO o
     * cuando se agotan todos los reintentos.
     */
    public String tokenFallback(Integer empresaId, Throwable ex) {
        log.error("[Factus Token CB ABIERTO] empresa={} error={}",
                empresaId, ex.getMessage());
        throw new FactusNoDisponibleException(
                "El servicio de autenticación de Factus no está disponible. " +
                "Intenta en unos minutos.");
    }
}