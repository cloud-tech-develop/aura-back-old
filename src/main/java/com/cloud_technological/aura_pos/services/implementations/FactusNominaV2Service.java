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

import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload;
import com.cloud_technological.aura_pos.utils.FactusNoDisponibleException;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;

/**
 * Envío de nómina electrónica a <b>Factus v2</b> ({@code POST /v2/payrolls}).
 *
 * <p>Reutiliza el circuit breaker {@code factus-nomina}, el {@link FactusTokenService}
 * (OAuth {@code /oauth/token}, grant_type=password) y el patrón de fallback. El
 * cuerpo es {@link FactusNominaV2Payload}, la estructura real de Factus.
 */
@Slf4j
@Service
public class FactusNominaV2Service {

    private final String url;
    private final String adjustmentUrl;
    private final RestTemplate restTemplate;
    private final FactusTokenService tokenService;
    private final ObjectMapper mapper;

    public FactusNominaV2Service(@Value("${factus.api.base-url}") String baseUrl,
                                 @org.springframework.beans.factory.annotation.Qualifier("factusNominaRestTemplate")
                                 RestTemplate restTemplate,
                                 FactusTokenService tokenService) {
        this.url = baseUrl + "/v2/payrolls";
        this.adjustmentUrl = baseUrl + "/v2/adjustment-payrolls";
        this.restTemplate = restTemplate;
        this.tokenService = tokenService;
        this.mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @CircuitBreaker(name = "factus-nomina", fallbackMethod = "fallback")
    @Retry(name = "factus-nomina")
    public Respuesta enviar(Integer empresaId, FactusNominaV2Payload payload) {
        String token = tokenService.obtenerToken(empresaId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(token);

        String requestBody;
        try {
            requestBody = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error serializando el payload de nómina Factus: " + e.getMessage());
        }

        long inicio = System.currentTimeMillis();
        ResponseEntity<String> raw;
        try {
            raw = restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers), String.class);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            // 4xx (validación DIAN/Factus): NO reintentar, devolver el detalle.
            int dur = (int) (System.currentTimeMillis() - inicio);
            log.warn("[Factus Nómina v2] HTTP {} | {} ms | {}",
                    e.getStatusCode(), dur, e.getResponseBodyAsString());
            return new Respuesta(e.getStatusCode().value(), requestBody,
                    e.getResponseBodyAsString(), null, payload.getReferenceCode(), dur, false);
        }
        int dur = (int) (System.currentTimeMillis() - inicio);
        log.info("[Factus Nómina v2] HTTP {} | {} ms | {}", raw.getStatusCode(), dur, raw.getBody());
        return new Respuesta(raw.getStatusCode().value(), requestBody, raw.getBody(),
                extraerCune(raw.getBody()), payload.getReferenceCode(), dur,
                raw.getStatusCode().is2xxSuccessful());
    }

    @SuppressWarnings("unused")
    private Respuesta fallback(Integer empresaId, FactusNominaV2Payload payload, Throwable t) {
        log.error("[Factus Nómina v2] No disponible (empresa={}): {}", empresaId, t.getMessage());
        throw new FactusNoDisponibleException("Factus no está disponible para nómina electrónica: " + t.getMessage());
    }

    // ── Consultar / eliminar / listar (envuelven Factus v2) ─────────────────

    /** Ver una nómina por su reference_code. {@code GET /v2/payrolls/reference/{ref}}. */
    public com.fasterxml.jackson.databind.JsonNode consultar(Integer empresaId, String referenceCode) {
        return llamar(empresaId, url + "/reference/" + referenceCode, HttpMethod.GET);
    }

    /** Ver una nómina por su reference_code, con el código HTTP (404 = Factus no la tiene). */
    public RespuestaFactus verPorReferencia(Integer empresaId, String referenceCode) {
        return llamarConCodigo(empresaId, url + "/reference/" + referenceCode, HttpMethod.GET, null);
    }

    /** Ver una nómina por su número (prefijo + consecutivo). {@code GET /v2/payrolls/{number}}. */
    public RespuestaFactus verPorNumero(Integer empresaId, String number) {
        return llamarConCodigo(empresaId, url + "/" + number, HttpMethod.GET, null);
    }

    /**
     * Eliminar una nómina que la DIAN no validó, para crearla de nuevo corregida.
     * {@code DELETE /v2/payrolls/reference/{ref}}.
     */
    public RespuestaFactus eliminar(Integer empresaId, String referenceCode) {
        return llamarConCodigo(empresaId, url + "/reference/" + referenceCode, HttpMethod.DELETE, null);
    }

