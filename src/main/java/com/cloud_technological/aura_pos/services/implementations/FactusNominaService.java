package com.cloud_technological.aura_pos.services.implementations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.cloud_technological.aura_pos.dto.nomina.electronica.NominaElectronicaPayload;
import com.cloud_technological.aura_pos.utils.FactusNoDisponibleException;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;

/**
 * Envío de nómina electrónica a la DIAN vía Factus (Fase 5).
 *
 * <p>Sigue el patrón de {@link FactusService}: constructor injection,
 * {@code @CircuitBreaker} + {@code @Retry} con instancia propia, fallback, y
 * reutiliza {@link FactusTokenService} — que está separado a propósito para que
 * el circuit breaker del token no se mezcle con el del documento. <b>Mantener
 * esa separación.</b>
 *
 * <h2>🚧 EN PAUSA — no lo usan los clientes actuales</h2>
 * Este código está escrito y compila, pero <b>nunca se ha ejecutado contra
 * Factus</b>. Se dejó listo para cuando haga falta.
 *
 * <p><b>TODO — antes de usarlo en producción:</b>
 * <ol>
 *   <li><b>Confirmar la URL del endpoint de nómina</b> en la documentación de
 *       Factus. La de aquí ({@code /v1/payrolls/validate}) es una HIPÓTESIS:
 *       la integración existente del proyecto es solo factura de venta
 *       ({@code /v1/bills/validate}). Nómina electrónica es otro documento
 *       DIAN, con CUNE en vez de CUFE.</li>
 *   <li><b>Contrastar los nombres de campo</b> de {@link NominaElectronicaPayload}
 *       contra el contrato real. Están modelados sobre el UBL de nómina de la
 *       DIAN, no sobre la API de Factus.</li>
 *   <li><b>Ajustar {@link #extraerCune}</b>: las rutas donde se busca el CUNE
 *       en la respuesta son adivinanzas.</li>
 *   <li><b>Probar en sandbox</b> antes de emitir un documento real. Un
 *       consecutivo quemado ante la DIAN no se recupera.</li>
 *   <li>Implementar el flujo de <b>notas de ajuste</b> ({@code es_ajuste} en
 *       {@code NominaElectronicaEntity}): está modelado, no implementado.</li>
 *   <li>Falta el <b>job de envío</b> (Fase 7): hoy nadie llama a
 *       {@code NominaElectronicaService.enviar()}. Los documentos se quedan en
 *       PENDIENTE.</li>
 * </ol>
 *
 * <p>Lo que SÍ está resuelto y no hay que rehacer: la idempotencia. El
 * consecutivo se reserva atómicamente antes de enviar y
 * {@code uq_ne_consecutivo} lo protege; un reintento reusa la misma fila.
 */
@Slf4j
@Service
public class FactusNominaService {

    private final String factusNominaUrl;
    private final RestTemplate restTemplate;
    private final FactusTokenService factusTokenService;
    private final ObjectMapper objectMapper;

    public FactusNominaService(@Value("${factus.api.base-url}") String factusBaseUrl,
                               RestTemplate restTemplate,
                               FactusTokenService factusTokenService) {
        // ⚠️ HIPÓTESIS: verificar la ruta real en la doc de Factus.
        this.factusNominaUrl = factusBaseUrl + "/v1/payrolls/validate";
        this.restTemplate = restTemplate;
        this.factusTokenService = factusTokenService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Envía un documento de nómina.
     *
     * <p><b>El llamador ya reservó el consecutivo y persistió la fila.</b> Si
     * esto falla, el reintento debe reusar la MISMA fila: un consecutivo nuevo
     * ante la DIAN no se deshace.
     *
     * @return respuesta cruda de Factus, para que el llamador la registre en el
     *         log y extraiga el CUNE
     */
    @CircuitBreaker(name = "factus-nomina", fallbackMethod = "envioFallback")
    @Retry(name = "factus-nomina")
    public RespuestaEnvio enviar(Integer empresaId, NominaElectronicaPayload payload) {
        String token = factusTokenService.obtenerToken(empresaId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error serializando el payload de nómina electrónica: " + e.getMessage());
        }

        long inicio = System.currentTimeMillis();
        ResponseEntity<String> raw = restTemplate.exchange(
                factusNominaUrl, HttpMethod.POST,
                new HttpEntity<>(payload, headers), String.class);
        int duracion = (int) (System.currentTimeMillis() - inicio);

        log.info("[Factus Nómina] HTTP {} | {} ms | Body: {}",
                raw.getStatusCode(), duracion, raw.getBody());

        return new RespuestaEnvio(
                raw.getStatusCode().value(),
                requestBody,
                raw.getBody(),
                extraerCune(raw.getBody()),
                duracion,
                raw.getStatusCode().is2xxSuccessful());
    }

    /**
     * Fallback del circuit breaker.
     *
     * <p>No traga la excepción: la convierte en {@link FactusNoDisponibleException}
     * para que el job deje el documento en PENDIENTE y reintente. Un documento
     * que no se envió NO puede quedar marcado como enviado.
     */
    @SuppressWarnings("unused")
    private RespuestaEnvio envioFallback(Integer empresaId, NominaElectronicaPayload payload, Throwable t) {
        log.error("[Factus Nómina] No disponible (empresa={}, consecutivo={}): {}",
                empresaId, payload.getConsecutivo(), t.getMessage());
        throw new FactusNoDisponibleException(
                "Factus no está disponible para enviar nómina electrónica: " + t.getMessage());
    }

    /**
     * Extrae el CUNE de la respuesta.
     *
     * <p>⚠️ Los nombres de campo son hipótesis. Verificar contra la respuesta
     * real de Factus.
     */
    private String extraerCune(String body) {
        if (body == null) return null;
        try {
            var node = objectMapper.readTree(body);
            for (String ruta : new String[]{"data/payroll/cune", "data/cune", "cune"}) {
                var actual = node;
                for (String parte : ruta.split("/")) {
                    if (actual == null) break;
                    actual = actual.get(parte);
                }
                if (actual != null && !actual.isNull()) return actual.asText();
            }
        } catch (Exception e) {
            log.warn("[Factus Nómina] No se pudo extraer el CUNE: {}", e.getMessage());
        }
        return null;
    }

    /** Respuesta cruda de un intento. */
    public record RespuestaEnvio(int codigoHttp,
                                 String requestBody,
                                 String responseBody,
                                 String cune,
                                 int duracionMs,
                                 boolean exitoso) {}
}