    /**
     * PDF de una nómina en base64 ({@code pdf_base_64_encoded} y {@code file_name}).
     * {@code GET /v2/payrolls/{number}/download-pdf}.
     */
    public RespuestaFactus descargarPdf(Integer empresaId, String number) {
        return llamarConCodigo(empresaId, url + "/" + number + "/download-pdf", HttpMethod.GET, null);
    }

    // ── Notas de ajuste (eliminación) ───────────────────────────────────────

    /** Ver una nota de ajuste por su número. {@code GET /v2/adjustment-payrolls/{number}}. */
    public RespuestaFactus verAjuste(Integer empresaId, String number) {
        return llamarConCodigo(empresaId, adjustmentUrl + "/" + number, HttpMethod.GET, null);
    }

    /**
     * Eliminar una nota de ajuste que la DIAN no validó.
     * {@code DELETE /v2/adjustment-payrolls/reference/{ref}}.
     */
    public RespuestaFactus eliminarAjuste(Integer empresaId, String referenceCode) {
        return llamarConCodigo(empresaId, adjustmentUrl + "/reference/" + referenceCode, HttpMethod.DELETE, null);
    }

    /** XML de una nota de ajuste en base64. {@code GET /v2/adjustment-payrolls/{number}/download-xml}. */
    public RespuestaFactus descargarXmlAjuste(Integer empresaId, String number) {
        return llamarConCodigo(empresaId, adjustmentUrl + "/" + number + "/download-xml", HttpMethod.GET, null);
    }

    /** PDF de una nota de ajuste en base64. {@code GET /v2/adjustment-payrolls/{number}/download-pdf}. */
    public RespuestaFactus descargarPdfAjuste(Integer empresaId, String number) {
        return llamarConCodigo(empresaId, adjustmentUrl + "/" + number + "/download-pdf", HttpMethod.GET, null);
    }

    /**
     * Nota de eliminación (forma DIAN de anular una nómina electrónica ya emitida).
     * {@code POST /v2/adjustment-payrolls}.
     *
     * @param payrollNumber  número de la nómina a eliminar (prefijo + consecutivo).
     * @param referenceCode  código único de ESTA nota de eliminación.
     * @param numberingRangeId opcional; solo si hay varios rangos activos.
     */
    public RespuestaFactus notaEliminacion(
            Integer empresaId, String payrollNumber, String referenceCode, String numberingRangeId) {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("payroll_number", payrollNumber);
        body.put("reference_code", referenceCode);
        if (numberingRangeId != null && !numberingRangeId.isBlank())
            body.put("numbering_range_id", numberingRangeId);
        return llamarConCodigo(empresaId, adjustmentUrl, HttpMethod.POST, body);
    }

    /**
     * Descarga el XML firmado de una nómina. {@code GET /v2/payrolls/{number}/download-xml}.
     *
     * @param number número de la nómina = prefijo + consecutivo (p.ej. "NEF1").
     * @return el cuerpo tal cual lo devuelve Factus (XML, o el JSON que lo envuelve).
     */
    public String descargarXml(Integer empresaId, String number) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenService.obtenerToken(empresaId));
        String fullUrl = url + "/" + number + "/download-xml";
        try {
            ResponseEntity<String> raw = restTemplate.exchange(fullUrl, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            return raw.getBody();
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.warn("[Factus Nómina v2] GET {} -> HTTP {} | {}", fullUrl,
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new GlobalException(HttpStatus.BAD_GATEWAY,
                    "No se pudo descargar el XML de Factus: " + e.getResponseBodyAsString());
        }
    }

    /** Listar nóminas con filtros. {@code GET /v2/payrolls?filter[...]&page=}. */
    public com.fasterxml.jackson.databind.JsonNode listar(Integer empresaId, String identificationNumber,
                                                          String number, String names, Integer page) {
        var uri = org.springframework.web.util.UriComponentsBuilder.fromHttpUrl(url);
        if (identificationNumber != null && !identificationNumber.isBlank())
            uri.queryParam("filter[identification_number]", identificationNumber);
        if (number != null && !number.isBlank()) uri.queryParam("filter[number]", number);
        if (names != null && !names.isBlank()) uri.queryParam("filter[names]", names);
        if (page != null) uri.queryParam("page", page);
        return llamar(empresaId, uri.toUriString(), HttpMethod.GET);
    }

    private com.fasterxml.jackson.databind.JsonNode llamar(Integer empresaId, String fullUrl, HttpMethod method) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(tokenService.obtenerToken(empresaId));
        try {
            ResponseEntity<String> raw = restTemplate.exchange(fullUrl, method,
                    new HttpEntity<>(headers), String.class);
            return parse(raw.getBody());
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.warn("[Factus Nómina v2] {} {} -> HTTP {} | {}", method, fullUrl,
                    e.getStatusCode(), e.getResponseBodyAsString());
            return parse(e.getResponseBodyAsString());
        }
    }

    /**
     * Llama a Factus y devuelve el código HTTP con el cuerpo, para que quien llama
     * sepa si la operación se hizo (un 4xx trae el detalle del error en el cuerpo).
     *
     * @param body cuerpo JSON, o null si no lleva
     */
    private RespuestaFactus llamarConCodigo(Integer empresaId, String fullUrl, HttpMethod method, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(tokenService.obtenerToken(empresaId));
        try {
            HttpEntity<String> entity;
            if (body != null) {
                headers.setContentType(MediaType.APPLICATION_JSON);
                entity = new HttpEntity<>(mapper.writeValueAsString(body), headers);
            } else {
                entity = new HttpEntity<>(headers);
            }
            ResponseEntity<String> raw = restTemplate.exchange(fullUrl, method, entity, String.class);
            log.info("[Factus Nómina v2] {} {} -> HTTP {}", method, fullUrl, raw.getStatusCode());
            return new RespuestaFactus(raw.getStatusCode().value(), parse(raw.getBody()));
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.warn("[Factus Nómina v2] {} {} -> HTTP {} | {}", method, fullUrl,
                    e.getStatusCode(), e.getResponseBodyAsString());
            return new RespuestaFactus(e.getStatusCode().value(), parse(e.getResponseBodyAsString()));
        } catch (org.springframework.web.client.ResourceAccessException e) {
            throw new FactusNoDisponibleException("Factus no está disponible: " + e.getMessage());
        } catch (Exception e) {
            throw new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error llamando a Factus (" + fullUrl + "): " + e.getMessage());
        }
    }

    private com.fasterxml.jackson.databind.JsonNode parse(String body) {
        try {
            return body != null ? mapper.readTree(body) : mapper.nullNode();
        } catch (Exception e) {
            return mapper.nullNode();
        }
    }

    private String extraerCune(String body) {
        return extraer(body, "data/payroll/cune", "data/cune", "cune");
    }

    /** Extrae el primer texto no nulo entre varias rutas 'a/b/c' del JSON. */
    public String extraer(String body, String... rutas) {
        if (body == null) return null;
        try {
            var node = mapper.readTree(body);
            for (String ruta : rutas) {
                var actual = node;
                for (String parte : ruta.split("/")) {
                    if (actual == null) break;
                    actual = actual.get(parte);
                }
                if (actual != null && !actual.isNull()) return actual.asText();
            }
        } catch (Exception ignored) { }
        return null;
    }

    public record Respuesta(int codigoHttp, String requestBody, String responseBody,
                            String cune, String referenceCode, int duracionMs, boolean exitoso) {}

    /** Respuesta de Factus con su código HTTP y el cuerpo ya leído como JSON. */
    public record RespuestaFactus(int codigoHttp, com.fasterxml.jackson.databind.JsonNode cuerpo) {

        public boolean exitosa() {
            return codigoHttp >= 200 && codigoHttp < 300;
        }

        /** Texto de un campo del cuerpo por ruta 'a/b/c' (null si no está). */
        public String texto(String... rutas) {
            if (cuerpo == null) return null;
            for (String ruta : rutas) {
                com.fasterxml.jackson.databind.JsonNode n = cuerpo;
                for (String parte : ruta.split("/")) {
                    if (n == null) break;
                    n = n.get(parte);
                }
                if (n != null && !n.isNull() && !n.isContainerNode()) return n.asText();
            }
            return null;
        }

        /** Mensaje de error de Factus (message + errors) para mostrarle al usuario. */
        public String error() {
            if (cuerpo == null || cuerpo.isNull()) return "Factus respondió HTTP " + codigoHttp;
            StringBuilder sb = new StringBuilder(cuerpo.path("message").asText("Factus respondió HTTP " + codigoHttp));
            com.fasterxml.jackson.databind.JsonNode errores = cuerpo.path("data").path("errors");
            if (errores.isMissingNode()) errores = cuerpo.path("errors");
            if (!errores.isMissingNode() && !errores.isNull()) sb.append(": ").append(errores.toString());
            return sb.toString();
        }
    }
}
